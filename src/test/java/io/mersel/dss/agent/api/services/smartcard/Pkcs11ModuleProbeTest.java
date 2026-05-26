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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.services.keystore.Pkcs11LibraryResolver;

class Pkcs11ModuleProbeTest {

  /**
   * Helper: registry'i in-memory akisp11 ve eTPKCS11 lib'leriyle doldurur ve TempDir'i
   * extra-search-path olarak ekler. Test PCSC olmadan probe akışını doğrular.
   */
  private static Pkcs11LibraryResolver resolverWithSearchPath(Path searchDir) {
    SignerProperties props = new SignerProperties();
    props.setExtraLibSearchPaths(Collections.singletonList(searchDir.toString()));
    CardTypeRegistry registry = new CardTypeRegistry(props, new DefaultResourceLoader());
    registry.load();
    return new Pkcs11LibraryResolver(props, registry);
  }

  private static CardTypeRegistry registryWith(Path searchDir) {
    SignerProperties props = new SignerProperties();
    props.setExtraLibSearchPaths(Collections.singletonList(searchDir.toString()));
    CardTypeRegistry registry = new CardTypeRegistry(props, new DefaultResourceLoader());
    registry.load();
    return registry;
  }

  /** OS'a göre dosya adı türetir (Pkcs11LibraryResolver ile aynı mantık). */
  private static String fileFor(String os, String bare) {
    if (os.contains("mac") || os.contains("darwin")) return "lib" + bare + ".dylib";
    if (os.contains("win")) return bare + ".dll";
    return "lib" + bare + ".so";
  }

  /* ====================== Cache hit/miss ====================== */

  @Test
  void cacheHitPreventsSecondProbe(@TempDir Path tmp) throws Exception {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    // Bulunabilir bir akisp11 fake'ı oluştur.
    Files.write(tmp.resolve(fileFor(os, "akisp11")), new byte[] {0x7F, 'E', 'L', 'F'});

    Pkcs11LibraryResolver resolver = resolverWithSearchPath(tmp);
    CardTypeRegistry registry = registryWith(tmp);

    AtomicInteger calls = new AtomicInteger(0);
    Pkcs11ModuleProbe.ProbeStep step =
        path -> {
          calls.incrementAndGet();
          return path.toString().toLowerCase(Locale.ROOT).contains("akisp11");
        };
    Pkcs11ModuleProbe probe = new Pkcs11ModuleProbe(registry, resolver, step);

    Optional<CardType> first = probe.probeCardType("ACR39U", "3BCAFEBABE");
    Optional<CardType> second = probe.probeCardType("ACR39U", "3BCAFEBABE");

    assertThat(first).isPresent();
    assertThat(first.get().getName()).isEqualTo("AKIS");
    assertThat(second).isPresent();
    assertThat(calls.get()).isEqualTo(1); // ikinci çağrı cache'ten döndü
  }

  @Test
  void cacheMissForDifferentAtr(@TempDir Path tmp) throws Exception {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    Files.write(tmp.resolve(fileFor(os, "akisp11")), new byte[] {0x7F});

    Pkcs11LibraryResolver resolver = resolverWithSearchPath(tmp);
    CardTypeRegistry registry = registryWith(tmp);
    AtomicInteger calls = new AtomicInteger(0);
    Pkcs11ModuleProbe.ProbeStep step =
        path -> {
          calls.incrementAndGet();
          return path.toString().toLowerCase(Locale.ROOT).contains("akisp11");
        };
    Pkcs11ModuleProbe probe = new Pkcs11ModuleProbe(registry, resolver, step);

    probe.probeCardType("ACR39U", "ATR-A");
    probe.probeCardType("ACR39U", "ATR-B"); // farklı ATR — cache miss
    assertThat(calls.get()).isGreaterThanOrEqualTo(2);
  }

  /* ====================== Probe akışı ====================== */

  @Test
  void probeReturnsEmptyWhenNoLibPhysicallyPresent(@TempDir Path tmp) {
    Pkcs11LibraryResolver resolver = resolverWithSearchPath(tmp);
    CardTypeRegistry registry = registryWith(tmp);
    Pkcs11ModuleProbe.ProbeStep neverCalled =
        path -> {
          throw new AssertionError(
              "ProbeStep çağrılmamalı; hiçbir lib disk üstünde yok. path=" + path);
        };
    Pkcs11ModuleProbe probe = new Pkcs11ModuleProbe(registry, resolver, neverCalled);
    Optional<CardType> result = probe.probeCardType("ACR39U", "3BCAFEBABE");
    assertThat(result).isEmpty();
  }

  @Test
  void probeReturnsFirstSuccessfulMatch(@TempDir Path tmp) throws Exception {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    // İki vendor lib'i de diskte var; ALADDIN önce gelse de step false döndürürken,
    // AKIS true döner. İlk başarılı match alınmalı.
    Files.write(tmp.resolve(fileFor(os, "akisp11")), new byte[] {0});
    Files.write(tmp.resolve(fileFor(os, "etpkcs11")), new byte[] {0});

    Pkcs11LibraryResolver resolver = resolverWithSearchPath(tmp);
    CardTypeRegistry registry = registryWith(tmp);

    // Sadece akisp11 başarılı kabul edilir.
    Pkcs11ModuleProbe.ProbeStep akisOnly =
        path -> path.toString().toLowerCase(Locale.ROOT).contains("akisp11");
    Pkcs11ModuleProbe probe = new Pkcs11ModuleProbe(registry, resolver, akisOnly);

    Optional<CardType> result = probe.probeCardType("ACR39U", "ATR-X");
    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("AKIS");
  }

