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
package io.mersel.dss.agent.api.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code application.yml} {@code mersel.signer.*} alanlarını taşır. {@link
 * io.mersel.dss.agent.api.services.smartcard.CardTypeRegistry} ve {@link
 * io.mersel.dss.agent.api.services.keystore.Pkcs11LibraryResolver} tarafından okunur.
 */
@Component
@ConfigurationProperties(prefix = "mersel.signer")
public class SignerProperties {

  /** Default: classpath:smartcard-config.xml — Spring Resource sözdizimi. */
  private String smartcardConfig = "classpath:smartcard-config.xml";

  /** Kullanıcı tarafından elle eklenmiş ek lib arama yolları. */
  private List<String> extraLibSearchPaths = new ArrayList<String>();

  private List<String> pkcs11SearchPathsWindows = new ArrayList<String>();
  private List<String> pkcs11SearchPathsMacos = new ArrayList<String>();
  private List<String> pkcs11SearchPathsLinux = new ArrayList<String>();

  /** AIA tabanlı sertifika zinciri tamamlama ayarları. */
  private final Chain chain = new Chain();

  /** Masaüstü UI (splash screen + system tray) ayarları. */
  private final Ui ui = new Ui();

  /** GitHub Releases tabanlı otomatik güncelleme ayarları. */
  private final Update update = new Update();

  public Chain getChain() {
    return chain;
  }

  public Ui getUi() {
    return ui;
  }

  public Update getUpdate() {
    return update;
  }

  /**
   * AIA (Authority Information Access) URL'lerini takip ederek akıllı kart tek-element zincirini
   * root CA'ya kadar tamamlamak için ayarlar. {@code mersel.signer.chain.*} alanları.
   */
  public static class Chain {

    /**
     * AIA chain building'i aç/kapat. Kapalıyken {@code Pkcs11Session.getCertificateChain}'in
     * döndürdüğü zincir aynen kullanılır.
     */
    private boolean aiaEnabled = true;

    /** Her bir AIA URL'i için maksimum HTTP timeout (millisaniye). */
    private int httpTimeoutMs = 3000;

    /**
     * Zincir derinliği üst sınırı. End-entity dahil; tipik PKI 3-5 katmanlı olduğundan 8 fazlasıyla
     * yeterlidir. Loop koruması olarak da çalışır.
     */
    private int maxDepth = 8;

    public boolean isAiaEnabled() {
      return aiaEnabled;
    }

    public void setAiaEnabled(boolean aiaEnabled) {
      this.aiaEnabled = aiaEnabled;
    }

    public int getHttpTimeoutMs() {
      return httpTimeoutMs;
    }

    public void setHttpTimeoutMs(int httpTimeoutMs) {
      this.httpTimeoutMs = httpTimeoutMs;
    }

    public int getMaxDepth() {
      return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
      this.maxDepth = maxDepth;
    }
  }

  /**
   * Masaüstü UI katmanı için ayarlar ({@code mersel.signer.ui.*}).
   *
   * <p>Daemon kullanıcı makinesinde arka planda çalışır; bu ayarlar küçük bir Swing splash screen'i
   * ve sistem tepsisi (system tray) simgesini kontrol eder. {@code GraphicsEnvironment} headless
   * olduğunda (Docker, CI, sunucu) tüm UI sessizce devre dışı bırakılır.
   */
  public static class Ui {

    /** Tüm UI katmanını kapatır (splash + tray + main window). */
    private boolean enabled = true;

    /** Spring Boot başlamadan önce gösterilen splash window. */
    private boolean splashEnabled = true;

    /** Hazır olduğunda gösterilen system tray icon + menüsü. */
    private boolean trayEnabled = true;

    /**
     * Splash kapandıktan sonra gösterilen ana pencere (Mersel banner + butonlar). Tray'siz salt
     * pencere ya da pencere'siz salt tray senaryolarında esneklik için ayrı bayrak.
     */
    private boolean windowEnabled = true;

    /**
     * Tray menüsündeki "Aç" öğesinin işaret ettiği URL. Boş bırakıldığında {@code
     * http://<server.address>:<server.port>/} (Scalar API reference) otomatik hesaplanır.
     */
    private String openUrl = "";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isSplashEnabled() {
      return splashEnabled;
    }

    public void setSplashEnabled(boolean splashEnabled) {
      this.splashEnabled = splashEnabled;
    }

    public boolean isTrayEnabled() {
      return trayEnabled;
    }

    public void setTrayEnabled(boolean trayEnabled) {
      this.trayEnabled = trayEnabled;
    }

    public boolean isWindowEnabled() {
      return windowEnabled;
    }

    public void setWindowEnabled(boolean windowEnabled) {
      this.windowEnabled = windowEnabled;
    }

    public String getOpenUrl() {
      return openUrl;
    }

    public void setOpenUrl(String openUrl) {
      this.openUrl = openUrl;
    }
  }

  /**
   * GitHub Releases tabanlı otomatik güncelleme kontrolü için ayarlar ({@code
   * mersel.signer.update.*}).
   *
   * <p>Daemon hazır olduğunda {@code ApplicationReadyEvent} dinleyicisi arka plan thread'inde
   * GitHub API'sini çağırır, {@code tag_name} → semantic version karşılaştırması yapar ve yeni
   * sürüm bulursa system tray menüsüne dinamik bir "indir" öğesi ekler. Tüm HTTP / parse hataları
   * yutulur (WARN ile loglanır); daemon ASLA güncelleme kontrolü yüzünden patlamaz.
   */
  public static class Update {

