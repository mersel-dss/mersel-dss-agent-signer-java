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
package io.mersel.dss.agent.api.services.smartcard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sun JDK {@code sun.security.smartcardio} provider'ının her platformda doğru PCSC native
 * kütüphanesini yükleyebilmesi için {@code sun.security.smartcardio.library} system property'sini
 * cross-platform olarak yapılandırır.
 *
 * <h2>Neden gerekli?</h2>
 *
 * <ul>
 *   <li><b>macOS (Big Sur 11+ ve özellikle Apple Silicon)</b>: Apple, system framework
 *       binary'lerini <em>dyld shared cache</em>'e taşıdığı için {@link Files#exists(Path,
 *       java.nio.file.LinkOption...)} false dönüyor; ama {@code dlopen()} hâlâ symlink path'i
 *       üzerinden binary'yi yükleyebiliyor. Bu yüzden {@code Files.exists} <em>check'i
 *       yapmadan</em>, Apple'ın stabil symlink path'ini doğrudan set ediyoruz.
 *   <li><b>Linux</b>: dağıtıma göre {@code libpcsclite.so.1} farklı dizinlerde olabilir (Debian
 *       multi-arch: {@code /usr/lib/x86_64-linux-gnu}, RHEL/Fedora: {@code /usr/lib64}). Birden
 *       fazla aday dizini sırayla deneriz.
 *   <li><b>Windows</b>: JDK kendi {@code winscard.dll}'ini sistem PATH üzerinden bulur; ek
 *       konfigürasyona gerek yok.
 * </ul>
 *
 * <h2>Override</h2>
 *
 * Kullanıcı {@code -Dsun.security.smartcardio.library=…} ile başlattıysa biz dokunmayız (kullanıcı
 * kararı önceliklidir). Spring config üzerinden override için {@code MERSEL_AGENT_PCSC_LIBRARY} env
 * var'ı da desteklenir.
 */
@Component
public class PcscEnvironment {

  private static final Logger log = LoggerFactory.getLogger(PcscEnvironment.class);

  static final String SYSPROP_KEY = "sun.security.smartcardio.library";
  static final String ENV_OVERRIDE = "MERSEL_AGENT_PCSC_LIBRARY";

  /**
   * macOS PCSC framework path'i. Big Sur+ ile dosya sisteminde "boş" görünür ama symlink üzerinden
   * dyld cache'e map edilir. {@code Files.exists} check'i yapma.
   */
  static final String MACOS_PCSC_FRAMEWORK =
      "/System/Library/Frameworks/PCSC.framework/Versions/Current/PCSC";

  /**
   * Linux'ta {@code libpcsclite.so.1} için aday yollar. İlk var olan kullanılır. Liste sırası en
   * yaygın dağıtım konvansiyonlarına göre düzenlenmiştir.
   */
  static final List<String> LINUX_PCSC_CANDIDATES =
      Collections.unmodifiableList(
          Arrays.asList(
              // Debian / Ubuntu (multi-arch)
              "/usr/lib/x86_64-linux-gnu/libpcsclite.so.1",
              "/usr/lib/aarch64-linux-gnu/libpcsclite.so.1",
              // RHEL / Fedora / CentOS
              "/usr/lib64/libpcsclite.so.1",
              "/usr/lib/libpcsclite.so.1",
              // /usr/local kurulumları
              "/usr/local/lib/libpcsclite.so.1",
              "/opt/local/lib/libpcsclite.so.1"));

  private OperatingSystem detectedOs;
  private String resolvedLibraryPath;
  private String resolutionReason;

  @PostConstruct
  public void initialize() {
    detectedOs = detectOs();

    String envOverride = trimToNull(System.getenv(ENV_OVERRIDE));
    String existing = trimToNull(System.getProperty(SYSPROP_KEY));

    if (existing != null) {
      resolvedLibraryPath = existing;
      resolutionReason = "system property önceden set";
      log.info("PCSC kütüphanesi (önceden set): {}", existing);
      return;
    }

    if (envOverride != null) {
      System.setProperty(SYSPROP_KEY, envOverride);
      resolvedLibraryPath = envOverride;
      resolutionReason = "ENV var " + ENV_OVERRIDE;
      log.info("PCSC kütüphanesi (ENV {} → set edildi): {}", ENV_OVERRIDE, envOverride);
      return;
    }

    String detected = detectLibraryFor(detectedOs);
    if (detected != null) {
      System.setProperty(SYSPROP_KEY, detected);
      resolvedLibraryPath = detected;
      resolutionReason = "otomatik tespit";
      log.info("PCSC kütüphanesi ({} için otomatik set): {}", detectedOs, detected);
    } else {
      resolvedLibraryPath = null;
      resolutionReason = "varsayılan (JDK yerleşik araması)";
      log.info(
          "PCSC kütüphanesi otomatik tespit edilemedi ({}); JDK yerleşik aramaya bırakıldı."
              + " Kart algılanmıyorsa {} ENV var'ını set edin.",
          detectedOs,
          ENV_OVERRIDE);
    }
  }

  /** Bu metot {@link SmartCardReaderService} tarafından her tarama öncesi çağrılabilir. */
  public OperatingSystem getOs() {
    return detectedOs != null ? detectedOs : detectOs();
  }

  public String getResolvedLibraryPath() {
    return resolvedLibraryPath;
  }

  public String getResolutionReason() {
    return resolutionReason;
  }

  /**
   * Salt-okunur snapshot — {@code GET /smartcard/diagnostics} endpoint'i için JSON serileştirilir.
   */
  public java.util.Map<String, String> snapshot() {
    java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<String, String>();
    m.put("os", String.valueOf(detectedOs));
    m.put("osArch", System.getProperty("os.arch", ""));
    m.put("osVersion", System.getProperty("os.version", ""));
    m.put("javaVersion", System.getProperty("java.version", ""));
    m.put("pcscLibraryPath", resolvedLibraryPath != null ? resolvedLibraryPath : "(default)");
    m.put("pcscResolutionReason", resolutionReason != null ? resolutionReason : "");
    return m;
  }

  /* ------------------------------------------------------------------ */
  /* OS detection                                                        */
  /* ------------------------------------------------------------------ */

  static OperatingSystem detectOs() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac") || os.contains("darwin")) return OperatingSystem.MACOS;
    if (os.contains("win")) return OperatingSystem.WINDOWS;
    if (os.contains("nux") || os.contains("nix") || os.contains("aix"))
      return OperatingSystem.LINUX;
    return OperatingSystem.UNKNOWN;
  }

  static String detectLibraryFor(OperatingSystem os) {
    switch (os) {
      case MACOS:
        return MACOS_PCSC_FRAMEWORK;
      case LINUX:
        return firstExisting(LINUX_PCSC_CANDIDATES);
      case WINDOWS:
        return null;
      default:
        return null;
    }
  }

  private static String firstExisting(List<String> candidates) {
    List<String> tried = new ArrayList<String>();
    for (String c : candidates) {
      try {
        if (Files.exists(Paths.get(c))) return c;
        tried.add(c);
      } catch (RuntimeException ignored) {
        tried.add(c);
      }
    }
    log.debug("Linux PCSC adayları bulunamadı: {}", tried);
    return null;
  }

  private static String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  /** Tespit edilebilen işletim sistemleri. */
  public enum OperatingSystem {
    MACOS,
    LINUX,
    WINDOWS,
    UNKNOWN
  }
}
