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
package io.mersel.dss.agent.api.services.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.InvalidAlgorithmParameterException;

import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.exceptions.SignatureOperationException;

class XadesServiceClassifyTest {

  @Test
  void unsupportedParametersInCauseChain_classifiedAsAlgorithmUnsupported() {
    Throwable root = new RuntimeException("CKR_MECHANISM_INVALID");
    Throwable mid = new InvalidAlgorithmParameterException("Unsupported parameters");
    mid.initCause(root);
    Throwable top = new RuntimeException("Marshalling failed", mid);

    String code = XadesService.classifySignatureFailure(top);
    assertThat(code).isEqualTo(SignatureOperationException.CODE_ALGORITHM_UNSUPPORTED);
  }

  @Test
  void mechanismInvalidInRootCause_classifiedAsAlgorithmUnsupported() {
    Throwable root = new RuntimeException("PKCS11Exception: CKR_MECHANISM_INVALID (0x70)");
    Throwable top = new RuntimeException("XAdES sign error", root);
    assertThat(XadesService.classifySignatureFailure(top))
        .isEqualTo(SignatureOperationException.CODE_ALGORITHM_UNSUPPORTED);
  }

  @Test
  void unrelatedFailure_classifiedAsGenericFailed() {
    Throwable root = new RuntimeException("Disk full");
    Throwable top = new RuntimeException("XAdES sign error", root);
    assertThat(XadesService.classifySignatureFailure(top))
        .isEqualTo(SignatureOperationException.CODE_FAILED);
  }

  @Test
  void nullThrowableYieldsGenericFailedCode() {
    assertThat(XadesService.classifySignatureFailure(null))
        .isEqualTo(SignatureOperationException.CODE_FAILED);
  }
}
