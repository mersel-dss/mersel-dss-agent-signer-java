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

import io.mersel.dss.agent.api.exceptions.TimestampException;

/** {@link TimestampProvider} parametre çözümleme davranışı. */
class TimestampProviderTest {

  @Test
  void blankUrlThrows() {
    assertThatThrownBy(() -> TimestampProvider.fromRequest("  ", "1", "p", null))
        .isInstanceOf(TimestampException.class)
        .hasMessageContaining("tsaUrl");
  }

  @Test
  void tubitakAutoDetectedFromKamuSmHost() {
    TimestampProvider provider =
        TimestampProvider.fromRequest("http://zd.kamusm.gov.tr", "12345", "secret", null);
    assertThat(provider.isTubitak()).isTrue();
    assertThat(provider.requireCustomerId()).isEqualTo(12345);
    assertThat(provider.hasCredentials()).isTrue();
  }

  @Test
  void standardTsaIsNotTubitakByDefault() {
    TimestampProvider provider =
        TimestampProvider.fromRequest("http://tsa.example.com", null, null, null);
    assertThat(provider.isTubitak()).isFalse();
    assertThat(provider.hasCredentials()).isFalse();
  }

  @Test
  void explicitTubitakFlagForcesMode() {
    TimestampProvider provider =
        TimestampProvider.fromRequest("http://tsa.example.com", "9", "p", Boolean.TRUE);
    assertThat(provider.isTubitak()).isTrue();
  }

  @Test
  void nonNumericCustomerIdThrows() {
    TimestampProvider provider =
        TimestampProvider.fromRequest("http://zd.kamusm.gov.tr", "abc", "p", null);
    assertThatThrownBy(provider::requireCustomerId)
        .isInstanceOf(TimestampException.class)
        .hasMessageContaining("sayısal");
  }
}
