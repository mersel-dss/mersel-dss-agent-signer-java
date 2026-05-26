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
package io.mersel.dss.agent.api.services.keystore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class Pkcs11VendorHintsTest {

  @Test
  void akisHintsToKamuSm() {
    String url = Pkcs11VendorHints.downloadUrlFor("AKIS");
    assertThat(url).isNotNull();
    assertThat(url).contains("kamusm.bilgem.tubitak.gov.tr");
  }

  @Test
  void lookupIsCaseInsensitive() {
    assertThat(Pkcs11VendorHints.downloadUrlFor("akis"))
        .isEqualTo(Pkcs11VendorHints.downloadUrlFor("AKIS"));
    assertThat(Pkcs11VendorHints.downloadUrlFor("AkIs"))
        .isEqualTo(Pkcs11VendorHints.downloadUrlFor("AKIS"));
  }

  @Test
  void unknownCardReturnsNull() {
    assertThat(Pkcs11VendorHints.downloadUrlFor("XYZ_NONEXISTENT")).isNull();
    assertThat(Pkcs11VendorHints.downloadUrlFor(null)).isNull();
  }

  @Test
  void hintForReturnsTurkishSentence() {
    String hint = Pkcs11VendorHints.hintFor("ALADDIN");
    assertThat(hint).isNotNull();
    assertThat(hint).startsWith("ALADDIN kartı için PKCS#11 sürücüsünü ");
    assertThat(hint).contains(Pkcs11VendorHints.downloadUrlFor("ALADDIN"));
  }

  @Test
  void unknownCardHintIsNull() {
    assertThat(Pkcs11VendorHints.hintFor(null)).isNull();
    assertThat(Pkcs11VendorHints.hintFor("ASLA_OLMAYAN_KART")).isNull();
  }

  @Test
  void allKnownVendorsHaveValidUrls() {
    Map<String, String> all = Pkcs11VendorHints.knownHints();
    assertThat(all).isNotEmpty();
    for (Map.Entry<String, String> e : all.entrySet()) {
      assertThat(e.getValue()).as(e.getKey() + " URL").startsWith("https://");
    }
  }

  @Test
  void knownVendorsCoverCommonTurkishCards() {
    Map<String, String> all = Pkcs11VendorHints.knownHints();
    // Türkiye'de en yaygın e-imza kartları
    assertThat(all).containsKeys("AKIS", "ALADDIN", "SAFESIGN", "GEMPLUS", "NCIPHER");
    // TÜBİTAK kart tipleri
    assertThat(all).containsKeys("ATIKKG", "ATIKHSM", "DIRAKHSM", "TKART");
  }
}
