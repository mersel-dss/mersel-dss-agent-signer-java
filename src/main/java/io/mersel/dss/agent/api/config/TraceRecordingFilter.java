/*
 * Copyright 2026 Mersel DSS
 * SPDX-License-Identifier: Apache-2.0 WITH LicenseRef-Mersel-Brand-Attribution
 *
 * Bu dosya, "Mersel Marka Atıf Eki" ile genişletilmiş Apache Lisansı
 * sürüm 2.0 ("Lisans") altında lisanslanmıştır. Bu dosyayı yalnızca
 * Lisans ve Ek şartlarına uygun olarak kullanabilirsiniz. Lisans ve
 * Ek'in tam metni proje kök dizinindeki LICENSE dosyasındadır; temel
 * Apache Lisansı metnine
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * adresinden de ulaşabilirsiniz.
 *
 * Yürürlükteki hukuk aksini gerektirmedikçe veya yazılı olarak
 * anlaşılmadıkça, Lisans kapsamında dağıtılan yazılım "OLDUĞU GİBİ"
 * esasıyla, açık ya da örtük HİÇBİR GARANTİ veya KOŞUL OLMAKSIZIN
 * sunulur. Lisans kapsamındaki haklar ve sınırlamalar için Lisans
 * metnine bakınız.
 *
 * Mersel Marka Atıf Eki, uygulamanın kullanıcı arayüzünde render
 * edilen marka atıflarının (splash penceresindeki "MERSEL DSS" marka
 * işareti, ana pencerenin üst kısmındaki Mersel banner / logo ve
 * altbilgi satırındaki mersel.io credit'i) her dağıtımda korunmasını
 * zorunlu kılar. Detay için LICENSE 2. Madde ve TRADEMARK.md.
 */
package io.mersel.dss.agent.api.config;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.mersel.dss.agent.api.services.diagnostics.TraceErrorContext;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecord;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecorder;

