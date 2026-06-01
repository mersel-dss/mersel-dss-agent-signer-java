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
import java.util.UUID;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Her HTTP isteği için bir {@code traceId} üretir, {@link MDC}'ye yerleştirir ve {@code
 * X-Mersel-Trace-Id} response header'ı olarak döner.
 *
 * <p>Kullanım: log pattern'i {@code %X{traceId}} ile MDC değerini basar; {@code ErrorModel} aynı ID
 * ile zenginleştirilir; kullanıcı bu ID ile destek talebi açtığında log dosyasında tek {@code grep}
 * yeterlidir.
 *
 * <h3>Inbound trace propagation</h3>
 *
 * <p>Çağıran tarafta zaten bir trace ID varsa ({@code X-Mersel-Trace-Id} header'ında veya yaygın
 * {@code X-Request-Id} header'ında) onu kullanırız; aksi halde yeni UUID üretilir. Bu sayede
 * frontend'in her isteği için tutarlı bir ID akışı kurulabilir.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

  /** Yanıt header'ı + opsiyonel inbound header. Standart bir header değil; uygulamamıza özel. */
  public static final String HEADER_NAME = "X-Mersel-Trace-Id";

  /** Yaygın diğer trace header'ları (inbound'da öncelik sırası ile bakılır). */
  static final String[] INBOUND_HEADERS = {
    HEADER_NAME, "X-Request-Id", "X-Correlation-Id", "Traceparent"
  };

  /** Logback {@code %X{traceId}} ile basılan MDC anahtarı. */
  public static final String MDC_KEY = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = readInboundOrGenerate(request);
    MDC.put(MDC_KEY, traceId);
    try {
      response.setHeader(HEADER_NAME, traceId);
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  static String readInboundOrGenerate(HttpServletRequest request) {
    if (request != null) {
      for (String header : INBOUND_HEADERS) {
        String v = request.getHeader(header);
        if (v != null) {
          String trimmed = v.trim();
          if (!trimmed.isEmpty() && trimmed.length() <= 128 && isAcceptable(trimmed)) {
            return trimmed;
          }
        }
      }
    }
    return UUID.randomUUID().toString();
  }

  /**
   * Header injection / log forging riskini düşürmek için inbound trace ID'yi karaktere göre
   * filtrele: yalnız ASCII alfanumerik + {@code . - _} kabul edilir. Aksi halde yeni UUID üretilir.
   */
  private static boolean isAcceptable(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok =
          (c >= '0' && c <= '9')
              || (c >= 'A' && c <= 'Z')
              || (c >= 'a' && c <= 'z')
              || c == '-'
              || c == '_'
              || c == '.';
      if (!ok) {
        return false;
      }
    }
    return true;
  }
}
