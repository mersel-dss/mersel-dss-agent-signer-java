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

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.services.keystore.Pkcs11MechanismProbe;

class MechanismCapabilityServiceTest {

  @Test
  void modernAkisFirmware_recommendsRsaSha256_andDefaultProfileWorks() {
    Pkcs11MechanismProbe.ProbeResult probe =
        Pkcs11MechanismProbe.ProbeResult.forTest(
            Arrays.asList(
                "CKM_RSA_PKCS",
                "CKM_SHA1_RSA_PKCS",
                "CKM_SHA256_RSA_PKCS",
                "CKM_SHA384_RSA_PKCS",
                "CKM_SHA512_RSA_PKCS"));

    MechanismCapabilityResponse.XadesProfileSummary s =
        MechanismCapabilityService.buildSummary(probe);

    assertThat(s.getPreferredRsaMechanism()).isEqualTo("CKM_SHA256_RSA_PKCS");
    assertThat(s.getRecommendedSignatureUrl())
        .isEqualTo("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
    assertThat(s.isDefaultProfileWorks()).isTrue();
    assertThat(s.getFallbackStrategy()).isNull();
  }

  @Test
  void legacyAkisFirmware_onlyRawRsa_marksFallbackStrategy() {
    Pkcs11MechanismProbe.ProbeResult probe =
        Pkcs11MechanismProbe.ProbeResult.forTest(Arrays.asList("CKM_RSA_PKCS", "CKM_SHA_1"));

    MechanismCapabilityResponse.XadesProfileSummary s =
        MechanismCapabilityService.buildSummary(probe);

    assertThat(s.getPreferredRsaMechanism()).isEqualTo("CKM_RSA_PKCS");
    assertThat(s.isDefaultProfileWorks()).isFalse();
    assertThat(s.getFallbackStrategy()).isEqualTo("raw-rsa-soft-digest");
    assertThat(s.getWarnings()).isNotNull().anyMatch(w -> w.contains("raw CKM_RSA_PKCS"));
  }

  @Test
  void ecdsaOnlyToken_recommendsEcdsaSha384() {
    Pkcs11MechanismProbe.ProbeResult probe =
        Pkcs11MechanismProbe.ProbeResult.forTest(
            Arrays.asList("CKM_ECDSA", "CKM_ECDSA_SHA256", "CKM_ECDSA_SHA384"));

    MechanismCapabilityResponse.XadesProfileSummary s =
        MechanismCapabilityService.buildSummary(probe);

    assertThat(s.getPreferredEcdsaMechanism()).isEqualTo("CKM_ECDSA_SHA384");
    assertThat(s.getRecommendedSignatureUrl())
        .isEqualTo("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384");
    assertThat(s.isDefaultProfileWorks()).isTrue();
  }

  @Test
  void noSigningMechanismAvailable_returnsBlockingState() {
    Pkcs11MechanismProbe.ProbeResult probe =
        Pkcs11MechanismProbe.ProbeResult.forTest(Arrays.asList("CKM_SHA256", "CKM_AES_CBC"));

    MechanismCapabilityResponse.XadesProfileSummary s =
        MechanismCapabilityService.buildSummary(probe);

    assertThat(s.isDefaultProfileWorks()).isFalse();
    assertThat(s.getFallbackStrategy()).isEqualTo("none-available");
    assertThat(s.getRecommendedSignatureUrl()).isNull();
    assertThat(s.getWarnings()).isNotNull().isNotEmpty();
  }

  @Test
  void unsupportedJdkReflection_falsBackToDefaultsWithWarning() {
    Pkcs11MechanismProbe.ProbeResult probe = Pkcs11MechanismProbe.ProbeResult.empty(null);

    MechanismCapabilityResponse.XadesProfileSummary s =
        MechanismCapabilityService.buildSummary(probe);

    assertThat(s.isDefaultProfileWorks()).isTrue();
    assertThat(s.getRecommendedSignatureUrl()).contains("rsa-sha256");
    assertThat(s.getWarnings()).isNotNull().anyMatch(w -> w.contains("C_GetMechanismList"));
  }
}
