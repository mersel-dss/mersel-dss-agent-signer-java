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
package io.mersel.dss.agent.api.services.diagnostics;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.mersel.dss.agent.api.exceptions.CauseChainExtractor;
import io.mersel.dss.agent.api.models.SignatureDiagnostics;

/**
 * Tek bir HTTP isteğine ait tanılama bağlamı. {@link TraceRecorder}'ın bellekteki ring buffer'ında
 * tutulur, REST/JSON üzerinden ya da Swing tanılama panelinden okunur.
 *
 * <h2>Gizlilik</h2>
 *
 * <p>Buraya hiçbir koşulda PIN, ham XML/PDF içeriği, tam kart serisi veya kullanıcı sertifikası
 * yazılmaz. {@link Builder} sadece güvenli, "tanılama düzeyi" alanları kabul eder. {@code
 * SignatureDiagnostics} zaten kart serisini maskeler; ek olarak {@link Builder#queryString} bilinen
 * hassas anahtar adlarını ({@code pin}, {@code password}) atar.
 *
 * <h2>Immutability</h2>
 *
 * <p>Tüm alanlar final; record'lar buffer'a yazıldıktan sonra dışarıdan değiştirilemez.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TraceRecord {

  private final String traceId;
  private final OffsetDateTime startedAt;
  private final long durationMs;
  private final String method;
  private final String path;
  private final String querySanitised;
  private final String remoteAddr;
  private final int statusCode;

  private final String errorCode;
  private final String errorMessage;
  private final String exceptionType;
  private final List<CauseChainExtractor.Frame> causeChain;
  private final SignatureDiagnostics signatureDiagnostics;

  private TraceRecord(Builder b) {
    this.traceId = b.traceId;
    this.startedAt = b.startedAt;
    this.durationMs = b.durationMs;
    this.method = b.method;
    this.path = b.path;
    this.querySanitised = b.querySanitised;
    this.remoteAddr = b.remoteAddr;
    this.statusCode = b.statusCode;
    this.errorCode = b.errorCode;
    this.errorMessage = b.errorMessage;
    this.exceptionType = b.exceptionType;
    this.causeChain =
        b.causeChain == null
            ? null
            : Collections.unmodifiableList(new ArrayList<CauseChainExtractor.Frame>(b.causeChain));
    this.signatureDiagnostics = b.signatureDiagnostics;
  }

  public String getTraceId() {
    return traceId;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public String getMethod() {
    return method;
  }

  public String getPath() {
    return path;
  }

  public String getQuerySanitised() {
    return querySanitised;
  }

  public String getRemoteAddr() {
    return remoteAddr;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getExceptionType() {
    return exceptionType;
  }

  public List<CauseChainExtractor.Frame> getCauseChain() {
    return causeChain;
  }

  public SignatureDiagnostics getSignatureDiagnostics() {
    return signatureDiagnostics;
  }

  /**
   * {@code statusCode >= 400} ya da {@code errorCode != null} ise hata sayılır. Tanılama panelinde
   * "yalnız hatalar" filtresi bu metoda dayanır.
   */
  public boolean isError() {
    return statusCode >= 400 || errorCode != null;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Mutable yapıcı; fluent API. {@link #build()} immutable record üretir. */
  public static final class Builder {
    private String traceId;
    private OffsetDateTime startedAt = OffsetDateTime.now();
    private long durationMs;
    private String method;
    private String path;
    private String querySanitised;
    private String remoteAddr;
    private int statusCode;
    private String errorCode;
    private String errorMessage;
    private String exceptionType;
    private List<CauseChainExtractor.Frame> causeChain;
    private SignatureDiagnostics signatureDiagnostics;

    public Builder traceId(String v) {
      this.traceId = v;
      return this;
    }

    public Builder startedAt(OffsetDateTime v) {
      if (v != null) {
        this.startedAt = v;
      }
      return this;
    }

    public Builder durationMs(long v) {
      this.durationMs = v;
      return this;
    }

    public Builder method(String v) {
      this.method = v;
      return this;
    }

    public Builder path(String v) {
      this.path = v;
      return this;
    }

    /**
     * Query string'i alır, bilinen hassas parametreleri ({@code pin}, {@code password}, {@code
     * certificateId} gibi PII riskli alanlar) maskeler. Diğer parametreler aynen yansır; tanılama
     * için terminalName / cardType / pkcs11LibraryPath bilgisi değerlidir.
     */
    public Builder queryString(String raw) {
      this.querySanitised = sanitiseQuery(raw);
      return this;
    }

    public Builder remoteAddr(String v) {
      // Basit anonimleştirme: localhost kalır; diğer IP'ler son okteti maskele.
      this.remoteAddr = anonymiseIp(v);
      return this;
    }

    public Builder statusCode(int v) {
      this.statusCode = v;
      return this;
    }

    public Builder errorCode(String v) {
      this.errorCode = v;
      return this;
    }

    public Builder errorMessage(String v) {
      this.errorMessage = v;
      return this;
    }

    public Builder exceptionType(String v) {
      this.exceptionType = v;
      return this;
    }

    public Builder causeChain(List<CauseChainExtractor.Frame> v) {
      this.causeChain = v;
      return this;
    }

    public Builder signatureDiagnostics(SignatureDiagnostics v) {
      this.signatureDiagnostics = v;
      return this;
    }

    public TraceRecord build() {
      return new TraceRecord(this);
    }

    /* --------------- helpers --------------- */

    static String sanitiseQuery(String raw) {
      if (raw == null || raw.isEmpty()) {
        return null;
      }
      String[] parts = raw.split("&");
      StringBuilder sb = new StringBuilder(raw.length());
      boolean first = true;
      for (String p : parts) {
        if (p.isEmpty()) {
          continue;
        }
        int eq = p.indexOf('=');
        String key = eq >= 0 ? p.substring(0, eq) : p;
        String val = eq >= 0 ? p.substring(eq + 1) : "";
        boolean sensitive = isSensitiveKey(key);
        if (!first) {
          sb.append('&');
        }
        sb.append(key);
        if (eq >= 0) {
          sb.append('=').append(sensitive ? "***" : val);
        }
        first = false;
      }
      return sb.length() == 0 ? null : sb.toString();
    }

    static boolean isSensitiveKey(String key) {
      if (key == null) return false;
      String k = key.toLowerCase(java.util.Locale.ROOT);
      // Substring / case-insensitive match — sızan ön ekleri/yazım farklarını da yakala.
      // "userPin", "card_pin", "APIKEY", "x-token" hepsi maskelensin.
      return k.indexOf("pin") >= 0
          || k.indexOf("password") >= 0
          || k.indexOf("pwd") >= 0
          || k.indexOf("secret") >= 0
          || k.indexOf("token") >= 0
          || k.indexOf("apikey") >= 0
          || k.indexOf("api_key") >= 0
          || k.indexOf("credential") >= 0
          || k.indexOf("certificateid") >= 0;
    }

    static String anonymiseIp(String addr) {
      if (addr == null || addr.isEmpty()) {
        return null;
      }
      // Loopback + sıkça gelen kısa yazımları olduğu gibi tut — destek için faydalı.
      if (addr.equals("127.0.0.1")
          || addr.equalsIgnoreCase("localhost")
          || addr.equals("0:0:0:0:0:0:0:1")
          || addr.equals("::1")) {
        return addr;
      }
      // IPv4 son oktet maskele.
      int lastDot = addr.lastIndexOf('.');
      if (lastDot > 0 && addr.indexOf('.') != lastDot && addr.length() - lastDot <= 4) {
        return addr.substring(0, lastDot) + ".***";
      }
      // IPv6 — son grup maskele.
      int lastColon = addr.lastIndexOf(':');
      if (lastColon > 0) {
        return addr.substring(0, lastColon) + ":***";
      }
      return addr;
    }
  }
}
