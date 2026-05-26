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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.services.keystore.Pkcs11LibraryResolver;

/**
 * Layer 3 — PKCS#11 module probe.
 *
 * <p>L1 (exact ATR) ve L2 (historical-bytes regex) başarısız olduğunda devreye girer. Diskte
 * bulunan tüm vendor PKCS#11 lib'leri sırayla {@code SunPKCS11} provider'ı ile başlatılır; ilk
 * "kart slot'unda token var" yanıtı veren lib, kartın gerçek vendor'ünü ele verir.
 *
 * <h2>Maliyet ve performans</h2>
 *
 * Her SunPKCS11 init pratikte 200ms-2s sürer (vendor lib JNI dlopen + C_Initialize + slot scan). Bu
 * yüzden:
 *
 * <ul>
 *   <li>Sadece diskte var olan lib'ler denenir (resolver bare-fallback yapmaz).
 *   <li>Sonuç (terminalName + ATR) anahtarıyla 30 saniyelik bellek-içi cache'lenir.
 *   <li>Cache miss durumlarında bile başarılı bir match bulunduğu anda kalan lib'ler atlanır.
 * </ul>
 *
 * <h2>Yan etki yönetimi</h2>
 *
 * Probe sırasında provider {@code Security.addProvider} yapılır; her denemeden sonra hemen {@code
 * Security.removeProvider} ile kaldırılır. Geçici config dosyaları silinir. Hata durumlarında
 * native JNI {@code UnsatisfiedLinkError}, {@code ProviderException}, {@code Error} bile yutulur —
 * yanlış vendor lib'inin yüklenmesi bütün servisi devirmemeli.
 *
 * <h2>Test edilebilirlik</h2>
 *
 * SunPKCS11 init kabuğu {@link ProbeStep} fonksiyonel arayüzü altında soyutlanmıştır; testler
 * mock'lu adımlar ile probe akışını JNI olmadan doğrular.
 */
@Component
public class Pkcs11ModuleProbe {

  private static final Logger log = LoggerFactory.getLogger(Pkcs11ModuleProbe.class);

  /** Probe cache TTL. Kart sabit kaldığı sürece sonucu tutmak için yeterince uzun. */
  static final long PROBE_CACHE_TTL_MS = 30_000L;

  private static final AtomicInteger SEQ = new AtomicInteger(0);

  private final CardTypeRegistry registry;
  private final Pkcs11LibraryResolver resolver;
  private final ProbeStep probeStep;
  private final ConcurrentMap<String, CachedProbe> cache =
      new ConcurrentHashMap<String, CachedProbe>();

  /**
   * Tek bir PKCS#11 lib'i {@code SunPKCS11} provider'ı ile başlatıp slot'taki token'ı görmeye
   * çalışan atomik adım. {@code true} → bu lib gerçek kart vendor'üdür.
   */
  @FunctionalInterface
  public interface ProbeStep {
    boolean tryInit(Path libraryPath);
  }

  private static final class CachedProbe {
    final String cardTypeName; // null = known miss
    final long expiresAt;

    CachedProbe(String cardTypeName, long expiresAt) {
      this.cardTypeName = cardTypeName;
      this.expiresAt = expiresAt;
    }
  }

  @Autowired
  public Pkcs11ModuleProbe(CardTypeRegistry registry, Pkcs11LibraryResolver resolver) {
    this(registry, resolver, Pkcs11ModuleProbe::defaultSunPkcs11Probe);
  }

  /** Test ctor — ProbeStep'i mock'lar. */
  public Pkcs11ModuleProbe(
      CardTypeRegistry registry, Pkcs11LibraryResolver resolver, ProbeStep probeStep) {
    this.registry = registry;
    this.resolver = resolver;
    this.probeStep = probeStep;
  }

  /**
   * Verilen terminal + ATR için kartın PKCS#11 lib'i üzerinden vendor'ünü tespit eder. L1/L2'den
   * sonra çağrılır.
   *
   * @param terminalName cache key parçası ({@code null} olabilir, fakat sonuç cache'lenmez)
   * @param atrHex cache key parçası — kart değiştiğinde cache otomatik bypass olur
   * @return tespit edilebilen ilk kart tipi; hiçbir lib slot'tan token okuyamadıysa boş
   */
  public Optional<CardType> probeCardType(String terminalName, String atrHex) {
    String cacheKey = buildCacheKey(terminalName, atrHex);
    long now = System.currentTimeMillis();
    if (cacheKey != null) {
      CachedProbe cached = cache.get(cacheKey);
      if (cached != null && cached.expiresAt > now) {
        log.debug(
            "L3 probe cache hit (terminal={}, atr={}): {}",
            terminalName,
            atrHex,
            cached.cardTypeName);
        if (cached.cardTypeName == null) return Optional.empty();
        return registry.getByName(cached.cardTypeName);
      }
    }

    Optional<CardType> match = doProbe(terminalName);
    if (cacheKey != null) {
      String name = match.isPresent() ? match.get().getName() : null;
      cache.put(cacheKey, new CachedProbe(name, now + PROBE_CACHE_TTL_MS));
    }
    return match;
  }

