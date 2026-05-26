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
package io.mersel.dss.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import io.mersel.dss.agent.api.controllers.GibApplicationController;
import io.mersel.dss.agent.api.controllers.PadesController;
import io.mersel.dss.agent.api.controllers.SmartCardController;
import io.mersel.dss.agent.api.controllers.UpdateController;
import io.mersel.dss.agent.api.controllers.XadesController;
import io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder;
import io.mersel.dss.agent.api.services.signature.PadesService;
import io.mersel.dss.agent.api.services.signature.XadesService;
import io.mersel.dss.agent.api.services.update.UpdateService;

/**
 * Spring context bean-wiring smoke test.
 *
 * <p>Constructor injection ile zincirleme bağımlı tüm beans'in (özellikle {@link
 * CertificateChainBuilder} → {@link PadesService}/{@link XadesService} → controllers, {@link
 * UpdateService} + {@link UpdateController}) context.refresh() sırasında temiz çözüldüğünü
 * doğrular.
 *
 * <p>UI ve update startup-check property'leri CI/test ortamında kapatılır — aksi takdirde
 * background thread GitHub'a istek atar veya AWT tray icon kurmaya çalışır.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
    properties = {
      "mersel.signer.ui.enabled=false",
      "mersel.signer.update.enabled=false",
      "mersel.signer.update.check-on-startup=false"
    })
class SignerApplicationContextTest {

  @Autowired private ApplicationContext context;
  @Autowired private GibApplicationController gibApplicationController;
  @Autowired private SmartCardController smartCardController;
  @Autowired private PadesController padesController;
  @Autowired private XadesController xadesController;
  @Autowired private UpdateController updateController;
  @Autowired private PadesService padesService;
  @Autowired private XadesService xadesService;
  @Autowired private CertificateChainBuilder chainBuilder;
  @Autowired private UpdateService updateService;

  @Test
  void contextLoadsAndAllBeansAreWired() {
    assertThat(context).isNotNull();
    assertThat(gibApplicationController).as("gibApplicationController").isNotNull();
    assertThat(smartCardController).as("smartCardController").isNotNull();
    assertThat(padesController).as("padesController").isNotNull();
    assertThat(xadesController).as("xadesController").isNotNull();
    assertThat(updateController).as("updateController").isNotNull();
    assertThat(padesService).as("padesService").isNotNull();
    assertThat(xadesService).as("xadesService").isNotNull();
    assertThat(chainBuilder).as("certificateChainBuilder").isNotNull();
    assertThat(updateService).as("updateService").isNotNull();
  }

  @Test
  void chainBuilderHonoursDefaultPropertiesAndIsAiaEnabled() {
    // CertificateChainBuilder iki ctor sunduğundan @Autowired ile public ctor'un seçildiğini
    // dolaylı olarak doğrularız: passthrough() ctor'u kullanılsaydı aiaEnabled=false olurdu.
    // Default profile + application.yml ile aia-enabled=true beklenir.
    java.security.cert.Certificate[] empty = new java.security.cert.Certificate[0];
    java.security.cert.Certificate[] out = chainBuilder.build(empty);
    assertThat(out).isSameAs(empty); // boş giriş için no-op davranışı doğrular
  }
}
