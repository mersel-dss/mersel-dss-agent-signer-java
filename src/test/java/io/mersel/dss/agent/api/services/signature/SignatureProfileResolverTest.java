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

import org.junit.jupiter.api.Test;

class SignatureProfileResolverTest {

  @Test
  void chooseAlgorithms_modernRsa_yieldsRsaSha256_noFallback() {
    SignatureProfileResolver.Choice c =
        SignatureProfileResolver.chooseAlgorithms("CKM_SHA256_RSA_PKCS");
    assertThat(c.getSignatureUrl()).isEqualTo("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
    assertThat(c.getDigestUrl()).isEqualTo("http://www.w3.org/2001/04/xmlenc#sha256");
    assertThat(c.getFallbackStrategy()).isNull();
    assertThat(c.getWarnings()).isEmpty();
  }

  @Test
  void chooseAlgorithms_legacyRsaPkcs_marksRawRsaFallbackAndWarns() {
    SignatureProfileResolver.Choice c = SignatureProfileResolver.chooseAlgorithms("CKM_RSA_PKCS");
    assertThat(c.getFallbackStrategy()).isEqualTo("raw-rsa-soft-digest");
    assertThat(c.getWarnings()).isNotEmpty();
    assertThat(c.getSignatureUrl()).contains("rsa-sha256");
  }

  @Test
  void chooseAlgorithms_sha1RsaOnly_marksSha1FallbackAndDeprecationWarning() {
    SignatureProfileResolver.Choice c =
        SignatureProfileResolver.chooseAlgorithms("CKM_SHA1_RSA_PKCS");
    assertThat(c.getFallbackStrategy()).isEqualTo("rsa-sha1-only");
    assertThat(c.getWarnings()).anyMatch(w -> w.toLowerCase().contains("sha-1"));
  }

  @Test
  void chooseAlgorithms_modernEcdsa_yieldsEcdsaSha384_noFallback() {
    SignatureProfileResolver.Choice c =
        SignatureProfileResolver.chooseAlgorithms("CKM_ECDSA_SHA384");
    assertThat(c.getSignatureUrl())
        .isEqualTo("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384");
    assertThat(c.getFallbackStrategy()).isNull();
  }

  @Test
  void chooseAlgorithms_unknownCkm_fallsBackToRsaSha256_default() {
    SignatureProfileResolver.Choice c = SignatureProfileResolver.chooseAlgorithms("CKM_UNKNOWN");
    assertThat(c.getSignatureUrl()).contains("rsa-sha256");
    assertThat(c.getFallbackStrategy()).isNull();
  }

  @Test
  void jcaName_mapsKnownMechanismsToJca() {
    assertThat(SignatureProfileResolver.jcaName("CKM_SHA256_RSA_PKCS")).isEqualTo("SHA256withRSA");
    assertThat(SignatureProfileResolver.jcaName("CKM_RSA_PKCS")).isEqualTo("NONEwithRSA");
    assertThat(SignatureProfileResolver.jcaName("CKM_ECDSA_SHA384")).isEqualTo("SHA384withECDSA");
    assertThat(SignatureProfileResolver.jcaName("CKM_FOO")).isNull();
  }
}