  @Test
  void probeStepExceptionsAreSwallowed(@TempDir Path tmp) throws Exception {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    Files.write(tmp.resolve(fileFor(os, "akisp11")), new byte[] {0});
    Files.write(tmp.resolve(fileFor(os, "etpkcs11")), new byte[] {0});

    Pkcs11LibraryResolver resolver = resolverWithSearchPath(tmp);
    CardTypeRegistry registry = registryWith(tmp);

    Set<String> attempted = new HashSet<String>();
    Pkcs11ModuleProbe.ProbeStep eachThrows =
        path -> {
          attempted.add(path.getFileName().toString().toLowerCase(Locale.ROOT));
          throw new UnsatisfiedLinkError("simulated " + path);
        };
    Pkcs11ModuleProbe probe = new Pkcs11ModuleProbe(registry, resolver, eachThrows);

    // Tüm exception'lar yutulmalı; sonuç boş optional.
    Optional<CardType> result = probe.probeCardType("ACR39U", "ATR-Y");
    assertThat(result).isEmpty();
    assertThat(attempted).isNotEmpty(); // en az birkaç lib denendi
  }

  @Test
  void invalidateClearsCache(@TempDir Path tmp) throws Exception {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    Files.write(tmp.resolve(fileFor(os, "akisp11")), new byte[] {0});

    Pkcs11LibraryResolver resolver = resolverWithSearchPath(tmp);
    CardTypeRegistry registry = registryWith(tmp);
    AtomicInteger calls = new AtomicInteger(0);
    Pkcs11ModuleProbe.ProbeStep step =
        path -> {
          calls.incrementAndGet();
          return path.toString().toLowerCase(Locale.ROOT).contains("akisp11");
        };
    Pkcs11ModuleProbe probe = new Pkcs11ModuleProbe(registry, resolver, step);

    probe.probeCardType("ACR39U", "ATR-Z");
    probe.invalidate();
    probe.probeCardType("ACR39U", "ATR-Z"); // cache temizlendiği için tekrar probe edilir

    assertThat(calls.get()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void nullKeysSkipCache(@TempDir Path tmp) throws Exception {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    Files.write(tmp.resolve(fileFor(os, "akisp11")), new byte[] {0});
    Pkcs11LibraryResolver resolver = resolverWithSearchPath(tmp);
    CardTypeRegistry registry = registryWith(tmp);
    AtomicInteger calls = new AtomicInteger(0);
    Pkcs11ModuleProbe.ProbeStep step =
        path -> {
          calls.incrementAndGet();
          return path.toString().toLowerCase(Locale.ROOT).contains("akisp11");
        };
    Pkcs11ModuleProbe probe = new Pkcs11ModuleProbe(registry, resolver, step);

    probe.probeCardType(null, null);
    probe.probeCardType(null, null);
    // Null key'ler cache'lenmez; her çağrı tekrar probe yapar.
    assertThat(calls.get()).isGreaterThanOrEqualTo(2);
  }

  /* ====================== Negative probe — kart tipi bulunmadı, cache yine yazılır ====================== */

  @Test
  void cachedMissShortCircuitsSubsequentProbes(@TempDir Path tmp) throws Exception {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    Files.write(tmp.resolve(fileFor(os, "akisp11")), new byte[] {0});

    Pkcs11LibraryResolver resolver = resolverWithSearchPath(tmp);
    CardTypeRegistry registry = registryWith(tmp);
    AtomicInteger calls = new AtomicInteger(0);
    Pkcs11ModuleProbe.ProbeStep allFail =
        path -> {
          calls.incrementAndGet();
          return false;
        };
    Pkcs11ModuleProbe probe = new Pkcs11ModuleProbe(registry, resolver, allFail);

    Optional<CardType> first = probe.probeCardType("term", "atr");
    int afterFirst = calls.get();
    Optional<CardType> second = probe.probeCardType("term", "atr");
    assertThat(first).isEmpty();
    assertThat(second).isEmpty();
    assertThat(calls.get()).isEqualTo(afterFirst); // ikinci çağrı hiçbir probe yapmadı
  }

  /* ====================== Registry sırası ====================== */

  @Test
  void respectsRegistryOrderForProbe(@TempDir Path tmp) throws Exception {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    // Tüm vendor lib'leri var; ProbeStep ilk tetiklenen path'i kaydeder.
    Files.write(tmp.resolve(fileFor(os, "akisp11")), new byte[] {0});
    Files.write(tmp.resolve(fileFor(os, "etpkcs11")), new byte[] {0});
    Files.write(tmp.resolve(fileFor(os, "aetpkss1")), new byte[] {0});

    Pkcs11LibraryResolver resolver = resolverWithSearchPath(tmp);
    CardTypeRegistry registry = registryWith(tmp);

    List<String> tried = new java.util.ArrayList<String>();
    Pkcs11ModuleProbe.ProbeStep firstWins =
        path -> {
          tried.add(path.getFileName().toString().toLowerCase(Locale.ROOT));
          return true; // ilk lib her zaman wins
        };
    Pkcs11ModuleProbe probe = new Pkcs11ModuleProbe(registry, resolver, firstWins);

    Optional<CardType> result = probe.probeCardType("term", "atr");
    assertThat(result).isPresent();
    // Config'de AKIS card-type'ı ilk geldiği için akisp11 önce denenmeli.
    assertThat(tried).hasSize(1);
    assertThat(tried.get(0)).contains("akisp11");
  }

  @Test
  void publicProbeStepInterfaceIsFunctional() {
    Pkcs11ModuleProbe.ProbeStep s = path -> true;
    assertThat(s.tryInit(Paths.get("/tmp/anywhere"))).isTrue();
    Pkcs11ModuleProbe.ProbeStep f = path -> false;
    assertThat(f.tryInit(Paths.get("/tmp/anywhere"))).isFalse();
  }
}
