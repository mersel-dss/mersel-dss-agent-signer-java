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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryNotFoundException;
import io.mersel.dss.agent.api.services.keystore.Pkcs11LibraryResolver;

class SmartCardManagerTest {

  private static SmartCardManager build(
      SmartCardReaderService reader,
      CardTypeRegistry registry,
      Pkcs11LibraryResolver resolver,
      Pkcs11ModuleProbe probe) {
    return new SmartCardManager(reader, registry, resolver, probe);
  }

  /** ATR ile kart tipi algılansın ama sürücü diskte yok → zenginleştirilmiş hata. */
  @Test
  void atrDetectedButLibraryMissingThrowsRichException() {
    // Dev makinesinde herhangi bir gerçek vendor lib kurulu olabileceği için
    // diskte asla bulunamayacak unique bir lib adı kullanıyoruz.
    String fakeLib = "merselNoSuchLib_" + System.nanoTime();
    SignerProperties props = new SignerProperties();
    CardTypeRegistry registry = new CardTypeRegistry(props, new DefaultResourceLoader());
    registry.load();
    Pkcs11LibraryResolver resolver = new Pkcs11LibraryResolver(props, registry);

    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    CardType bogus =
        new CardType(
            "AKIS",
            Arrays.asList(fakeLib),
            Collections.<String, java.util.List<String>>emptyMap(),
            Collections.<String>emptyList());
    SmartCardInfo info = new SmartCardInfo("ACS ACR39U ICC Reader", "3B9F97...", bogus);
    when(reader.findByTerminalName("ACS ACR39U ICC Reader")).thenReturn(info);

    Pkcs11ModuleProbe probe = mock(Pkcs11ModuleProbe.class);
    SmartCardManager manager = build(reader, registry, resolver, probe);

    Pkcs11LibraryResolver.Os os = resolver.getCurrentOs();
    if (os == Pkcs11LibraryResolver.Os.MACOS || os == Pkcs11LibraryResolver.Os.WINDOWS) {
      // Strict OS'larda bare-fallback hata ile yakalanır
      assertThatThrownBy(() -> manager.resolveLibrary("ACS ACR39U ICC Reader", null))
          .isInstanceOf(Pkcs11LibraryNotFoundException.class)
          .satisfies(
              ex -> {
                Pkcs11LibraryNotFoundException pl = (Pkcs11LibraryNotFoundException) ex;
                assertThat(pl.getCardType()).isEqualTo("AKIS");
                assertThat(pl.getRequiredLibrary()).isEqualTo(fakeLib);
                assertThat(pl.getSearchedPaths()).isNotEmpty();
                assertThat(pl.getDownloadHint()).contains("kamusm.bilgem.tubitak.gov.tr");
                assertThat(pl.getMessage()).contains("AKIS kartı algılandı").contains(fakeLib);
                // CardType bilindiği için userSelectionRequired false ve candidates boş.
                assertThat(pl.isUserSelectionRequired()).isFalse();
                assertThat(pl.getCardTypeCandidates()).isEmpty();
              });
    } else {
      // Linux'ta loader'a bare ad ile teslim edilir; hata fırlatılmaz
      assertThat(manager.resolveLibrary("ACS ACR39U ICC Reader", null)).isNotNull();
    }
    // L1 + wrapper'a göre çözüm — L3 probe çağrılmamalı.
    verify(probe, never()).probeCardType(any(), any());
  }

  /** Hiçbir kart algılanmadı + pkcs11LibraryPath da verilmedi → resolver candidate üretemez. */
  @Test
  void noCardAndNoLibPathThrowsRichExceptionWithCandidates() {
    SignerProperties props = new SignerProperties();
    CardTypeRegistry registry = new CardTypeRegistry(props, new DefaultResourceLoader());
    registry.load();
    Pkcs11LibraryResolver resolver = new Pkcs11LibraryResolver(props, registry);

    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    when(reader.findByTerminalName("Unknown Reader")).thenReturn(null);

    Pkcs11ModuleProbe probe = mock(Pkcs11ModuleProbe.class);
    SmartCardManager manager = build(reader, registry, resolver, probe);

    assertThatThrownBy(() -> manager.resolveLibrary("Unknown Reader", null))
        .isInstanceOf(Pkcs11LibraryNotFoundException.class)
        .satisfies(
            ex -> {
              Pkcs11LibraryNotFoundException pl = (Pkcs11LibraryNotFoundException) ex;
              assertThat(pl.getCardType()).isNull();
              assertThat(pl.getRequiredLibrary()).isNull();
              assertThat(pl.getMessage()).contains("Kart otomatik tanınamadı");
              // Layer 5 alanları doldurulmuş olmalı.
              assertThat(pl.isUserSelectionRequired()).isTrue();
              assertThat(pl.getCardTypeCandidates()).contains("AKIS", "ALADDIN", "SAFESIGN");
            });
  }