/**
 * Her HTTP isteğini ölçer ve ({@link TraceRecorder} açıksa) bellekteki ring buffer'a kaydeder.
 * {@link TraceIdFilter}'dan SONRA çalışır (precedence biraz daha düşük) ki MDC'deki traceId burada
 * okunabilsin.
 *
 * <h2>Hata bilgisi nereden geliyor?</h2>
 *
 * Spring exception resolver'ları yanıtı yazdığı için filter'a {@code Throwable} propagate olmaz.
 * Köprü olarak {@link io.mersel.dss.agent.api.GlobalExceptionHandler}, hata yakaladığı her yerde
 * {@link TraceErrorContext}'ü request attribute olarak iliştirir; biz {@code chain.doFilter}
 * döndükten sonra bunu okuruz.
 *
 * <h2>Performans</h2>
 *
 * Recorder kapalıysa {@code System.nanoTime()} ölçümü yine yapılır (maliyet ihmal edilebilir),
 * fakat record üretmeden döner. Açıksa: bir builder + immutable record, listener'lar buffer kilidi
 * DIŞINDA tetiklenir.
 *
 * <h2>Kapsam — gürültü filtresi</h2>
 *
 * Health / ping / favicon gibi yüksek frekanslı path'ler buffer'ı kısa sürede doldurup gerçek iş
 * trafiği kayıtlarını ringden düşürür. Bu nedenle bilinen "gürültü" prefix'leri varsayılan olarak
 * SKIP edilir (ne ring buffer'a, ne de UI tanılama paneline düşer). Liste config ile override
 * edilebilir:
 *
 * <pre>
 * mersel.signer.diagnostics.trace-recorder.skip-paths:
 *   - /actuator
 *   - /health
 *   - /ping
 *   - /favicon.ico
 *   - /error
 * </pre>
 *
 * <p>Boş string veya {@code "none"} verilirse hiçbir path skip edilmez (eski davranış).
 * Karşılaştırma case-sensitive prefix match'tir; query string yok sayılır.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TraceRecordingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TraceRecordingFilter.class);

  /**
   * Yüksek frekanslı health/ping/asset path'lerinin varsayılan listesi. Application.yml override
   * etmezse bu değer kullanılır. Kullanıcının "ping atılıyor, log'lamaya gerek yok" beklentisini
   * out-of-the-box karşılar.
   */
  static final List<String> DEFAULT_SKIP_PATHS =
      Collections.unmodifiableList(
          Arrays.asList(
              "/",
              "/actuator",
              "/health",
              "/ping",
              "/favicon.ico",
              "/error",
              "/diagnostics/traces",
              "/vendor"));

  private final TraceRecorder recorder;
  private final List<String> skipPathPrefixes;

  /** Test/eski kullanıcılar için — varsayılan skip listesiyle. */
  public TraceRecordingFilter(TraceRecorder recorder) {
    this(recorder, DEFAULT_SKIP_PATHS);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public TraceRecordingFilter(TraceRecorder recorder, SignerProperties properties) {
    this(
        recorder,
        properties == null
            ? DEFAULT_SKIP_PATHS
            : properties.getDiagnostics().getTraceRecorder().getSkipPaths());
  }

  public TraceRecordingFilter(TraceRecorder recorder, List<String> configuredSkipPaths) {
    this.recorder = recorder;
    this.skipPathPrefixes = normaliseSkipPaths(configuredSkipPaths);
    log.info(
        "TraceRecordingFilter skip-paths: {}",
        skipPathPrefixes.isEmpty() ? "(yok)" : skipPathPrefixes);
  }

  private static List<String> normaliseSkipPaths(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> out = new ArrayList<String>(raw.size());
    for (String p : raw) {
      if (p == null) continue;
      String trimmed = p.trim();
      if (trimmed.isEmpty()) continue;
      // "none" / "off" → liste boş kalsın (skip kapalı modu).
      if (trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("off")) {
        return Collections.emptyList();
      }
      out.add(trimmed);
    }
    return Collections.unmodifiableList(out);
  }

  /** Test friendly: prefix match'i izole birim olarak doğrulamak için. */
  boolean shouldSkip(String path) {
    if (path == null || skipPathPrefixes.isEmpty()) {
      return false;
    }
    for (String prefix : skipPathPrefixes) {
      // Kök "/" prefix olarak alınırsa HER path onunla başlar; sadece kökün KENDİSİ
      // (ya da boş/"/"-only istek) skip edilsin, alt path'ler kaydedilmeye devam etsin.
      if (prefix.equals("/")) {
        if (path.equals("/") || path.isEmpty()) {
          return true;
        }
        continue;
      }
      if (path.equals(prefix) || path.startsWith(prefix + "/")) {
        return true;
      }
      // Tam path olarak eşleşmesi de kabul: /favicon.ico /health gibi yapraklar.
      if (path.startsWith(prefix)
          && (path.length() == prefix.length()
              || !Character.isLetterOrDigit(path.charAt(prefix.length())))) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // Skip path'leri ölçüm yapmadan önce tespit et — health/ping gibi gürültü trafiği için
    // System.nanoTime() çağrısı bile harcamayalım (saniyede onlarca ping atılabilir).
    boolean skip = shouldSkip(request.getRequestURI());
    if (skip) {
      chain.doFilter(request, response);
      return;
    }

    long startNanos = System.nanoTime();
    Instant startInstant = Instant.now();
    try {
      chain.doFilter(request, response);
    } finally {
      // Recorder kapalıysa kayıt üretmeyiz; ama traceId header zaten yazıldı (TraceIdFilter).
      if (recorder != null && recorder.isEnabled()) {
        try {
          long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
          TraceRecord record = build(request, response, startInstant, durationMs);
          recorder.record(record);
        } catch (RuntimeException re) {
          // Trace kaydı hatası ASLA business response'u etkilemesin — filter zaten finally'de.
          log.debug("TraceRecord oluşturulurken beklenmedik hata: {}", re.toString());
        }
      }
    }
  }

  private TraceRecord build(
      HttpServletRequest request,
      HttpServletResponse response,
      Instant startedAt,
      long durationMs) {
    String traceId = response.getHeader(TraceIdFilter.HEADER_NAME);
    TraceRecord.Builder b =
        TraceRecord.builder()
            .traceId(traceId)
            .startedAt(OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC))
            .durationMs(durationMs)
            .method(request.getMethod())
            .path(request.getRequestURI())
            .queryString(request.getQueryString())
            .remoteAddr(request.getRemoteAddr())
            .statusCode(response.getStatus());

    TraceErrorContext ec = TraceErrorContext.from(request);
    if (ec != null) {
      b.errorCode(ec.getErrorCode())
          .errorMessage(ec.getErrorMessage())
          .exceptionType(ec.getExceptionType())
          .causeChain(ec.getCauseChain())
          .signatureDiagnostics(ec.getSignatureDiagnostics());
    }
    return b.build();
  }
}
