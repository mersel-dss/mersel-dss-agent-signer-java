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
package io.mersel.dss.agent.api.services.keystore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.services.smartcard.CardType;
import io.mersel.dss.agent.api.services.smartcard.CardTypeRegistry;

class Pkcs11LibraryResolverTest {

  private static Pkcs11LibraryResolver resolverFor(SignerProperties props) {
    return new Pkcs11LibraryResolver(
        props, new CardTypeRegistry(props, new DefaultResourceLoader()));
  }

  /** Boş giriş + null cardType → resolver hiçbir candidate üretemez, isResolved=false. */
  @Test
  void emptyInputReturnsUnresolved() {
    Pkcs11LibraryResolver resolver = resolverFor(new SignerProperties());
    Pkcs11LibraryResolver.ResolutionResult result = resolver.resolveDetailed(null, null);
    assertThat(result.isResolved()).isFalse();
    assertThat(result.getResolvedPath()).isEmpty();
    assertThat(result.getCandidateFileNames()).isEmpty();
    assertThat(result.getBareNames()).isEmpty();
  }

  /** Bilinen kart tipi verilse de dosya diskte yoksa bare-fallback çalışır. */
  @Test
  void unknownLibFallsBackToBareName() {
    // Dev makinesinde herhangi bir gerçek lib (akisp11) kurulu olabileceğinden
    // gerçek dünyada asla var olmayacak bir bare ad kullanıyoruz.
    String fakeLib = "merselTestNonExistentLib_" + System.nanoTime();
    Pkcs11LibraryResolver resolver = resolverFor(new SignerProperties());
    CardType bogus =
        new CardType(
            "TEST_BOGUS",
            Arrays.asList(fakeLib),
            Collections.<String, List<String>>emptyMap(),
            Collections.<String>emptyList());
    Pkcs11LibraryResolver.ResolutionResult result = resolver.resolveDetailed(null, bogus);

    assertThat(result.isResolved()).isTrue();
    assertThat(result.usedBareFallback()).isTrue();
    assertThat(result.getBareNames()).contains(fakeLib);
    assertThat(result.getCandidateFileNames()).isNotEmpty();
    assertThat(result.describeSearchedPaths()).isNotEmpty();
  }

  @Test
  void candidateFileNamesAreOsAware() {
    Pkcs11LibraryResolver resolver = resolverFor(new SignerProperties());
    CardType akis =
        new CardType(
            "AKIS",
            Arrays.asList("akisp11"),
            Collections.<String, List<String>>emptyMap(),
            Collections.<String>emptyList());
    List<String> candidates = resolver.resolveDetailed(null, akis).getCandidateFileNames();

    switch (resolver.getCurrentOs()) {
      case WINDOWS:
        assertThat(candidates).contains("akisp11.dll");
        break;
      case MACOS:
        assertThat(candidates).contains("libakisp11.dylib", "akisp11.dylib");
        break;
      case LINUX:
        assertThat(candidates).contains("libakisp11.so", "akisp11.so");
        break;
      default:
        // OTHER — sadece bare ad
        assertThat(candidates).contains("akisp11");
    }
  }

  /** application.yml extra search paths, resolver çıktısında öncelikle yer almalı. */
  @Test
  void extraLibSearchPathsRespected() {
    SignerProperties props = new SignerProperties();
    props.setExtraLibSearchPaths(Arrays.asList("/tmp/mersel-test-pkcs11"));
    Pkcs11LibraryResolver resolver = resolverFor(props);
    CardType akis =
        new CardType(
            "AKIS",
            Arrays.asList("akisp11"),
            Collections.<String, List<String>>emptyMap(),
            Collections.<String>emptyList());
    Pkcs11LibraryResolver.ResolutionResult r = resolver.resolveDetailed(null, akis);
    assertThat(r.getSearchedDirectories().toString()).contains("/tmp/mersel-test-pkcs11");
  }

  /** Tübitak ma3api uyumlu vendor dizinleri her OS profilinde yer almalı (sanity check). */
  @Test
  void searchPathSanityCheckPerOs() {
    SignerProperties props = new SignerProperties();
    // application.yml gibi olur — manuel doldurma:
    props.setPkcs11SearchPathsMacos(
        Arrays.asList(
            "/usr/local/lib",
            "/Library/Akia",
            "/Library/Application Support/AKiA",
            "/opt/homebrew/lib"));
    props.setPkcs11SearchPathsLinux(
        Arrays.asList(
            "/usr/lib",
            "/usr/lib/akia",
            "/opt/akia",
            "/opt/eToken/lib",
            "/opt/nfast/toolkits/pkcs11"));
    props.setPkcs11SearchPathsWindows(
        Arrays.asList(
            "C:\\Windows\\System32",
            "C:\\Program Files\\AKiA",
            "C:\\Program Files (x86)\\AKiA",
            "C:\\Program Files\\nCipher\\nfast\\bin"));

    Pkcs11LibraryResolver resolver = resolverFor(props);
    CardType akis =
        new CardType(
            "AKIS",
            Arrays.asList("akisp11"),
            Collections.<String, List<String>>emptyMap(),
            Collections.<String>emptyList());
    String dirs = resolver.resolveDetailed(null, akis).getSearchedDirectories().toString();

    switch (resolver.getCurrentOs()) {
      case MACOS:
        // TÜBİTAK Akia paketi
        assertThat(dirs).contains("/Library/Akia").contains("/usr/local/lib");
        break;
      case LINUX:
        assertThat(dirs).contains("/usr/lib/akia").contains("/opt/eToken/lib");
        break;
      case WINDOWS:
        assertThat(dirs).contains("AKiA");
        break;
      default:
        // noop
    }
  }

  @Test
  void osDetectionMatchesSystemProperty() {
    Pkcs11LibraryResolver resolver = resolverFor(new SignerProperties());
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    if (os.contains("mac") || os.contains("darwin")) {
      assertThat(resolver.getCurrentOs()).isEqualTo(Pkcs11LibraryResolver.Os.MACOS);
    } else if (os.contains("nix") || os.contains("nux")) {
      assertThat(resolver.getCurrentOs()).isEqualTo(Pkcs11LibraryResolver.Os.LINUX);
    } else if (os.contains("win")) {
      assertThat(resolver.getCurrentOs()).isEqualTo(Pkcs11LibraryResolver.Os.WINDOWS);
    }
  }
}
