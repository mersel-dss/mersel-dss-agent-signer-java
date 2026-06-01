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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * {@link CertificateListingService#computeValidity} davranışı.
 *
 * <p>Sözleşme: yalnızca {@code notBefore <= now <= notAfter} kontrolü; OCSP/CRL durumundan
 * <b>bağımsız</b>. KamuSM kartlarında zincirde issuer cert eksik kalan senaryoda OCSP {@code
 * UNKNOWN} döndürse bile bu fonksiyon network'e ÇIKMAZ ve sadece zaman penceresine bakar.
 *
 * <p>Mockito ile {@code X509Certificate}'in {@code getNotBefore} / {@code getNotAfter} metodları
 * stub'lanır; gerçek X.509 üretmeye gerek yok — fonksiyonun sözleşmesi pure date arithmetic.
 */
class CertificateListingServiceValidityTest {

  @Test
  void certWithinValidityWindowYieldsTrue() {
    X509Certificate cert =
        cert(
            Date.from(Instant.now().minus(30, ChronoUnit.DAYS)),
            Date.from(Instant.now().plus(365, ChronoUnit.DAYS)));
    assertThat(CertificateListingService.computeValidity(cert)).isTrue();
  }

  @Test
  void expiredCertYieldsFalse() {
    X509Certificate cert =
        cert(
            Date.from(Instant.now().minus(400, ChronoUnit.DAYS)),
            Date.from(Instant.now().minus(1, ChronoUnit.DAYS))); // dün doldu
    assertThat(CertificateListingService.computeValidity(cert)).isFalse();
  }

  @Test
  void notYetValidCertYieldsFalse() {
    X509Certificate cert =
        cert(
            Date.from(Instant.now().plus(7, ChronoUnit.DAYS)), // bir hafta sonra geçerli olacak
            Date.from(Instant.now().plus(370, ChronoUnit.DAYS)));
    assertThat(CertificateListingService.computeValidity(cert)).isFalse();
  }

  @Test
  void nullCertYieldsFalse() {
    assertThat(CertificateListingService.computeValidity(null)).isFalse();
  }

  @Test
  void nullNotBeforeIsTreatedAsAlwaysValidStartside() {
    // Defensive: cert.getNotBefore() == null → "lower bound yok" varsayımı.
    X509Certificate cert = cert(null, Date.from(Instant.now().plus(365, ChronoUnit.DAYS)));
    assertThat(CertificateListingService.computeValidity(cert)).isTrue();
  }

  @Test
  void nullNotAfterIsTreatedAsAlwaysValidEndside() {
    // Defensive: cert.getNotAfter() == null → "upper bound yok" varsayımı.
    X509Certificate cert = cert(Date.from(Instant.now().minus(30, ChronoUnit.DAYS)), null);
    assertThat(CertificateListingService.computeValidity(cert)).isTrue();
  }

  @Test
  void revocationStatusDoesNotAffectValidity() {
    // Sözleşmenin kalbi: cert zaman penceresi içindeyse VALID — RevocationChecker UNKNOWN/REVOKED
    // dönmüş olsa bile bu boolean'ı etkilemez (zaten cert objesinden başka argüman almıyor).
    // Burada implicit assertion: signature `(X509Certificate)` — status param yok.
    X509Certificate cert =
        cert(
            Date.from(Instant.now().minus(30, ChronoUnit.DAYS)),
            Date.from(Instant.now().plus(365, ChronoUnit.DAYS)));
    assertThat(CertificateListingService.computeValidity(cert)).isTrue();
  }

  private static X509Certificate cert(Date notBefore, Date notAfter) {
    X509Certificate c = mock(X509Certificate.class);
    when(c.getNotBefore()).thenReturn(notBefore);
    when(c.getNotAfter()).thenReturn(notAfter);
    return c;
  }
}
