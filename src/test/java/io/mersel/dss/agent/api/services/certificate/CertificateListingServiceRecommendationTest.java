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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.models.CertificateResponse;
import io.mersel.dss.agent.api.models.enums.CertificatePurpose;
import io.mersel.dss.agent.api.models.enums.TurkishCertificatePolicy;

/**
 * Liste seviyesinde "önerilen" sertifika seçim mantığını test eder. Inspector mock'lanmaz —
 * post-processing {@code annotateRecommendation} saf fonksiyondur, doğrudan çağrılır.
 */
class CertificateListingServiceRecommendationTest {

  private CertificateListingService service;

  @BeforeEach
  void setUp() {
    // null bağımlılıklarla — annotateRecommendation onlara ihtiyaç duymaz.
    service = new CertificateListingService(null, null);
  }

  @Test
  void singleSigningCertGetsRecommended() {
    List<CertificateResponse> list = new ArrayList<CertificateResponse>();
    list.add(cert("only", CertificatePurpose.SIGNING, true, "2025-01-01T00:00:00Z"));
    service.annotateRecommendation(list);
    assertThat(list.get(0).isRecommended()).isTrue();
  }

  @Test
  void encryptionCertNeverRecommended() {
    List<CertificateResponse> list =
        new ArrayList<CertificateResponse>(
            Arrays.asList(
                cert("ENCR0", CertificatePurpose.ENCRYPTION, false, "2025-01-01T00:00:00Z"),
                cert("SIGN0", CertificatePurpose.SIGNING, true, "2025-01-01T00:00:00Z")));
    service.annotateRecommendation(list);
    assertThat(list.get(0).isRecommended()).isFalse();
    assertThat(list.get(1).isRecommended()).isTrue();
  }

  @Test
  void multipleSigningCertsNewestNotBeforeWins() {
    List<CertificateResponse> list =
        new ArrayList<CertificateResponse>(
            Arrays.asList(
                cert("SIGN-2023", CertificatePurpose.SIGNING, true, "2023-06-15T00:00:00Z"),
                cert("SIGN-2025", CertificatePurpose.SIGNING, true, "2025-04-01T00:00:00Z"),
                cert("SIGN-2024", CertificatePurpose.SIGNING, true, "2024-09-10T00:00:00Z")));
    service.annotateRecommendation(list);
    assertThat(list.get(0).isRecommended()).isFalse();
    assertThat(list.get(1).isRecommended()).isTrue(); // SIGN-2025 (en yeni)
    assertThat(list.get(2).isRecommended()).isFalse();
  }

  @Test
  void noEligibleCertsLeavesAllUnrecommended() {
    List<CertificateResponse> list =
        new ArrayList<CertificateResponse>(
            Arrays.asList(
                cert("ENCR", CertificatePurpose.ENCRYPTION, false, "2025-01-01T00:00:00Z"),
                cert("CA", CertificatePurpose.OTHER, false, "2025-01-01T00:00:00Z")));
    service.annotateRecommendation(list);
    for (CertificateResponse c : list) {
      assertThat(c.isRecommended()).isFalse();
    }
  }

  @Test
  void revokedSigningCertNotRecommended() {
    List<CertificateResponse> list =
        new ArrayList<CertificateResponse>(
            Arrays.asList(
                cert("SIGN-REVOKED", CertificatePurpose.SIGNING, false, "2025-05-01T00:00:00Z"),
                cert("SIGN-OK", CertificatePurpose.SIGNING, true, "2024-01-01T00:00:00Z")));
    service.annotateRecommendation(list);
    assertThat(list.get(0).isRecommended()).isFalse();
    assertThat(list.get(1).isRecommended()).isTrue();
  }

