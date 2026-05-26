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
package io.mersel.dss.agent.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.models.CertificateResponse;
import io.mersel.dss.agent.api.models.enums.CertificatePurpose;

/** {@link SmartCardController#applyFilters(List, String, boolean)} davranışı. */
class SmartCardControllerFilterTest {

  @Test
  void noFilterReturnsAll() {
    List<CertificateResponse> in = sample();
    List<CertificateResponse> out = SmartCardController.applyFilters(in, null, false);
    assertThat(out).hasSize(3);
  }

  @Test
  void purposeAllIsTreatedAsNoFilter() {
    List<CertificateResponse> in = sample();
    List<CertificateResponse> out = SmartCardController.applyFilters(in, "ALL", false);
    assertThat(out).hasSize(3);
  }

  @Test
  void purposeSigningKeepsOnlySigningCerts() {
    List<CertificateResponse> out = SmartCardController.applyFilters(sample(), "SIGNING", false);
    assertThat(out).extracting(CertificateResponse::getId).containsExactly("SIGN0");
  }

  @Test
  void purposeIsCaseInsensitive() {
    List<CertificateResponse> out = SmartCardController.applyFilters(sample(), "signing", false);
    assertThat(out).extracting(CertificateResponse::getId).containsExactly("SIGN0");
  }

  @Test
  void purposeEncryptionKeepsOnlyEncryptionCerts() {
    List<CertificateResponse> out = SmartCardController.applyFilters(sample(), "ENCRYPTION", false);
    assertThat(out).extracting(CertificateResponse::getId).containsExactly("ENCR0");
  }

  @Test
  void unknownPurposeFallsBackToNoFilter() {
    List<CertificateResponse> out = SmartCardController.applyFilters(sample(), "WAT", false);
    assertThat(out).hasSize(3);
  }

  @Test
  void eligibleOnlyKeepsOnlyEligibleCerts() {
    List<CertificateResponse> out = SmartCardController.applyFilters(sample(), null, true);
    assertThat(out).extracting(CertificateResponse::getId).containsExactly("SIGN0");
  }

  @Test
  void purposeAndEligibleOnlyCombineWithAnd() {
    List<CertificateResponse> in = sample();
    // SIGN0 eligible + SIGNING → kalır.
    // CA-OTHER eligible=false → eliminate.
    // ENCR0 SIGNING değil → eliminate.
    List<CertificateResponse> out = SmartCardController.applyFilters(in, "SIGNING", true);
    assertThat(out).extracting(CertificateResponse::getId).containsExactly("SIGN0");
  }

  @Test
  void emptyAndNullInputsHandled() {
    assertThat(SmartCardController.applyFilters(null, "SIGNING", true)).isNull();
    assertThat(
            SmartCardController.applyFilters(new ArrayList<CertificateResponse>(), "SIGNING", true))
        .isEmpty();
  }

  private static List<CertificateResponse> sample() {
    return new ArrayList<CertificateResponse>(
        Arrays.asList(
            cert("ENCR0", CertificatePurpose.ENCRYPTION, false),
            cert("SIGN0", CertificatePurpose.SIGNING, true),
            cert("CA-OTHER", CertificatePurpose.OTHER, false)));
  }

  private static CertificateResponse cert(String id, CertificatePurpose purpose, boolean eligible) {
    CertificateResponse c = new CertificateResponse();
    c.setId(id);
    c.setPurpose(purpose);
    c.setEligibleForSignature(eligible);
    return c;
  }
}
