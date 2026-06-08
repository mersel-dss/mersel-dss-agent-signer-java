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
package io.mersel.dss.agent.api.services.timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.models.enums.TimestampHashAlgorithm;

/** {@link TimestampHashAlgorithm} eşleştirme davranışı. */
class TimestampHashAlgorithmTest {

  @Test
  void blankOrNullDefaultsToSha256() {
    assertThat(TimestampHashAlgorithm.fromName(null)).isEqualTo(TimestampHashAlgorithm.SHA256);
    assertThat(TimestampHashAlgorithm.fromName("  ")).isEqualTo(TimestampHashAlgorithm.SHA256);
  }

  @Test
  void normalizesCommonVariations() {
    assertThat(TimestampHashAlgorithm.fromName("sha256")).isEqualTo(TimestampHashAlgorithm.SHA256);
    assertThat(TimestampHashAlgorithm.fromName("SHA-256")).isEqualTo(TimestampHashAlgorithm.SHA256);
    assertThat(TimestampHashAlgorithm.fromName("SHA_512")).isEqualTo(TimestampHashAlgorithm.SHA512);
  }

  @Test
  void unknownAlgorithmThrows() {
    assertThatThrownBy(() -> TimestampHashAlgorithm.fromName("MD5"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void forOidResolvesKnownAndReturnsNullForUnknown() {
    assertThat(TimestampHashAlgorithm.forOid("2.16.840.1.101.3.4.2.1"))
        .isEqualTo(TimestampHashAlgorithm.SHA256);
    assertThat(TimestampHashAlgorithm.forOid("9.9.9.9")).isNull();
  }

  @Test
  void carriesOidAndJavaName() {
    assertThat(TimestampHashAlgorithm.SHA256.getJavaName()).isEqualTo("SHA-256");
    assertThat(TimestampHashAlgorithm.SHA256.getOid()).isEqualTo("2.16.840.1.101.3.4.2.1");
    assertThat(TimestampHashAlgorithm.SHA256.getTspOid()).isNotNull();
  }
}
