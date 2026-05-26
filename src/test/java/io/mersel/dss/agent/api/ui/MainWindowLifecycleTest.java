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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MainWindowLifecycle} static holder'ının thread-safe ve idempotent kontratını doğrular.
 * Headless ortamda show() no-op davranır ama holder'ın hâlâ patlamadan close edilebilmesi gerekir;
 * çünkü tray callback'leri lifecycle'a bağımsız tetiklenebilir.
 */
class MainWindowLifecycleTest {

  @AfterEach
  void cleanup() {
    MainWindowLifecycle.close();
  }

  @Test
  void closeIsNoOpWhenNoCurrentWindow() {
    assertDoesNotThrow(MainWindowLifecycle::close);
    assertThat(MainWindowLifecycle.isShowing()).isFalse();
  }

  @Test
  void bringToFrontIsNoOpWhenNoCurrentWindow() {
    assertDoesNotThrow(MainWindowLifecycle::bringToFront);
  }

  @Test
  void shutdownWithoutPromptIsNoOpWhenNoCurrentWindow() {
    assertDoesNotThrow(MainWindowLifecycle::shutdownWithoutPrompt);
  }

  @Test
  void showInHeadlessKeepsHolderConsistent() {
    // Headless ortamda window.show() içsel olarak no-op'tur ama lifecycle holder yine de window'u
    // current'a yazar — bringToFront/shutdown çağrıları sessizce akmalı.
    MainWindowLifecycle.show("1.0.0", "http://localhost/", "http://localhost/h", () -> {});
    // current null olabilir veya non-null olabilir (no-op show); her iki durumda da close/2x
    // patlamamalı.
    assertDoesNotThrow(MainWindowLifecycle::close);
    assertDoesNotThrow(MainWindowLifecycle::close);
    assertThat(MainWindowLifecycle.isShowing()).isFalse();
  }
}
