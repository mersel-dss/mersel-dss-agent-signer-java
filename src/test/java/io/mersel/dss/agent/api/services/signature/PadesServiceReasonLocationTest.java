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

/**
 * {@link PadesService#resolveReason(String)} ve {@link PadesService#resolveLocation(String)}
 * sınırları:
 *
 * <ul>
 *   <li>null / boş string → {@link PadesService#DEFAULT_REASON} ile geriye uyumluluk.
 *   <li>whitespace temizleme (PDF reader'lar boşluğu gösterir, UX kirli olur).
 *   <li>kullanıcı override değerini olduğu gibi kullan.
 * </ul>
 */
class PadesServiceReasonLocationTest {

  @Test
  void resolveReasonNullReturnsDefault() {
    assertThat(PadesService.resolveReason(null)).isEqualTo(PadesService.DEFAULT_REASON);
  }

  @Test
  void resolveReasonBlankReturnsDefault() {
    assertThat(PadesService.resolveReason("")).isEqualTo(PadesService.DEFAULT_REASON);
    assertThat(PadesService.resolveReason("   ")).isEqualTo(PadesService.DEFAULT_REASON);
  }

  @Test
  void resolveReasonTrimsAndReturnsCustomValue() {
    assertThat(PadesService.resolveReason("  Sözleşme imzası  ")).isEqualTo("Sözleşme imzası");
  }

  @Test
  void resolveLocationNullReturnsDefaultEmpty() {
    assertThat(PadesService.resolveLocation(null)).isEqualTo(PadesService.DEFAULT_LOCATION);
  }

  @Test
  void resolveLocationTrimsCustomValue() {
    assertThat(PadesService.resolveLocation("  Istanbul, TR  ")).isEqualTo("Istanbul, TR");
  }

  @Test
  void resolveLocationBlankReturnsDefaultEmpty() {
    assertThat(PadesService.resolveLocation("  ")).isEqualTo(PadesService.DEFAULT_LOCATION);
  }
}