  @Test
  void exactlyOneRecommendedAlways() {
    List<CertificateResponse> list =
        new ArrayList<CertificateResponse>(
            Arrays.asList(
                cert("SIGN1", CertificatePurpose.SIGNING, true, "2024-01-01T00:00:00Z"),
                cert("SIGN2", CertificatePurpose.SIGNING, true, "2024-02-01T00:00:00Z"),
                cert("SIGN3", CertificatePurpose.SIGNING, true, "2024-03-01T00:00:00Z")));
    service.annotateRecommendation(list);
    long count = list.stream().filter(CertificateResponse::isRecommended).count();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void emptyAndNullListHandled() {
    service.annotateRecommendation(null);
    service.annotateRecommendation(new ArrayList<CertificateResponse>());
    // No-op; no exceptions thrown.
  }

  /* ---------------- yeni tie-breaker dimensiyonları ---------------- */

  @Test
  void qualifiedWinsOverOlderQualifiedAndNewerNonQualified() {
    // Tie-breaker sırası: qualified > Türkçe SIGNING policy > purpose=SIGNING > newest notBefore.
    // qualified=true olan daha eski cert, qualified=false olan yeniyi yenmeli.
    CertificateResponse qesOld =
        cert("QES-2024", CertificatePurpose.SIGNING, true, "2024-01-01T00:00:00Z");
    qesOld.setQualified(Boolean.TRUE);
    CertificateResponse nonQesNew =
        cert("SIGN-2025", CertificatePurpose.SIGNING, true, "2025-12-01T00:00:00Z");
    nonQesNew.setQualified(Boolean.FALSE);

    List<CertificateResponse> list =
        new ArrayList<CertificateResponse>(Arrays.asList(nonQesNew, qesOld));
    service.annotateRecommendation(list);

    assertThat(qesOld.isRecommended()).isTrue();
    assertThat(nonQesNew.isRecommended()).isFalse();
  }

  @Test
  void turkishMaliMuhurSigningPolicyWinsAgainstGenericSigningWhenQualifiedTied() {
    // İki cert de qualified=false (mali mühür QES değil), tie. Mali Mühür SIGNING policy taşıyan
    // generic SIGNING'i yenmeli.
    CertificateResponse maliMuhurOld =
        cert("MALI-MUHUR-2024", CertificatePurpose.SIGNING, true, "2024-01-01T00:00:00Z");
    maliMuhurOld.setQualified(Boolean.FALSE);
    maliMuhurOld.setTurkishCertificatePolicies(
        EnumSet.of(TurkishCertificatePolicy.MALI_MUHUR_SIGNING));

    CertificateResponse genericNew =
        cert("GENERIC-SIGN-2025", CertificatePurpose.SIGNING, true, "2025-12-01T00:00:00Z");
    genericNew.setQualified(Boolean.FALSE);
    genericNew.setTurkishCertificatePolicies(Collections.<TurkishCertificatePolicy>emptySet());

    List<CertificateResponse> list =
        new ArrayList<CertificateResponse>(Arrays.asList(genericNew, maliMuhurOld));
    service.annotateRecommendation(list);

    assertThat(maliMuhurOld.isRecommended()).isTrue();
    assertThat(genericNew.isRecommended()).isFalse();
  }

  @Test
  void qesKamuSmStillBeatsMaliMuhurOnQualifiedTier() {
    // KamuSM QES (qualified=true) Mali Mühür'ü (qualified=false) yenmeli — daha üst tier.
    CertificateResponse qes = cert("QES", CertificatePurpose.SIGNING, true, "2024-01-01T00:00:00Z");
    qes.setQualified(Boolean.TRUE);
    qes.setTurkishCertificatePolicies(EnumSet.of(TurkishCertificatePolicy.KAMU_SM_QES_INDIVIDUAL));

    CertificateResponse maliMuhur =
        cert("MALI-MUHUR", CertificatePurpose.SIGNING, true, "2025-12-01T00:00:00Z");
    maliMuhur.setQualified(Boolean.FALSE);
    maliMuhur.setTurkishCertificatePolicies(
        EnumSet.of(TurkishCertificatePolicy.MALI_MUHUR_SIGNING));

    List<CertificateResponse> list =
        new ArrayList<CertificateResponse>(Arrays.asList(maliMuhur, qes));
    service.annotateRecommendation(list);

    assertThat(qes.isRecommended()).isTrue();
    assertThat(maliMuhur.isRecommended()).isFalse();
  }

  @Test
  void mixedPurposeIsEligibleAndCanBeRecommendedWhenNoPureSigningExists() {
    // Sadece MIXED cert var ve eligible=true → o öneriliyor olmalı (eski "purpose==SIGNING"
    // şartı kaldırıldığı için).
    CertificateResponse mixed =
        cert("DUAL-USE", CertificatePurpose.MIXED, true, "2024-06-01T00:00:00Z");
    List<CertificateResponse> list =
        new ArrayList<CertificateResponse>(Collections.singletonList(mixed));
    service.annotateRecommendation(list);
    assertThat(mixed.isRecommended()).isTrue();
  }

  @Test
  void pureSigningBeatsMixedOnPurposeTier() {
    CertificateResponse mixed =
        cert("DUAL-USE", CertificatePurpose.MIXED, true, "2025-12-01T00:00:00Z");
    CertificateResponse pure =
        cert("PURE-SIGN", CertificatePurpose.SIGNING, true, "2024-01-01T00:00:00Z");
    List<CertificateResponse> list = new ArrayList<CertificateResponse>(Arrays.asList(mixed, pure));
    service.annotateRecommendation(list);
    assertThat(pure.isRecommended()).isTrue();
    assertThat(mixed.isRecommended()).isFalse();
  }

  private static CertificateResponse cert(
      String id, CertificatePurpose purpose, boolean eligible, String notBefore) {
    CertificateResponse c = new CertificateResponse();
    c.setId(id);
    c.setPurpose(purpose);
    c.setEligibleForSignature(eligible);
    c.setNotBefore(notBefore);
    return c;
  }
}
