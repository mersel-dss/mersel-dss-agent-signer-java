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
package io.mersel.dss.agent.api.services.timestamp.tubitak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link TubitakTspDetector} host tespiti. */
class TubitakTspDetectorTest {

  @Test
  void detectsKamuSmProductionAndTestHosts() {
    assertThat(TubitakTspDetector.isTubitakTspHost("http://zd.kamusm.gov.tr")).isTrue();
    assertThat(TubitakTspDetector.isTubitakTspHost("https://tzd.kamusm.gov.tr")).isTrue();
    assertThat(TubitakTspDetector.isTubitakTspHost("http://ZD.KAMUSM.GOV.TR/path")).isTrue();
  }

  @Test
  void nonKamuSmHostsAreNotTubitak() {
    assertThat(TubitakTspDetector.isTubitakTspHost("http://tsa.example.com")).isFalse();
    assertThat(TubitakTspDetector.isTubitakTspHost("https://freetsa.org/tsr")).isFalse();
  }

  @Test
  void blankOrInvalidIsFalse() {
    assertThat(TubitakTspDetector.isTubitakTspHost(null)).isFalse();
    assertThat(TubitakTspDetector.isTubitakTspHost("  ")).isFalse();
    assertThat(TubitakTspDetector.isTubitakTspHost("not a url")).isFalse();
  }

  @Test
  void explicitFlagAlwaysWins() {
    assertThat(TubitakTspDetector.resolveTubitakTspMode(true, "http://tsa.example.com")).isTrue();
    assertThat(TubitakTspDetector.resolveTubitakTspMode(false, "http://zd.kamusm.gov.tr")).isTrue();
    assertThat(TubitakTspDetector.resolveTubitakTspMode(false, "http://tsa.example.com")).isFalse();
  }
}
