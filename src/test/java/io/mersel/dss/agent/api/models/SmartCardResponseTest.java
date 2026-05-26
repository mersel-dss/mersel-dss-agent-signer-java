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
package io.mersel.dss.agent.api.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/** {@link SmartCardResponse} host metadata + JSON serializasyon davranışı. */
class SmartCardResponseTest {

  @Test
  void withCurrentHostFillsSystemProperties() {
    SmartCardResponse response = new SmartCardResponse().withCurrentHost();

    assertThat(response.getOsName()).isNotBlank().isEqualTo(System.getProperty("os.name"));
    assertThat(response.getOsVersion()).isNotBlank().isEqualTo(System.getProperty("os.version"));
    assertThat(response.getOsArch()).isNotBlank().isEqualTo(System.getProperty("os.arch"));
    assertThat(response.getJavaVersion())
        .isNotBlank()
        .isEqualTo(System.getProperty("java.version"));
  }

  @Test
  void serializesHostMetadataIntoJson() throws Exception {
    SmartCardDetail detail = new SmartCardDetail();
    detail.setTerminalName("Test Reader 0");
    detail.setAtr("3B7F18000000638031C0735C019C03C9C0C0C0C0C0");
    SmartCardResponse response =
        new SmartCardResponse(Collections.singletonList(detail)).withCurrentHost();

    String json = new ObjectMapper().writeValueAsString(response);

    assertThat(json).contains("\"osName\":\"" + System.getProperty("os.name") + "\"");
    assertThat(json).contains("\"osVersion\":\"" + System.getProperty("os.version") + "\"");
    assertThat(json).contains("\"osArch\":\"" + System.getProperty("os.arch") + "\"");
    assertThat(json).contains("\"javaVersion\":\"" + System.getProperty("java.version") + "\"");
    assertThat(json).contains("\"cards\"");
    assertThat(json).contains("\"terminalName\":\"Test Reader 0\"");
  }

  @Test
  void omitsNullHostFieldsWhenWithCurrentHostNotCalled() throws Exception {
    SmartCardResponse response = new SmartCardResponse();
    String json = new ObjectMapper().writeValueAsString(response);

    // @JsonInclude(NON_NULL) host alanlarını gizler — frontend null görmesin.
    assertThat(json).doesNotContain("osName");
    assertThat(json).doesNotContain("javaVersion");
    // Cards listesi null değil (constructor empty list veriyor), serileşir.
    assertThat(json).contains("\"cards\":[]");
  }

  @Test
  void nullCardsConstructorFallsBackToEmptyList() {
    SmartCardResponse response = new SmartCardResponse(null);
    assertThat(response.getCards()).isNotNull().isEmpty();
  }
}
