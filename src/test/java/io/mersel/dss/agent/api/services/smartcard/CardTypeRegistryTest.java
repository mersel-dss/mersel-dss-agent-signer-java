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

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import io.mersel.dss.agent.api.config.SignerProperties;

class CardTypeRegistryTest {

  private static CardTypeRegistry loaded() {
    CardTypeRegistry registry =
        new CardTypeRegistry(new SignerProperties(), new DefaultResourceLoader());
    registry.load();
    return registry;
  }

  @Test
  void layer1ExactAtrMatchKnownAkis() {
    CardTypeRegistry registry = loaded();
    Optional<CardType> hit = registry.findByAtr("3B9F968131FE45806755454B41451112318073B3A180E9");
    assertThat(hit).isPresent();
    assertThat(hit.get().getName()).isEqualTo("AKIS");
  }

  @Test
  void layer1ExactAtrMatchUsersNewCard() {
    // Bu ATR L1'de listede; exact match önce yakalanır.
    CardTypeRegistry registry = loaded();
    Optional<CardType> hit = registry.findByAtr("3B9F978131FE4580655443D3230231C073F62180810552");
    assertThat(hit).isPresent();
    assertThat(hit.get().getName()).isEqualTo("AKIS");
  }

  /** L1 listede olmayan, henüz piyasaya çıkmamış sentetik bir AKIS ATR — L2 regex'ten geçmeli. */
  @Test
  void layer2FallbackMatchesNovelAkisAtr() {
    CardTypeRegistry registry = loaded();
    // Tamamen yeni hayali sürüm byte'ları (FF FF FF) — config'de exact match yok.
    String syntheticAtr = "3B9F978131FE4580655443FFFFFF31C073F62180810500";
    // L1 sıfır olmalı.
    assertThat(registry.findByAtrExact(syntheticAtr)).isEmpty();
    // L1 + L2 birlikte: regex'ten yakalanır.
    Optional<CardType> hit = registry.findByAtr(syntheticAtr);
    assertThat(hit).isPresent();
    assertThat(hit.get().getName()).isEqualTo("AKIS");
  }

  @Test
  void layer2DoesNotMatchAladdin() {
    CardTypeRegistry registry = loaded();
    // ALADDIN bilinen ATR'i — L1 exact match'i yakalar.
    Optional<CardType> hit = registry.findByAtr("3B7F96000080318065B0846160FB120FFD829000");
    assertThat(hit).isPresent();
    assertThat(hit.get().getName()).isEqualTo("ALADDIN");
    // Regex'le ayrı sorgu: ALADDIN'in regex'i yok, AKIS'in regex'leri eşleşmemeli.
    Optional<CardType> regexOnly =
        registry.findByAtrPattern("3B7F96000080318065B0846160FB120FFD829000");
    assertThat(regexOnly).isEmpty();
  }

  @Test
  void candidateNamesAlphabeticAndContainsAkis() {
    CardTypeRegistry registry = loaded();
    List<String> names = registry.candidateNames();
    assertThat(names).contains("AKIS", "ALADDIN", "SAFESIGN", "GEMPLUS");
    // Alfabetik
    List<String> sorted = new java.util.ArrayList<String>(names);
    java.util.Collections.sort(sorted);
    assertThat(names).isEqualTo(sorted);
  }

  @Test
  void emptyAtrReturnsEmpty() {
    CardTypeRegistry registry = loaded();
    assertThat(registry.findByAtr("")).isEmpty();
    assertThat(registry.findByAtr(null)).isEmpty();
  }

  @Test
  void layer2CacheReturnsSameResultOnSecondCall() {
    CardTypeRegistry registry = loaded();
    String synthetic = "3B9F978131FE4580655443DEADBE31C073F62180810500";
    Optional<CardType> first = registry.findByAtr(synthetic);
    Optional<CardType> second = registry.findByAtr(synthetic);
    assertThat(first).isEqualTo(second);
    assertThat(first).isPresent();
  }

  @Test
  void invalidatePatternCacheClearsL2() {
    CardTypeRegistry registry = loaded();
    String synthetic = "3B9F978131FE4580655443CAFEBA31C073F62180810500";
    assertThat(registry.findByAtr(synthetic)).isPresent();
    registry.invalidatePatternCache();
    // Cache temizlendikten sonra yeniden L2'ye düşer — sonuç değişmemeli.
    assertThat(registry.findByAtr(synthetic)).isPresent();
  }
}
