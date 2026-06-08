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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.dtos.TimestampValidationResponseDto;
import io.mersel.dss.agent.api.exceptions.TimestampException;

/** {@link TimestampService} davranışı (ağ erişimi mock'lanmış {@link TimestampHttpClient} ile). */
class TimestampServiceTest {

  private TimestampHttpClient httpClient;
  private TimestampService service;

  @BeforeEach
  void setUp() {
    httpClient = mock(TimestampHttpClient.class);
    service = new TimestampService(httpClient);
  }

  @Test
  void getTimestampThrowsWhenTsaReturnsGarbage() {
    when(httpClient.postTimestampQuery(any(), any(), any()))
        .thenReturn("definitely-not-a-timestamp".getBytes(StandardCharsets.UTF_8));

    TimestampProvider provider =
        TimestampProvider.fromRequest("http://tsa.example.com", null, null, null);

    assertThatThrownBy(
            () ->
                service.getTimestamp(
                    "hello".getBytes(StandardCharsets.UTF_8), "SHA256", provider, true, true))
        .isInstanceOf(TimestampException.class);

    verify(httpClient).postTimestampQuery(any(), any(), any());
  }

  @Test
  void getTimestampPropagatesTransportError() {
    when(httpClient.postTimestampQuery(any(), any(), any()))
        .thenThrow(new TimestampException("TSA zaman damgası isteği başarısız: HTTP 403"));

    TimestampProvider provider =
        TimestampProvider.fromRequest("http://zd.kamusm.gov.tr", "12345", "secret", null);

    assertThatThrownBy(
            () ->
                service.getTimestamp(
                    "hello".getBytes(StandardCharsets.UTF_8), "SHA256", provider, true, true))
        .isInstanceOf(TimestampException.class)
        .hasMessageContaining("403");
  }

  @Test
  void validateTimestampWithInvalidTokenReturnsInvalid() {
    TimestampValidationResponseDto response =
        service.validateTimestamp("garbage".getBytes(StandardCharsets.UTF_8), null);

    assertThat(response).isNotNull();
    assertThat(response.isValid()).isFalse();
    assertThat(response.getErrors()).isNotEmpty();
    assertThat(response.getMessage()).isNotBlank();
  }

  @Test
  void validateTimestampWithoutOriginalDoesNotCheckHash() {
    TimestampValidationResponseDto response =
        service.validateTimestamp("garbage".getBytes(StandardCharsets.UTF_8), null);

    assertThat(response.getHashVerified()).isNull();
  }
}
