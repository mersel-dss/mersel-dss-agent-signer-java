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

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mersel.dss.agent.api.models.GibApplicationQueryResponse;

/**
 * GİB {@code basvuruSorgula} yanıtının iki farklı shape'inin (obje + plain string) toleranslı
 * şekilde {@link GibApplicationQueryResponse}'a haritalandığını doğrular.
 *
 * <p>Eski projedeki Gson'lı uygulama {@code "Expected BEGIN_OBJECT but was STRING"} ile 500
 * patlıyordu — burada o regresyonun önüne geçiyoruz.
 */
class GibApplicationControllerParseTest {

  private final GibApplicationController controller =
      new GibApplicationController(null, null, new ObjectMapper());

  @Test
  void parsesJsonObjectResponse() {
    String payload = "{\"message\":\"Mükellef bulundu\",\"status\":\"OK\",\"result\":\"VKN\"}";

    GibApplicationQueryResponse out = controller.parseQueryResponse(payload, true);

    assertThat(out.getMessage()).isEqualTo("Mükellef bulundu");
    assertThat(out.getStatus()).isEqualTo("OK");
    assertThat(out.getResult()).isEqualTo("VKN");
    assertThat(out.isSuccess()).isTrue();
    assertThat(out.getRawResponse()).isEqualTo(payload);
  }

  @Test
  void parsesPlainJsonStringResponseAsErrorMessage() {
    String payload = "\"VKN/TCKN bulunamadı\"";

    GibApplicationQueryResponse out = controller.parseQueryResponse(payload, true);

    assertThat(out.getMessage()).isEqualTo("VKN/TCKN bulunamadı");
    assertThat(out.getStatus()).isEqualTo("ERROR");
    assertThat(out.isSuccess()).isFalse();
    assertThat(out.getRawResponse()).isEqualTo(payload);
  }

  @Test
  void parsesEmptyResponseAsError() {
    GibApplicationQueryResponse out = controller.parseQueryResponse("", true);

    assertThat(out.getStatus()).isEqualTo("ERROR");
    assertThat(out.getMessage()).contains("boş yanıt");
    assertThat(out.isSuccess()).isFalse();
  }

  @Test
  void parsesInvalidJsonAsRawMessage() {
    String payload = "not really json <html>";

    GibApplicationQueryResponse out = controller.parseQueryResponse(payload, false);

    assertThat(out.getStatus()).isEqualTo("ERROR");
    assertThat(out.getMessage()).isEqualTo(payload);
    assertThat(out.isSuccess()).isFalse();
    assertThat(out.getRawResponse()).isEqualTo(payload);
  }

  @Test
  void httpFailureMarksResponseUnsuccessfulEvenForObject() {
    String payload = "{\"message\":\"Hata\",\"status\":\"ERROR\"}";

    GibApplicationQueryResponse out = controller.parseQueryResponse(payload, false);

    assertThat(out.getMessage()).isEqualTo("Hata");
    assertThat(out.getStatus()).isEqualTo("ERROR");
    assertThat(out.isSuccess()).isFalse();
  }

  /**
   * Sahada karşılaştığımız spesifik senaryo: GİB mükellef zaten aktif olduğunda {@code
   * success:true} + {@code status:"4"} döner. Otorite GİB'dir; biz {@code success} alanına asla
   * dokunmayız.
   */
  @Test
  void honorsExplicitSuccessFieldFromGib() {
    String payload =
        "{\"success\":true,\"status\":\"4\","
            + "\"message\":\"e-Fatura başvurusu daha önceden yapılmış ve kullanıcı aktif."
            + "Portal İrsaliye başvurusu yapabilirsiniz.\",\"basvuruTipi\":\"2\"}";

    GibApplicationQueryResponse out = controller.parseQueryResponse(payload, true);

    assertThat(out.isSuccess()).as("GİB success=true geçirilmeli").isTrue();
    assertThat(out.getStatus()).isEqualTo("4");
    assertThat(out.getMessage()).startsWith("e-Fatura başvurusu daha önceden yapılmış");
    assertThat(out.getRawResponse()).isEqualTo(payload);
  }

  @Test
  void honorsExplicitSuccessFalseFromGib() {
    String payload = "{\"success\":false,\"status\":\"99\",\"message\":\"Bilinmeyen hata\"}";

    GibApplicationQueryResponse out = controller.parseQueryResponse(payload, true);

    assertThat(out.isSuccess()).isFalse();
    assertThat(out.getStatus()).isEqualTo("99");
    assertThat(out.getMessage()).isEqualTo("Bilinmeyen hata");
  }
}