  /** L1 hit → L2/L3 hiç çağrılmadan kart tipi döner. (mock reader sahte CardType döndürür) */
  @Test
  void layer1HitSkipsLayer3Probe() {
    SignerProperties props = new SignerProperties();
    CardTypeRegistry registry = new CardTypeRegistry(props, new DefaultResourceLoader());
    registry.load();
    Pkcs11LibraryResolver resolver = new Pkcs11LibraryResolver(props, registry);

    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    String fakeLib = "merselL1Test_" + System.nanoTime();
    CardType ct =
        new CardType(
            "AKIS",
            Arrays.asList(fakeLib),
            Collections.<String, List<String>>emptyMap(),
            Arrays.asList("3BBA11008131FE4D55454B41452056312E30AE"));
    when(reader.findByTerminalName("any"))
        .thenReturn(new SmartCardInfo("any", "3BBA11008131FE4D55454B41452056312E30AE", ct));

    Pkcs11ModuleProbe probe = mock(Pkcs11ModuleProbe.class);
    SmartCardManager manager = build(reader, registry, resolver, probe);

    // Linux'ta loader'a bırakılır; macOS/Windows'ta strict fallback exception.
    try {
      manager.resolveLibrary("any", null);
    } catch (Pkcs11LibraryNotFoundException ignored) {
      /* normal on macos/windows */
    }
    verify(probe, never()).probeCardType(any(), any());
  }

  /** L1+L2 miss, L3 hit → kart tipi probe'tan döner. */
  @Test
  void layer3HitWhenLayer1And2MissProvidesCardType() {
    SignerProperties props = new SignerProperties();
    CardTypeRegistry registry = new CardTypeRegistry(props, new DefaultResourceLoader());
    registry.load();
    Pkcs11LibraryResolver resolver = new Pkcs11LibraryResolver(props, registry);

    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    SmartCardInfo unknownAtr =
        new SmartCardInfo("ACR39U", "3BDEADBEEF00DEADBEEF", /*cardType=*/ null);
    when(reader.findByTerminalName("ACR39U")).thenReturn(unknownAtr);

    CardType akis = registry.getByName("AKIS").orElseThrow(() -> new AssertionError("AKIS yok"));

    Pkcs11ModuleProbe probe = mock(Pkcs11ModuleProbe.class);
    when(probe.probeCardType("ACR39U", "3BDEADBEEF00DEADBEEF")).thenReturn(Optional.of(akis));

    SmartCardManager manager = build(reader, registry, resolver, probe);

    try {
      java.nio.file.Path path = manager.resolveLibrary("ACR39U", null);
      assertThat(path.toString()).containsIgnoringCase("akisp11");
    } catch (Pkcs11LibraryNotFoundException ex) {
      // Strict OS'larda bare-fallback exception; kart tipi yine AKIS olmalı.
      assertThat(ex.getCardType()).isEqualTo("AKIS");
    }
    verify(probe).probeCardType("ACR39U", "3BDEADBEEF00DEADBEEF");
  }

  /** Kullanıcı cardType override verdiyse ATR algılama atlanır. */
  @Test
  void manualCardTypeOverrideBypassesAtrLookup() {
    SignerProperties props = new SignerProperties();
    CardTypeRegistry registry = new CardTypeRegistry(props, new DefaultResourceLoader());
    registry.load();
    Pkcs11LibraryResolver resolver = new Pkcs11LibraryResolver(props, registry);

    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    Pkcs11ModuleProbe probe = mock(Pkcs11ModuleProbe.class);
    SmartCardManager manager = build(reader, registry, resolver, probe);

    try {
      java.nio.file.Path path = manager.resolveLibrary("AnyTerm", null, "AKIS");
      assertThat(path.toString()).containsIgnoringCase("akisp11");
    } catch (Pkcs11LibraryNotFoundException ex) {
      assertThat(ex.getCardType()).isEqualTo("AKIS");
    }
    // Reader hiç çağrılmamalı — override en üst öncelik.
    verify(reader, never()).findByTerminalName(any());
    verify(probe, never()).probeCardType(any(), any());
  }
}
