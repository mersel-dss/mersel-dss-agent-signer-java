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

import java.awt.GraphicsEnvironment;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.SignerApplication;
import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.services.update.UpdateGate;
import io.mersel.dss.agent.api.services.update.UpdateInfo;
import io.mersel.dss.agent.api.services.update.UpdateService;

/**
 * Spring Boot tam ayağa kalktığında ({@link ApplicationReadyEvent}) tetiklenir:
 *
 * <ol>
 *   <li>Açık olan splash window'unu kapatır.
 *   <li>UI etkinse Mersel banner + hızlı eylem butonları içeren ana pencereyi gösterir.
 *   <li>UI etkinse system tray icon'u mount eder; ana pencere açıksa tray menüsünde "Pencereyi Aç"
 *       öğesi otomatik belirir ve tray çıkışı pencerenin shutdown akışına delege edilir.
 *   <li>{@code update.check-on-startup=true} ise arka plan daemon thread'inde GitHub Releases
 *       sorgusu başlatır; yeni sürüm bulursa tray menüsüne dinamik "indir" öğesi ekler ve balloon
 *       bildirimi gösterir.
 * </ol>
 *
 * <p>Headless ortamlarda tüm UI işlemleri sessizce atlanır; sadece güncelleme kontrolü log'a
 * yansır.
 */
@Component
public class DesktopUiBootstrap {

  private static final Logger LOG = LoggerFactory.getLogger(DesktopUiBootstrap.class);

  private final SignerProperties properties;
  private final UpdateService updateService;
  private final UpdateGate updateGate;
  private final int serverPort;
  private final String serverAddress;
  private final String contextPath;

  private volatile SystemTrayManager trayManager;
  private volatile ScheduledExecutorService updateScheduler;

  public DesktopUiBootstrap(
      SignerProperties properties,
      UpdateService updateService,
      UpdateGate updateGate,
      @Value("${server.port:15211}") int serverPort,
      @Value("${server.address:127.0.0.1}") String serverAddress,
      @Value("${server.servlet.context-path:/}") String contextPath) {
    this.properties = properties;
    this.updateService = updateService;
    this.updateGate = updateGate;
    this.serverPort = serverPort;
    this.serverAddress = serverAddress;
    this.contextPath = contextPath;
  }

  @EventListener
  public void onApplicationReady(ApplicationReadyEvent event) {
    closeSplashSafely();
    boolean windowOpened = false;
    if (shouldOpenWindow()) {
      windowOpened = openMainWindow();
    }
    if (shouldInstallTray()) {
      installTray(windowOpened);
    }
    // UpdateGate listener'ı: gate state değiştiğinde ana pencere otomatik yeşil → turuncu kart'a
    // veya tersine geçer. Listener pencere açılmasa bile install edilir; pencere açıldığında
    // pendingUpdate üzerinden son state apply edilir.
    updateGate.setListener(MainWindowLifecycle::applyUpdateState);
    if (properties.getUpdate().isCheckOnStartup()) {
      runUpdateCheckInBackground();
    }
    scheduleRecheckIfConfigured();
  }

  /** Scheduler clean-up — Spring context shutdown'da thread'leri sızdırma. */
  @PreDestroy
  public void shutdown() {
    ScheduledExecutorService sched = this.updateScheduler;
    if (sched != null) {
      sched.shutdownNow();
      this.updateScheduler = null;
    }
  }

  /* ---------------- splash ---------------- */

  private void closeSplashSafely() {
    try {
      SplashLifecycle.close();
    } catch (RuntimeException re) {
      LOG.debug("Splash kapatılırken hata: {}", re.getMessage());
    }
  }

  /* ---------------- main window ---------------- */

  private boolean shouldOpenWindow() {
    if (!properties.getUi().isEnabled() || !properties.getUi().isWindowEnabled()) {
      LOG.debug("Ana pencere devre dışı (config).");
      return false;
    }
    if (GraphicsEnvironment.isHeadless()) {
      LOG.debug("Ana pencere atlandı (headless).");
      return false;
    }
    return true;
  }

  private boolean openMainWindow() {
    UrlBundle urls = computeUrlBundle();
    String version = readVersion();
    try {
      // Pencerenin "Uygulamayı Kapat" akışı: önce tray varsa onu temizle, sonra JVM exit. Tray
      // henüz mount edilmemiş olabilir; bu callback exit anında çalıştığı için trayManager
      // referansını VOLATİL alanından okumak race-free.
      MainWindowLifecycle.show(
          version,
          urls.openUrl,
          urls.healthUrl,
          () -> {
            SystemTrayManager mgr = this.trayManager;
            if (mgr != null) {
              try {
                mgr.remove();
              } catch (RuntimeException re) {
                LOG.debug("Tray kaldırma hatası (kapatma sırasında): {}", re.getMessage());
              }
            }
            System.exit(0);
          });
      return true;
    } catch (RuntimeException re) {
      LOG.warn("Ana pencere açılırken hata: {}", re.getMessage());
      return false;
    }
  }

  /* ---------------- tray ---------------- */

