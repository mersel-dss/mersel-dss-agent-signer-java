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

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * Daemon hazır olduğunda sistem tepsisinde (system tray) görünen küçük simge ve sağ-tık menüsü.
 *
 * <p>Menü öğeleri: API dökümanı (Scalar UI) aç, sağlık endpoint'ini aç, çıkış. Güncelleme
 * mekanizması yeni sürüm bulduğunda {@link #notifyUpdateAvailable(String, String)} çağrısı ile
 * dinamik bir "Yeni sürüm vX.Y.Z — indir" menü öğesi eklenir ve tray balloon bildirimi gösterilir.
 *
 * <p>Headless ortamlarda ya da {@code SystemTray.isSupported() == false} olduğunda tüm metotlar
 * no-op olur; daemon CLI/server modunda hatasız çalışmaya devam eder. macOS ve bazı Linux
 * masaüstlerinde {@code SystemTray} ayarları farklı davranabilir; hataları LOG'a düşer ama daemon'u
 * asla durdurmayız.
 */
public final class SystemTrayManager {

  private static final Logger LOG = LoggerFactory.getLogger(SystemTrayManager.class);
  private static final String ICON_RESOURCE = "static/assets/icon.png";

  private final String openUrl;
  private final String healthUrl;
  private final String appTitle;
  private final Runnable bringWindowToFront;
  private final Runnable shutdownWithoutPrompt;

  private TrayIcon trayIcon;
  private PopupMenu popupMenu;
  private MenuItem updateMenuItem;

  public SystemTrayManager(String appTitle, String openUrl, String healthUrl) {
    this(appTitle, openUrl, healthUrl, null, null);
  }

  /**
   * MainWindow'a köprü kuran constructor. {@code bringWindowToFront} verilirse menüye en üste
   * "Pencereyi Aç" öğesi eklenir ve tray icon'a çift tıklama URL açmak yerine pencereyi öne alır.
   * {@code shutdownWithoutPrompt} verilirse çıkış akışı pencereye delege edilir (pencerenin kendi
   * onay diyaloğu varsa tray onay diyaloğunu atlamamak için bu callback opsiyoneldir; null
   * verilirse tray klasik onay+exit davranışı sürer).
   */
  public SystemTrayManager(
      String appTitle,
      String openUrl,
      String healthUrl,
      Runnable bringWindowToFront,
      Runnable shutdownWithoutPrompt) {
    this.appTitle = appTitle == null ? "Mersel DSS Agent Signer" : appTitle;
    this.openUrl = openUrl;
    this.healthUrl = healthUrl;
    this.bringWindowToFront = bringWindowToFront;
    this.shutdownWithoutPrompt = shutdownWithoutPrompt;
  }

  /**
   * Tray icon'u oluşturup ekler. EDT üzerinde çalışır (Swing thread safety). Headless / SystemTray
   * desteklenmiyor ise {@code false} döner.
   */
  public boolean install() {
    if (GraphicsEnvironment.isHeadless()) {
      LOG.debug("System tray atlandı: headless ortam.");
      return false;
    }
    if (!SystemTray.isSupported()) {
      LOG.info("System tray bu işletim sisteminde desteklenmiyor; tray icon eklenmedi.");
      return false;
    }
    try {
      SwingUtilities.invokeAndWait(this::installOnEdt);
      return trayIcon != null;
    } catch (Exception ex) {
      LOG.warn("System tray kurulumu başarısız: {}", ex.getMessage());
      return false;
    }
  }

  private void installOnEdt() {
    Image image = loadIcon();
    if (image == null) {
      LOG.warn("Tray icon resmi yüklenemedi ({}); tray atlandı.", ICON_RESOURCE);
      return;
    }

    popupMenu = new PopupMenu();

    if (bringWindowToFront != null) {
      MenuItem showWindowItem = new MenuItem("Pencereyi Aç");
      showWindowItem.addActionListener(e -> bringWindowToFront.run());
      popupMenu.add(showWindowItem);
      popupMenu.addSeparator();
    }

    MenuItem openItem = new MenuItem("API dökümanını aç");
    openItem.addActionListener(e -> openInBrowser(openUrl));
    popupMenu.add(openItem);

    MenuItem healthItem = new MenuItem("Sağlık kontrolünü aç");
    healthItem.addActionListener(e -> openInBrowser(healthUrl));
    popupMenu.add(healthItem);

    popupMenu.addSeparator();

    MenuItem exitItem = new MenuItem("Çıkış");
    exitItem.addActionListener(e -> confirmAndExit());
    popupMenu.add(exitItem);

    trayIcon = new TrayIcon(image, appTitle + " — çalışıyor", popupMenu);
    trayIcon.setImageAutoSize(true);
    trayIcon.setToolTip(appTitle + " — çalışıyor");
    // Çift tıklama: pencere varsa öne al, yoksa API dökümanını tarayıcıda aç.
    trayIcon.addActionListener(
        e -> {
          if (bringWindowToFront != null) {
            bringWindowToFront.run();
          } else {
            openInBrowser(openUrl);
          }
        });

    try {
      SystemTray.getSystemTray().add(trayIcon);
      LOG.info("System tray icon mounted: {}", appTitle);
    } catch (AWTException ex) {
      LOG.warn("Tray icon eklenemedi: {}", ex.getMessage());
      trayIcon = null;
    }
  }

  /**
   * Yeni sürüm bulunduğunda tray menüsüne dinamik bir "indir" öğesi ekler ve balloon bildirimi
   * gösterir. Idempotent — aynı sürüm için tekrar çağrılırsa tek bir öğe kalır.
   */
  public void notifyUpdateAvailable(String latestVersion, String downloadUrl) {
    if (trayIcon == null || popupMenu == null) {
      return;
    }
    if (latestVersion == null
        || latestVersion.isEmpty()
        || downloadUrl == null
        || downloadUrl.isEmpty()) {
      return;
    }
    SwingUtilities.invokeLater(
        () -> {
          if (updateMenuItem != null) {
            popupMenu.remove(updateMenuItem);
          }
          updateMenuItem = new MenuItem("Yeni sürüm v" + latestVersion + " — indir");
          updateMenuItem.addActionListener(e -> promptDownload(latestVersion, downloadUrl));
          // En üstte olsun
          popupMenu.insert(updateMenuItem, 0);
          popupMenu.insertSeparator(1);
          trayIcon.setToolTip(appTitle + " — yeni sürüm v" + latestVersion + " mevcut");
          try {
            trayIcon.displayMessage(
                "Mersel DSS Agent Signer",
                "Yeni sürüm v" + latestVersion + " mevcut. Tray menüsünden indirebilirsiniz.",
                TrayIcon.MessageType.INFO);
          } catch (RuntimeException ex) {
            LOG.debug("Tray balloon bildirimi gösterilemedi: {}", ex.getMessage());
          }
        });
  }

  /** Tray icon'u kaldırır (uygulama kapanırken). */
  public void remove() {
    if (trayIcon == null) {
      return;
    }
    SwingUtilities.invokeLater(
        () -> {
          try {
            SystemTray.getSystemTray().remove(trayIcon);
          } finally {
            trayIcon = null;
            popupMenu = null;
            updateMenuItem = null;
          }
        });
  }

  private void promptDownload(String latestVersion, String downloadUrl) {
    int choice =
        JOptionPane.showConfirmDialog(
            null,
            "Yeni sürüm v"
                + latestVersion
                + " yayınlandı.\nİndirme sayfasını şimdi açmak ister misiniz?",
            "Mersel DSS Agent Signer — Güncelleme",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
    if (choice == JOptionPane.YES_OPTION) {
      openInBrowser(downloadUrl);
    }
  }

  private void confirmAndExit() {
    int choice =
        JOptionPane.showConfirmDialog(
            null,
            "Uygulama kapatıldığında imzalama aracı desteği gereken işlemleri yapamayacaksınız.\n"
                + "Uygulamayı kapatmak istediğinizden emin misiniz?",
            "Mersel DSS Agent Signer — Çıkış",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice == JOptionPane.YES_OPTION) {
      remove();
      // Pencere lifecycle'ı varsa exit'i delege et (pencerenin disposal/cleanup logic'i tetiklensin
      // — `shutdownWithoutPrompt` zaten onay sormaz, biz burada sorduk). Aksi halde doğrudan exit.
      if (shutdownWithoutPrompt != null) {
        shutdownWithoutPrompt.run();
      } else {
        System.exit(0);
      }
    }
  }

  private void openInBrowser(String url) {
    if (url == null || url.isEmpty()) {
      LOG.debug("Açılacak URL boş.");
      return;
    }
    try {
      if (!Desktop.isDesktopSupported()) {
        LOG.warn("Desktop.browse() bu platformda desteklenmiyor; URL: {}", url);
        return;
      }
      Desktop desktop = Desktop.getDesktop();
      if (!desktop.isSupported(Desktop.Action.BROWSE)) {
        LOG.warn("Desktop.Action.BROWSE desteklenmiyor; URL: {}", url);
        return;
      }
      desktop.browse(new URI(url));
    } catch (IOException | URISyntaxException ex) {
      LOG.warn("URL açılamadı ({}): {}", url, ex.getMessage());
    }
  }

  private static Image loadIcon() {
    try {
      URL iconUrl = new ClassPathResource(ICON_RESOURCE).getURL();
      return Toolkit.getDefaultToolkit().getImage(iconUrl);
    } catch (IOException ex) {
      LOG.debug("Tray icon yüklenemedi ({}): {}", ICON_RESOURCE, ex.getMessage());
      return null;
    }
  }
}
