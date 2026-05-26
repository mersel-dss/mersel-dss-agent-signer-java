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
 * {@link SignerApplication#main(String[])} ile {@code DesktopUiBootstrap} (Spring component)
 * arasında splash referansını köprüleyen küçük static holder. Spring'ten ÖNCE oluşturulduğu için
 * IoC ile değil global state ile yönetilir; yine de tek thread güvenli senkronizasyonla.
 *
 * <p>Headless veya {@code MERSEL_AGENT_UI_SPLASH=false} durumlarda {@link #show(String)} sessizce
 * geçer (no-op). {@link #close()} her zaman idempotenttir.
 */
public final class SplashLifecycle {

  private static SplashWindow current;

  private SplashLifecycle() {}

  /** Splash penceresini ilk kez gösterir. Aynı turda ikinci çağrı yok sayılır. */
  public static synchronized void show(String version) {
    if (current != null) {
      return;
    }
    SplashWindow window = new SplashWindow();
    window.show(version);
    current = window;
  }

  /** Açıksa kapatır; kapalıysa no-op. */
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

  /** Test friendly: gösterimin aktif olup olmadığını sorgular. */
  static synchronized boolean isShowing() {
    return current != null;
  }
}
