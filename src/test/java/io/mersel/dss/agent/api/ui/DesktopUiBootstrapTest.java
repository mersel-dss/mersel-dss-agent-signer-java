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
package io.mersel.dss.agent.api.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.services.update.UpdateService;

/**
 * {@link DesktopUiBootstrap} smoke test — Spring context'i kaldırmadan bootstrap akışının headless
 * ortamda güvenle no-op'ladığını doğrular. Ek olarak {@link SignerProperties.Ui#isWindowEnabled()}
 * default'unun {@code true} olduğunu garanti altına alır (config UX kararı: "kullanıcı yeni
 * pencereyi explicit olarak kapatmalı").
 */
class DesktopUiBootstrapTest {

  @AfterEach
  void cleanup() {
    MainWindowLifecycle.close();
    SplashLifecycle.close();
  }

  @Test
  void uiWindowEnabledDefaultIsTrue() {
    SignerProperties.Ui ui = new SignerProperties.Ui();
    assertThat(ui.isEnabled()).isTrue();
    assertThat(ui.isSplashEnabled()).isTrue();
    assertThat(ui.isTrayEnabled()).isTrue();
    assertThat(ui.isWindowEnabled()).isTrue();
  }

  @Test
  void uiWindowEnabledSetterToggles() {
    SignerProperties.Ui ui = new SignerProperties.Ui();
    ui.setWindowEnabled(false);
    assertThat(ui.isWindowEnabled()).isFalse();
    ui.setWindowEnabled(true);
    assertThat(ui.isWindowEnabled()).isTrue();
  }

  @Test
  void onApplicationReadyDoesNotThrowInHeadless() {
    SignerProperties props = new SignerProperties();
    // UI tamamen aktif, ama test JVM headless — bu kombinasyon CI ortamına eşdeğer; tüm UI
    // adımları sessizce no-op olmalı, exception atmamalı.
    UpdateService updateService = mock(UpdateService.class);
    when(updateService.checkForUpdate(false)).thenReturn(java.util.Optional.empty());

    DesktopUiBootstrap boot = new DesktopUiBootstrap(props, updateService, 15211, "127.0.0.1", "/");

    ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
    assertDoesNotThrow(() -> boot.onApplicationReady(event));
  }

  @Test
  void uiDisabledShortCircuitsAllSubsystems() {
    SignerProperties props = new SignerProperties();
    props.getUi().setEnabled(false);
    props.getUpdate().setCheckOnStartup(false);

    UpdateService updateService = mock(UpdateService.class);
    DesktopUiBootstrap boot = new DesktopUiBootstrap(props, updateService, 15211, "127.0.0.1", "/");

    ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
    assertDoesNotThrow(() -> boot.onApplicationReady(event));
    assertThat(boot.getTrayManagerForTest()).isNull();
    assertThat(MainWindowLifecycle.isShowing()).isFalse();
  }
}