  private Optional<CardType> doProbe(String terminalName) {
    for (CardType cardType : registry.all()) {
      for (String libName : cardType.getLibraries()) {
        Pkcs11LibraryResolver.ResolutionResult result = resolver.resolveDetailed(libName, cardType);
        if (!result.isResolved() || result.usedBareFallback()) {
          log.debug("L3 probe atla: {}/{} diskte bulunamadı.", cardType.getName(), libName);
          continue;
        }
        Path libPath = result.getResolvedPath().get();
        boolean ok;
        try {
          ok = probeStep.tryInit(libPath);
        } catch (Throwable t) {
          // Native crash, UnsatisfiedLinkError, ProviderException → yut, devam et.
          log.debug(
              "L3 probe {} ({}): yüklenemedi ({})",
              cardType.getName(),
              libPath,
              t.getClass().getSimpleName() + ": " + t.getMessage());
          continue;
        }
        if (ok) {
          log.info(
              "L3 probe başarılı (terminal={}): kart tipi {} (lib={})",
              terminalName,
              cardType.getName(),
              libPath);
          return Optional.of(cardType);
        }
        log.debug("L3 probe {} ({}): token slot'ta görünmüyor.", cardType.getName(), libPath);
      }
    }
    log.debug("L3 probe (terminal={}): hiçbir vendor lib eşleşmedi.", terminalName);
    return Optional.empty();
  }

  private static String buildCacheKey(String terminalName, String atrHex) {
    if (terminalName == null || atrHex == null) return null;
    return terminalName + "::" + atrHex;
  }

  /** Cache'i manuel temizle (kart değişikliği şüphesinde). */
  public void invalidate() {
    cache.clear();
  }

  /* ----------------- default SunPKCS11 probe step ----------------- */

  /**
   * Default probe adımı: geçici bir config dosyası yaz, {@code sun.security.pkcs11.SunPKCS11}
   * ctor'ü reflection ile çağır, {@code KeyStore.PKCS11.load(null, null)} dene. Başarılıysa lib
   * gerçek kart vendor'üdür.
   *
   * <p>{@code load(null, null)} PIN'siz çağrı: SunPKCS11 token'a "C_OpenSession" yapar ama
   * "C_Login" istemez; bu sayede PIN'siz olarak slot'ta token olup olmadığını anlayabiliriz. Token
   * yoksa ProviderException atılır.
   */
  private static boolean defaultSunPkcs11Probe(Path libraryPath) {
    String name =
        "merselProbe-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
    Path configFile;
    try {
      configFile = writeConfigFile(name, libraryPath.toString());
    } catch (IOException e) {
      log.debug("L3 probe config yazılamadı: {}", e.getMessage());
      return false;
    }
    Provider provider;
    try {
      provider = instantiateSunPkcs11(configFile.toString());
    } catch (Throwable t) {
      silentDelete(configFile);
      // SunPKCS11 ctor "no token present" durumunda da exception atar; debug-only log.
      log.debug(
          "L3 probe init ({}): {}",
          libraryPath,
          t.getClass().getSimpleName() + ": " + t.getMessage());
      return false;
    }
    try {
      Security.addProvider(provider);
      KeyStore ks = KeyStore.getInstance("PKCS11", provider);
      // PIN'siz load — sadece slot'ta token var mı baktırırız. Başarısızsa exception.
      ks.load(null, null);
      return true;
    } catch (Throwable t) {
      log.debug(
          "L3 probe load ({}): {}",
          libraryPath,
          t.getClass().getSimpleName() + ": " + t.getMessage());
      return false;
    } finally {
      try {
        Security.removeProvider(provider.getName());
      } catch (Throwable ignore) {
        /* noop */
      }
      silentDelete(configFile);
    }
  }

  private static Path writeConfigFile(String providerName, String libraryPath) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("name = ").append(providerName).append('\n');
    sb.append("library = ").append(libraryPath).append('\n');
    sb.append("showInfo = false\n");
    Path file = Files.createTempFile("mersel-pkcs11-probe-", ".cfg");
    Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    return file;
  }

  private static Provider instantiateSunPkcs11(String configFilePath) throws Exception {
    Class<?> cls = Class.forName("sun.security.pkcs11.SunPKCS11");
    Constructor<?> ctor = cls.getConstructor(String.class);
    return (Provider) ctor.newInstance(configFilePath);
  }

  private static void silentDelete(Path p) {
    if (p == null) return;
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignore) {
      /* noop */
    }
  }
}
