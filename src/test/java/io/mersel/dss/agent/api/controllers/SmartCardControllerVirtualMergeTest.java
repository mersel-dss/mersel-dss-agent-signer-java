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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;

import io.mersel.dss.agent.api.models.SmartCardDetail;
import io.mersel.dss.agent.api.models.SmartCardResponse;
import io.mersel.dss.agent.api.services.certificate.CertificateListingService;
import io.mersel.dss.agent.api.services.signature.MechanismCapabilityService;
import io.mersel.dss.agent.api.services.smartcard.SmartCardPinValidator;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;
import io.mersel.dss.agent.api.services.virtualtoken.VirtualTokenRegistry;

/** {@code GET /smartcard} çıktısının fiziksel + sanal kartları merge ettiğini doğrular. */
class SmartCardControllerVirtualMergeTest {

  @Test
  void listCardsAppendsVirtualTokens(@TempDir Path tmp) throws Exception {
    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    when(reader.listCardsWithMeta()).thenReturn(Collections.emptyList());

    Path lib = Files.write(tmp.resolve("libfake.so"), new byte[] {1, 2, 3});
    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    registry.registerPkcs11("HSM Slot 0", lib.toString());

    SmartCardController controller =
        new SmartCardController(
            reader,
            mock(CertificateListingService.class),
            mock(SmartCardPinValidator.class),
            mock(MechanismCapabilityService.class),
            registry);

    ResponseEntity<SmartCardResponse> resp = controller.listCards();
    SmartCardResponse body = resp.getBody();

    assertThat(body).isNotNull();
    assertThat(body.getCards()).hasSize(1);
    SmartCardDetail virtual = body.getCards().get(0);
    assertThat(virtual.getTerminalName()).isEqualTo("HSM Slot 0");
    assertThat(virtual.isVirtual()).isTrue();
    assertThat(virtual.getSource()).isEqualTo("PKCS11");
    assertThat(virtual.getCardType()).isEqualTo("PKCS#11 (HSM)");
    assertThat(virtual.getPkcs11LibraryPath()).isEqualTo(lib.toString());
  }
}
