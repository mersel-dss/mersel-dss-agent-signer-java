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
package io.mersel.dss.agent.api.services.certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.security.cert.X509Certificate;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.mersel.dss.agent.api.models.CertificateStatusResponse;
import io.mersel.dss.agent.api.testsupport.PfxLoader;
import io.mersel.dss.agent.api.testsupport.PfxTestKey;

/**
 * {@link CertificateInspector} davranışını Kamu SM RSA test sertifikasıyla + mock {@link
 * RevocationChecker} ile doğrular.
 */
class CertificateInspectorTest {

  @Test
  void inspectReturnsActiveForFreshKamuSmCert() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());

    PfxLoader.Loaded loaded = PfxLoader.load(key);

    RevocationChecker mocked = Mockito.mock(RevocationChecker.class);
    CertificateInspector inspector = new CertificateInspector(mocked);

    CertificateStatusResponse status = inspector.inspect(loaded.certificate);
    assertEquals(CertificateStatusResponse.Status.ACTIVE, status.getStatus());
  }

  @Test
  void inspectWithRevocationDelegatesToChecker() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable());

    PfxLoader.Loaded loaded = PfxLoader.load(key);

    RevocationChecker mocked = Mockito.mock(RevocationChecker.class);
    Mockito.when(mocked.check(Mockito.any(), Mockito.any()))
        .thenReturn(
            new CertificateStatusResponse(
                CertificateStatusResponse.Status.UNKNOWN, "mocked-no-network"));

    CertificateInspector inspector = new CertificateInspector(mocked);
    CertificateStatusResponse status =
        inspector.inspectWithRevocation(
            loaded.certificate, new X509Certificate[] {loaded.certificate});

    assertEquals(CertificateStatusResponse.Status.UNKNOWN, status.getStatus());
    Mockito.verify(mocked).check(Mockito.eq(loaded.certificate), Mockito.any());
  }

  @Test
  void qualifiedDetectionOnKamuSmCert() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    CertificateInspector inspector =
        new CertificateInspector(Mockito.mock(RevocationChecker.class));
    // Kamu SM TEST sertifikası genelde QCStatements taşır; ama test
    // dünyasında olduğu için garanti değil. Sadece method'un patlamadığını
    // doğrularız (true/false her ikisi de geçerli sonuç).
    boolean qualified = inspector.isQualified(loaded.certificate);
    // Sonuç bilgi amaçlı; test başarısız olmaz.
    org.slf4j.LoggerFactory.getLogger(getClass()).info("Kamu SM cert qualified={}", qualified);
  }
}