  private boolean shouldInstallTray() {
    if (!properties.getUi().isEnabled() || !properties.getUi().isTrayEnabled()) {
      LOG.debug("System tray devre dışı (config).");
      return false;
    }
    if (GraphicsEnvironment.isHeadless()) {
      LOG.debug("System tray atlandı (headless).");
      return false;
    }
    return true;
  }

  private void installTray(boolean windowAvailable) {
    UrlBundle urls = computeUrlBundle();

    Runnable bringWindowToFront = windowAvailable ? MainWindowLifecycle::bringToFront : null;
    Runnable shutdownDelegate = windowAvailable ? MainWindowLifecycle::shutdownWithoutPrompt : null;

    try {
      SystemTrayManager mgr =
          new SystemTrayManager(
              "Mersel DSS Agent Signer",
              urls.openUrl,
              urls.healthUrl,
              bringWindowToFront,
              shutdownDelegate);
      if (mgr.install()) {
        this.trayManager = mgr;
      }
    } catch (RuntimeException re) {
      LOG.warn("System tray kurulumu sırasında hata: {}", re.getMessage());
    }
  }

  /* ---------------- url + version helpers ---------------- */

  private UrlBundle computeUrlBundle() {
    String baseUrl = "http://" + serverAddress + ":" + serverPort;
    String normalizedContext = (contextPath == null || contextPath.equals("/")) ? "" : contextPath;
    String configured = properties.getUi().getOpenUrl();
    String openUrl =
        (configured != null && !configured.isEmpty())
            ? configured
            : baseUrl + normalizedContext + "/";
    String healthUrl = baseUrl + normalizedContext + "/actuator/health";
    return new UrlBundle(openUrl, healthUrl);
  }

  private static String readVersion() {
    // SignerApplication root paketinde — JAR manifest'i Implementation-Version'ı root paketle
    // ilişkilendirir. Splash ile aynı kaynaktan okuyoruz; UI değerlerinin tutarlı olması için.
    try {
      Package pkg = SignerApplication.class.getPackage();
      String v = (pkg == null) ? null : pkg.getImplementationVersion();
      return (v == null || v.isEmpty()) ? "0.0.0-dev" : v;
    } catch (RuntimeException re) {
      return "0.0.0-dev";
    }
  }

  private static final class UrlBundle {
    final String openUrl;
    final String healthUrl;

    UrlBundle(String openUrl, String healthUrl) {
      this.openUrl = openUrl;
      this.healthUrl = healthUrl;
    }
  }

  /* ---------------- update ---------------- */

  private void runUpdateCheckInBackground() {
    Thread t = new Thread(this::performUpdateCheck, "mersel-update-check");
    t.setDaemon(true);
    t.start();
  }

  /**
   * UpdateService.currentStatus() çağrısı UpdateGate'i besler — gate de listener üzerinden ana
   * pencereyi günceller. Burada ek olarak tray balloon bildirimi tetikliyoruz (pencere minimize
   * edilmişse / kullanıcı tray'i izliyorsa kanıt olsun).
   */
  private void performUpdateCheck() {
    try {
      UpdateInfo status = updateService.currentStatus(true);
      if (status != null && status.isUpdateAvailable()) {
        LOG.info(
            "Yeni sürüm bulundu: {} → {} ({})",
            status.getCurrentVersion(),
            status.getLatestVersion(),
            status.getDownloadUrl());
        SystemTrayManager mgr = this.trayManager;
        if (mgr != null) {
          mgr.notifyUpdateAvailable(status.getLatestVersion(), status.getDownloadUrl());
        }
      } else {
        LOG.debug("Güncelleme kontrolü: yeni sürüm yok ya da kontrol başarısız.");
      }
    } catch (RuntimeException re) {
      LOG.debug("Güncelleme kontrolü background thread'inde hata: {}", re.getMessage());
    }
  }

  /**
   * Kullanıcı daemon'u günlerce açık tutabilir; tek seferlik startup check yetmez. {@code
   * recheck-interval-minutes} dakikada bir recheck yapan tek-thread'lik daemon scheduler kurar.
   * Negatif veya 0 değer scheduler'ı kapatır.
   */
  private void scheduleRecheckIfConfigured() {
    int minutes = properties.getUpdate().getRecheckIntervalMinutes();
    if (!properties.getUpdate().isEnabled() || minutes <= 0) {
      LOG.debug("Periyodik güncelleme recheck devre dışı.");
      return;
    }
    ScheduledExecutorService sched =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "mersel-update-recheck");
              t.setDaemon(true);
              return t;
            });
    long intervalMs = TimeUnit.MINUTES.toMillis(minutes);
    sched.scheduleAtFixedRate(
        this::performUpdateCheck, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    this.updateScheduler = sched;
    LOG.info("Periyodik güncelleme recheck planlandı: her {} dakikada bir.", minutes);
  }

  /** Test/diagnostic için tray manager'a erişim (paket-içi). */
  SystemTrayManager getTrayManagerForTest() {
    return trayManager;
  }
}
