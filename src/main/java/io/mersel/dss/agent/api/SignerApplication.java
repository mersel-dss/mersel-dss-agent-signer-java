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

import java.awt.GraphicsEnvironment;
import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import io.mersel.dss.agent.api.ui.SplashLifecycle;
import io.mersel.dss.agent.api.ui.StartupError;
import io.mersel.dss.agent.api.ui.StartupErrorClassifier;
import io.mersel.dss.agent.api.ui.StartupErrorWindow;

/**
 * Spring Boot uygulama girişi.
 *
 * <p>Cross-platform PCSC native kütüphane çözümü {@link
 * io.mersel.dss.agent.api.services.smartcard.PcscEnvironment} bean'i tarafından (Spring context
 * başlangıcında, {@code @PostConstruct} ile) yapılır.
 *
 * <p>Eğer ortam GUI-capable ise ve {@code MERSEL_AGENT_UI=true} + {@code
 * MERSEL_AGENT_UI_SPLASH=true} (default'lar) ise Spring Boot başlatılmadan ÖNCE küçük bir Swing
 * splash window gösterilir; {@link io.mersel.dss.agent.api.ui.DesktopUiBootstrap} {@code
 * ApplicationReadyEvent} dinleyicisi splash'i kapatır ve system tray icon'unu mount eder. Headless
 * / server / Docker ortamlarda hiçbir UI etkinleştirilmez.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SignerApplication {

  /**
   * Config dosyalarını YALNIZCA jar'ın içindeki classpath kaynaklarıyla sınırlar. Spring Boot'un
   * varsayılan harici tarama yolları ({@code file:./}, {@code file:./config/}, {@code
   * file:./config/*}{@code /}) bilinçli olarak devre dışı bırakılır: agent, kullanıcı makinesinde
   * çalışan dışa kapalı bir daemon olduğundan çalışma dizinine bırakılan bir {@code application.*}
   * dosyası ne yapılandırmayı ezebilmeli ne de (örn. DOCTYPE'sız {@code application.xml})
   * başlangıcı bozabilmeli. Çalışma zamanı override'ları yine ortam değişkenleri ({@code
   * MERSEL_AGENT_*}) ve komut satırı argümanlarıyla yapılır; bunlar config-dosyası konumu değil,
   * ayrı property source'lardır ve bu kısıttan etkilenmez.
   */
  private static final String CONFIG_LOCATION = "optional:classpath:/";

  public static void main(String[] args) {
    maybeShowSplash();
    SpringApplication app = new SpringApplication(SignerApplication.class);
    app.setDefaultProperties(Collections.singletonMap("spring.config.location", CONFIG_LOCATION));
    try {
      app.run(args);
    } catch (Throwable startupFailure) {
      // Spring context kalkamadı (örn. port zaten dinleniyor → başka bir örnek çalışıyor). Bu
      // noktada ApplicationReadyEvent hiç tetiklenmez; DesktopUiBootstrap splash'i kapatmaz.
      // Görünür
      // splash JWindow'u non-daemon AWT thread'ini canlı tuttuğu için JVM da kendiliğinden
      // sonlanmaz — kullanıcı sonsuza dek dönen splash'le baş başa kalır. Bu yüzden burada hatayı
      // yakalayıp splash'i kapatıyor, anlaşılır bir hata ekranı gösteriyor ve süreci
      // sonlandırıyoruz.
      handleStartupFailure(startupFailure);
    }
  }

  /**
   * Başlatma hatasını kullanıcıya gösterir ve süreci sonlandırır. GUI mevcutsa {@link
   * StartupErrorWindow} açılır; pencere kapatılınca {@code System.exit(1)} çalışır. Headless / UI
   * kapalı durumda doğrudan stderr'e yazıp non-zero kod ile çıkılır.
   */
  private static void handleStartupFailure(Throwable startupFailure) {
    closeSplashQuietly();
    StartupError error = StartupErrorClassifier.classify(startupFailure);
    System.err.println("[Mersel DSS] " + error.getHeadline() + " — " + error.getMessage());

    boolean uiCapable =
        !GraphicsEnvironment.isHeadless()
            && isFlagEnabled("MERSEL_AGENT_UI", true)
            && isFlagEnabled("MERSEL_AGENT_UI_ERROR_WINDOW", true);
    if (uiCapable) {
      boolean shown =
          StartupErrorWindow.show(error, readImplementationVersion(), () -> System.exit(1));
      if (shown) {
        // Pencere görünür kaldı; AWT thread JVM'i canlı tutar. Kapatıldığında onClose → exit(1).
        return;
      }
    }
    System.exit(1);
  }

  private static void closeSplashQuietly() {
    try {
      SplashLifecycle.close();
    } catch (RuntimeException ignored) {
      // Splash kapatılamadı; hata ekranı yine de gösterilecek.
    }
  }

  /**
   * Spring context'i kalkmadan önce splash'i gösteren minimal entry point logic. Spring property
   * binding henüz hazır olmadığından env var üzerinden bayrak okunur.
   */
  private static void maybeShowSplash() {
    if (GraphicsEnvironment.isHeadless()) {
      return;
    }
    if (!isFlagEnabled("MERSEL_AGENT_UI", true) || !isFlagEnabled("MERSEL_AGENT_UI_SPLASH", true)) {
      return;
    }
    String version = readImplementationVersion();
    try {
      SplashLifecycle.show(version);
    } catch (RuntimeException re) {
      // UI hatası daemon başlamasını engellemez; sadece sessizce yutulur (LOG yok — slf4j henüz
      // init değil).
    }
  }

  private static boolean isFlagEnabled(String envName, boolean defaultValue) {
    String value = System.getenv(envName);
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    return !"false".equalsIgnoreCase(value.trim())
        && !"0".equals(value.trim())
        && !"no".equalsIgnoreCase(value.trim());
  }

  private static String readImplementationVersion() {
    try {
      Package pkg = SignerApplication.class.getPackage();
      String v = (pkg == null) ? null : pkg.getImplementationVersion();
      return (v == null || v.isEmpty()) ? "0.0.0-dev" : v;
    } catch (RuntimeException re) {
      return "0.0.0-dev";
    }
  }
}