    /** Tüm güncelleme alt sistemini kapatır (kontrol yapılmaz, REST 200 dönmeye devam eder). */
    private boolean enabled = true;

    /** Daemon hazır olduğunda otomatik kontrol et. Kapatıldığında sadece manual POST tetikler. */
    private boolean checkOnStartup = true;

    /** GitHub repo {@code owner/name} formatında. */
    private String repository = "mersel-dss/mersel-dss-agent-signer-java";

    /** GitHub API base URL. Test / GitHub Enterprise için override edilebilir. */
    private String apiBaseUrl = "https://api.github.com";

    /** HTTP timeout (connect + read) milisaniye. */
    private int httpTimeoutMs = 5000;

    /**
     * Yayın asset'leri arasından seçilecek jar adı için regex pattern. İlk pattern'e uyan asset
     * tercih edilir; uymazsa ilk {@code .jar} asset; o da yoksa release {@code html_url}.
     */
    private String assetNamePattern = "mersel-dss-agent-signer-api-.*\\.jar";

    /**
     * {@code true} → prerelease'ler de hesaba katılır ({@code /releases} taranır, ilk uyumlu
     * seçilir). {@code false} (default) → {@code /releases/latest} kullanılır (GitHub prerelease'i
     * dışlar).
     */
    private boolean prereleaseAllowed = false;

    /**
     * Yeni sürüm bulunduğunda eski sürümü "zorunlu güncelleme" moduna sokar:
     *
     * <ul>
     *   <li>Ana pencere "Servis Hazır" yerine kırmızı "Güncelleme Gerekli" kartı + <em>İndir</em>
     *       butonunu render eder.
     *   <li>İmzalama endpoint'leri (`/pades/**`, `/xades/**`, `/smartcard/**`, `/gib/**`) HTTP 426
     *       <em>Upgrade Required</em> + indirme URL'i taşıyan {@code ErrorModel} döner. {@code
     *       /update/**}, {@code /actuator/**}, Scalar UI ve {@code /v3/api-docs} açık kalır
     *       (kullanıcı yine de durumu sorgulayabilsin).
     * </ul>
     *
     * <p>{@code false} → eski davranış: yalnız tray balloon + UI bilgi banner'ı, REST endpoint'leri
     * çalışır. Yerelde test ederken kapatmak için kullanışlı.
     */
    private boolean mandatory = true;

    /**
     * Daemon ayakta kaldığı süre boyunca periyodik recheck aralığı (dakika). Kullanıcı bilgisayarı
     * günlerce kapatmaz; yayınladıktan sonra "kabul ortalama gecikme" budur. {@code 0} ya da
     * negatif değer scheduler'ı kapatır (yalnız startup check + manuel {@code POST /update/check}).
     */
    private int recheckIntervalMinutes = 360;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isCheckOnStartup() {
      return checkOnStartup;
    }

    public void setCheckOnStartup(boolean checkOnStartup) {
      this.checkOnStartup = checkOnStartup;
    }

    public String getRepository() {
      return repository;
    }

    public void setRepository(String repository) {
      this.repository = repository;
    }

    public String getApiBaseUrl() {
      return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
      this.apiBaseUrl = apiBaseUrl;
    }

    public int getHttpTimeoutMs() {
      return httpTimeoutMs;
    }

    public void setHttpTimeoutMs(int httpTimeoutMs) {
      this.httpTimeoutMs = httpTimeoutMs;
    }

    public String getAssetNamePattern() {
      return assetNamePattern;
    }

    public void setAssetNamePattern(String assetNamePattern) {
      this.assetNamePattern = assetNamePattern;
    }

    public boolean isPrereleaseAllowed() {
      return prereleaseAllowed;
    }

    public void setPrereleaseAllowed(boolean prereleaseAllowed) {
      this.prereleaseAllowed = prereleaseAllowed;
    }

    public boolean isMandatory() {
      return mandatory;
    }

    public void setMandatory(boolean mandatory) {
      this.mandatory = mandatory;
    }

    public int getRecheckIntervalMinutes() {
      return recheckIntervalMinutes;
    }

    public void setRecheckIntervalMinutes(int recheckIntervalMinutes) {
      this.recheckIntervalMinutes = recheckIntervalMinutes;
    }
  }

  public String getSmartcardConfig() {
    return smartcardConfig;
  }

  public void setSmartcardConfig(String smartcardConfig) {
    this.smartcardConfig = smartcardConfig;
  }

  public List<String> getExtraLibSearchPaths() {
    return extraLibSearchPaths;
  }

  public void setExtraLibSearchPaths(List<String> extraLibSearchPaths) {
    this.extraLibSearchPaths = extraLibSearchPaths;
  }

  public List<String> getPkcs11SearchPathsWindows() {
    return pkcs11SearchPathsWindows;
  }

  public void setPkcs11SearchPathsWindows(List<String> pkcs11SearchPathsWindows) {
    this.pkcs11SearchPathsWindows = pkcs11SearchPathsWindows;
  }

  public List<String> getPkcs11SearchPathsMacos() {
    return pkcs11SearchPathsMacos;
  }

  public void setPkcs11SearchPathsMacos(List<String> pkcs11SearchPathsMacos) {
    this.pkcs11SearchPathsMacos = pkcs11SearchPathsMacos;
  }

  public List<String> getPkcs11SearchPathsLinux() {
    return pkcs11SearchPathsLinux;
  }

  public void setPkcs11SearchPathsLinux(List<String> pkcs11SearchPathsLinux) {
    this.pkcs11SearchPathsLinux = pkcs11SearchPathsLinux;
  }
}
