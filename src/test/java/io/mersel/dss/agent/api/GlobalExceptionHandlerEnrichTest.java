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
package io.mersel.dss.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.mersel.dss.agent.api.config.TraceIdFilter;
import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.models.ErrorModel;
import io.mersel.dss.agent.api.models.SignatureDiagnostics;

class GlobalExceptionHandlerEnrichTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void traceIdFromMdcIsCopiedToErrorModel() {
    MDC.put(TraceIdFilter.MDC_KEY, "trace-xyz");
    GlobalExceptionHandler handler = new GlobalExceptionHandler(true);

    SignatureOperationException ex =
        new SignatureOperationException(
            "imzalama başarısız",
            new RuntimeException("Unsupported parameters", new RuntimeException("CKR")));

    ResponseEntity<ErrorModel> resp = handler.handleSignatureOp(ex);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ErrorModel body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getTraceId()).isEqualTo("trace-xyz");
    assertThat(body.getCauseChain()).isNotNull().hasSize(3);
    assertThat(body.getCauseChain().get(2).getMessage()).isEqualTo("CKR");
  }

  @Test
  void exposeCauseChainFalse_keepsTraceIdButHidesChain() {
    MDC.put(TraceIdFilter.MDC_KEY, "trace-abc");
    GlobalExceptionHandler handler = new GlobalExceptionHandler(false);

    SignatureOperationException ex =
        new SignatureOperationException("hata", new RuntimeException("kök"));
    ErrorModel body = handler.handleSignatureOp(ex).getBody();

    assertThat(body).isNotNull();
    assertThat(body.getTraceId()).isEqualTo("trace-abc");
    assertThat(body.getCauseChain()).isNull();
  }

  @Test
  void diagnosticsFromExceptionIsMirroredToErrorModel() {
    MDC.put(TraceIdFilter.MDC_KEY, "trace-1");
    GlobalExceptionHandler handler = new GlobalExceptionHandler(true);

    SignatureDiagnostics diag = new SignatureDiagnostics();
    diag.setTerminalName("Atlantis ATR19 0");
    diag.setCardType("AKIS");
    diag.setResolvedPkcs11Mechanism("CKM_RSA_PKCS");
    diag.setFallbackStrategy("raw-rsa-soft-digest");

    SignatureOperationException ex =
        (SignatureOperationException)
            new SignatureOperationException("hata", new RuntimeException("kök"))
                .withDiagnostics(diag);

    ErrorModel body = handler.handleSignatureOp(ex).getBody();
    assertThat(body).isNotNull();
    assertThat(body.getSignatureDiagnostics()).isNotNull();
    assertThat(body.getSignatureDiagnostics().getCardType()).isEqualTo("AKIS");
    assertThat(body.getSignatureDiagnostics().getResolvedPkcs11Mechanism())
        .isEqualTo("CKM_RSA_PKCS");
    assertThat(body.getSignatureDiagnostics().getFallbackStrategy())
        .isEqualTo("raw-rsa-soft-digest");
  }

  @Test
  void enrich_traceIdFromExceptionWhenMdcEmpty() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler(true);
    SignatureOperationException ex =
        (SignatureOperationException)
            new SignatureOperationException("hata", new RuntimeException("k"))
                .withTraceId("ex-trace-id");

    ErrorModel body = handler.handleSignatureOp(ex).getBody();
    assertThat(body).isNotNull();
    assertThat(body.getTraceId()).isEqualTo("ex-trace-id");
  }
}
