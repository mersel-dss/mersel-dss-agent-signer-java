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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mersel.dss.agent.api.models.SmartCardDetail;
import io.mersel.dss.agent.api.models.SmartCardResponse;
import io.mersel.dss.agent.api.services.certificate.CertificateListingService;
import io.mersel.dss.agent.api.services.smartcard.CardType;
import io.mersel.dss.agent.api.services.smartcard.SmartCardInfo;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;

/**
 * {@link SmartCardController#listCards()} controller-level davranışı — yanıt body'sinin host
 * metadata (osName / osVersion / osArch / javaVersion) alanlarını içerdiğini ve {@link
 * SmartCardReaderService#listCardsWithMeta()} çıktısının {@link SmartCardDetail}'lere doğru
 * map'lendiğini doğrular.
 *
 * <p>Frontend, vendor lib path'i (.dylib / .dll / .so + arch suffix) ve OS-spesifik UX (örn.
 * Windows'ta tray icon davranışı) belirlemek için bu metadata'yı kullanır; bu yüzden controller
 * seviyesinde de garantilenmesi gerekir — `SmartCardResponseTest` sadece JSON şeklini doğruluyordu.
 */
class SmartCardControllerHostMetadataTest {

  @Test
  void listCardsReturnsHostMetadataPopulatedFromSystemProperties() {
    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    CertificateListingService listing = mock(CertificateListingService.class);
    when(reader.listCardsWithMeta()).thenReturn(Collections.<SmartCardInfo>emptyList());

    SmartCardController controller = new SmartCardController(reader, listing);
    ResponseEntity<SmartCardResponse> resp = controller.listCards();

    assertThat(resp.getStatusCodeValue()).isEqualTo(200);
    SmartCardResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getOsName()).isNotBlank().isEqualTo(System.getProperty("os.name"));
    assertThat(body.getOsVersion()).isNotBlank().isEqualTo(System.getProperty("os.version"));
    assertThat(body.getOsArch()).isNotBlank().isEqualTo(System.getProperty("os.arch"));
    assertThat(body.getJavaVersion()).isNotBlank().isEqualTo(System.getProperty("java.version"));
    assertThat(body.getCards()).isEmpty();
  }

  @Test
  void listCardsMapsCardInfoIntoSmartCardDetail() {
    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    CertificateListingService listing = mock(CertificateListingService.class);

    CardType akis =
        new CardType(
            "AKIS",
            Arrays.asList("/usr/local/lib/libakisp11.dylib"),
            Collections.<String, java.util.List<String>>emptyMap(),
            Collections.<String>emptyList());
    SmartCardInfo info =
        new SmartCardInfo("ACR39U", "3B7F18000000638031C0735C019C03C9C0C0C0", akis);
    when(reader.listCardsWithMeta()).thenReturn(Collections.singletonList(info));

    SmartCardController controller = new SmartCardController(reader, listing);
    ResponseEntity<SmartCardResponse> resp = controller.listCards();

    SmartCardResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getCards()).hasSize(1);
    SmartCardDetail detail = body.getCards().get(0);
    assertThat(detail.getTerminalName()).isEqualTo("ACR39U");
    assertThat(detail.getAtr()).isEqualTo("3B7F18000000638031C0735C019C03C9C0C0C0");
    assertThat(detail.getCardType()).isEqualTo("AKIS");
    assertThat(detail.getPkcs11Library()).isEqualTo("/usr/local/lib/libakisp11.dylib");
  }

  @Test
  void listCardsLeavesCardTypeNullWhenInfoUnrecognised() {
    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    CertificateListingService listing = mock(CertificateListingService.class);

    // Bilinmeyen kart: cardType=null — host metadata yine de set'lenmeli.
    SmartCardInfo info = new SmartCardInfo("Generic Reader", "3BAABBCCDDEEFF", null);
    when(reader.listCardsWithMeta()).thenReturn(Collections.singletonList(info));

    SmartCardController controller = new SmartCardController(reader, listing);
    ResponseEntity<SmartCardResponse> resp = controller.listCards();

    SmartCardResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getCards()).hasSize(1);
    SmartCardDetail detail = body.getCards().get(0);
    assertThat(detail.getCardType()).isNull();
    assertThat(detail.getPkcs11Library()).isNull();
    assertThat(body.getOsName()).isNotBlank();
  }

  @Test
  void listCardsResponseSerializesHostMetadataIntoJson() throws Exception {
    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    CertificateListingService listing = mock(CertificateListingService.class);
    when(reader.listCardsWithMeta()).thenReturn(Collections.<SmartCardInfo>emptyList());

    SmartCardController controller = new SmartCardController(reader, listing);
    SmartCardResponse body = controller.listCards().getBody();
    String json = new ObjectMapper().writeValueAsString(body);

    // Frontend bu 4 alanı parse ediyor — JSON kontrat regression korunmalı.
    assertThat(json).contains("\"osName\"");
    assertThat(json).contains("\"osVersion\"");
    assertThat(json).contains("\"osArch\"");
    assertThat(json).contains("\"javaVersion\"");
    assertThat(json).contains("\"cards\":[]");
  }
}
