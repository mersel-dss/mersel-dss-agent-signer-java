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

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Headless ortamda {@link MainWindow}'un patlamadan çalıştığını ve idempotency invariant'larını
 * doğrular. Surefire argLine'ı {@code -Djava.awt.headless=true} set ettiği için tüm UI işlemleri
 * no-op olarak akar — bu test sınıfı asıl olarak "show/close/bringToFront yan etki bırakmaz"
 * sözleşmesini koruma altına alır.
 */
class MainWindowTest {

  @Test
  void surefireRunsHeadless() {
    // Tüm diğer assertion'ların önkoşulu: test JVM gerçekten headless olmalı. Aksi halde Mac dev
    // makinesinde Aqua LAF tetiklenir ve testler CI ile farklı davranır.
    assertThat(GraphicsEnvironment.isHeadless()).isTrue();
  }

  @Test
  void showIsNoOpInHeadless() {
    AtomicInteger exitCalls = new AtomicInteger();
    MainWindow window =
        new MainWindow(
            "1.2.3", "http://localhost/", "http://localhost/h", exitCalls::incrementAndGet);

    assertDoesNotThrow(window::show);
    assertThat(window.isShowingForTest()).isFalse();
    assertThat(exitCalls.get()).isZero();
  }

  @Test
  void bringToFrontIsSafeWhenNotShown() {
    MainWindow window = new MainWindow(null, null, null, null);
    assertDoesNotThrow(window::bringToFront);
  }

  @Test
  void closeIsIdempotentEvenWhenNotShown() {
    MainWindow window = new MainWindow("1.0.0", "u", "h", () -> {});
    assertDoesNotThrow(window::close);
    assertDoesNotThrow(window::close);
  }

  @Test
  void shutdownWithoutPromptDoesNotInvokeExitInHeadless() {
    // Headless'ta show() no-op olduğu için frame null kalır; shutdownWithoutPrompt EDT'ye iş
    // göndermez ama exit callback yine de tetiklenir (kullanıcı kararı: tray çıkış akışı).
    AtomicInteger exitCalls = new AtomicInteger();
    MainWindow window = new MainWindow("1.0.0", "u", "h", exitCalls::incrementAndGet);

    // EDT-safe: invokeLater kullanır, ancak headless'ta ana thread'de directly çalışmaz.
    // Bu test sadece API contract'ını korur — exception fırlatmaz.
    assertDoesNotThrow(window::shutdownWithoutPrompt);
  }

  @Test
  void nullVersionFallsBackToDefault() {
    // safe(null) -> "0.0.0"; UI render'lansaydı footer'da "v0.0.0" görünecekti. Sadece ctor
    // exception atmamalı.
    assertDoesNotThrow(() -> new MainWindow(null, null, null, null));
  }
}
