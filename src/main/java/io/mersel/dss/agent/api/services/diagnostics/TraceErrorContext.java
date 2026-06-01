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

import java.util.List;

import javax.servlet.ServletRequest;

import io.mersel.dss.agent.api.exceptions.CauseChainExtractor;
import io.mersel.dss.agent.api.models.SignatureDiagnostics;

/**
 * {@link io.mersel.dss.agent.api.GlobalExceptionHandler}'ın yakaladığı hatayı {@link
 * TraceRecordingFilter}'a iletmek için kullanılan basit request attribute taşıyıcısı. Spring'in
 * exception resolver'ı yanıtı zaten yazdığı için filtreye exception nesnesi propagate olmaz; bu
 * sınıf "köprü" işlevi görür.
 *
 * <p>Hassas hiçbir alan (PIN, ham gövde) tutulmaz; sadece tanılama düzeyi alanlar.
 */
public final class TraceErrorContext {

  /** Servlet request attribute anahtarı. */
  public static final String ATTRIBUTE = "io.mersel.dss.agent.trace.errorContext";

  private final String errorCode;
  private final String errorMessage;
  private final String exceptionType;
  private final List<CauseChainExtractor.Frame> causeChain;
  private final SignatureDiagnostics signatureDiagnostics;

  public TraceErrorContext(
      String errorCode,
      String errorMessage,
      String exceptionType,
      List<CauseChainExtractor.Frame> causeChain,
      SignatureDiagnostics signatureDiagnostics) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.exceptionType = exceptionType;
    this.causeChain = causeChain;
    this.signatureDiagnostics = signatureDiagnostics;
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

  /** Request attribute'una yazar; null-safe. */
  public static void attach(ServletRequest request, TraceErrorContext context) {
    if (request != null && context != null) {
      request.setAttribute(ATTRIBUTE, context);
    }
  }

  /** Request attribute'tan okur; yoksa null. */
  public static TraceErrorContext from(ServletRequest request) {
    if (request == null) {
      return null;
    }
    Object o = request.getAttribute(ATTRIBUTE);
    return o instanceof TraceErrorContext ? (TraceErrorContext) o : null;
  }
}
