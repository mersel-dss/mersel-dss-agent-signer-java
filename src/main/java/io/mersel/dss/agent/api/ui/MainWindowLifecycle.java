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

/**
 * {@link MainWindow} için {@link SplashLifecycle} ile aynı pattern'i izleyen statik holder. Tray
 * menüsünden "Pencereyi Aç" gibi global eylemler buradan gerçek pencereye köprülenir; çoklu
 * gösterim önlenir.
 *
 * <p>Headless ortamlarda {@link #show(String, String, String, Runnable)} no-op'tur. Tüm metotlar
 * thread-safe ve idempotenttir.
 */
public final class MainWindowLifecycle {

  private static MainWindow current;

  private MainWindowLifecycle() {}

  /**
   * Ana pencereyi gösterir. Zaten gösteriliyorsa öne alır (idempotent). {@code onExitRequest}
   * pencerenin "Uygulamayı Kapat" butonu ya da X tuşu ile çağrıldığında tetiklenir.
   */
  public static synchronized void show(
      String version, String openUrl, String healthUrl, Runnable onExitRequest) {
    if (current != null) {
      current.bringToFront();
      return;
    }
    MainWindow window = new MainWindow(version, openUrl, healthUrl, onExitRequest);
    window.show();
    current = window;
  }

  /** Pencere açıksa ön plana alır; kapalıysa no-op. */
  public static synchronized void bringToFront() {
    if (current == null) {
      return;
    }
    current.bringToFront();
  }

  /** Onay diyaloğu olmadan pencereyi kapatır + exit handler'ını çağırır. Tray menüsü için. */
  public static synchronized void shutdownWithoutPrompt() {
    if (current == null) {
      return;
    }
    MainWindow w = current;
    current = null;
    w.shutdownWithoutPrompt();
  }

  /** Sadece pencereyi kapatır (uygulamayı sonlandırmaz). Idempotent. */
  public static synchronized void close() {
    if (current == null) {
      return;
    }
    try {
      current.close();
    } finally {
      current = null;
    }
  }

  /** Test friendly: pencere holder'ı set edilmiş mi? */
  static synchronized boolean isShowing() {
    return current != null;
  }

  /** Test friendly: paket-içi erişim. */
  static synchronized MainWindow currentForTest() {
    return current;
  }
}
