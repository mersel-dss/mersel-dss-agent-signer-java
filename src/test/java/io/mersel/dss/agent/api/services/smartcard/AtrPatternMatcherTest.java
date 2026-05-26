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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class AtrPatternMatcherTest {

  /** TÜBİTAK ma3api-smartcard-2.3.11 orijinal UEKAE regex'i — eski AKIS NES (2011-2019). */
  private static final Pattern UEKAE =
      Pattern.compile("806755454B4145....318073B3A180", Pattern.CASE_INSENSITIVE);

  /** TÜBİTAK orijinal TC regex'i — yeni AKIS NES + T.C. Kimlik (2020+). */
  private static final Pattern TC =
      Pattern.compile("80655443......31C073F6218081..", Pattern.CASE_INSENSITIVE);

  private static CardType akisWithBothPatterns() {
    return new CardType(
        "AKIS",
        Collections.singletonList("akisp11"),
        Collections.emptyMap(),
        Collections.emptyList(),
        Arrays.asList(UEKAE, TC));
  }

  /* ====================== Layer 2 — happy path ====================== */

  @Test
  void uekaeFamilyMatchesAllVersionVariants() {
    // smartcard-config.xml'den 12 UEKAE varyantı (versiyon byte'ları farklı, vendor parmak izi
    // aynı).
    String[] uekaeVariants = {
      "3B9F968131FE45806755454B41451112318073B3A180E9",
      "3B9F968131FE45806755454B41451212318073B3A180EA",
      "3B9F968131FE45806755454B41451213318073B3A180EB",
      "3B9F968131FE45806755454B41451252318073B3A180AA",
      "3B9F968131FE45806755454B41451253318073B3A180AB",
      "3B9F158131FE45806755454B41451221318073B3A1805A",
      "3B9F968131FE45806755454B41451221318073B3A180D9",
      "3B9F138131FE45806755454B41451221318073B3A1805C",
      "3B9F138131FE45806755454B41451261318073B3A1801C",
      "3B9F158131FE45806755454B41451261318073B3A1801A",
      "3B9F968131FE45806755454B41451261318073B3A18099",
      "3B9F968131FE45806755454B41451292318073B3A1806A",
      "3B9F968131FE45806755454B41451293318073B3A1806B",
      "3B9F968131FE45806755454B41451312318073B3A180EB",
      "3B9F968131FE45806755454B414512A4318073B3A1805C",
      "3B9F968131FE45806755454B414512A5318073B3A1805D",
    };
    CardType akis = akisWithBothPatterns();
    for (String atr : uekaeVariants) {
      Optional<CardType> hit =
          AtrPatternMatcher.matchByHistoricalBytes(atr, Collections.singletonList(akis));
      assertThat(hit).as("UEKAE varyantı '%s' UEKAE regex'ine düşmeli", atr).isPresent();
      assertThat(hit.get().getName()).isEqualTo("AKIS");
    }
  }

  @Test
  void tcFamilyMatchesAllVersionVariants() {
    String[] tcVariants = {
      "3B9F968131FE458065544320201231C073F621808105B3",
      "3B9F978131FE458065544312210031C073F62180810593",
      "3B9F978131FE4580655443D3230231C073F62180810552", // <-- kullanıcının yeni kartı
      // TÜBİTAK ma3api extract'tan ek varyantlar
      "3B9F978131FE458065544312210731C073F62180810594",
      "3B9F978131FE458065544312210731C073F62180810595",
      "3B9F978131FE4580655443D2210831C073F6218081015F",
      "3B9F978131FE458065544312210831C073F6218081019F",
      "3B9F978131FE4580655443D2210831C073F6218081055B",
      "3B9F978131FE4580655443E4210831C073F6218081056D",
      "3B9F968131FE4580655443D3210831C073F6218081055B",
      "3B9F968131FE4580655443D2210831C073F6218081055A",
      "3B9F978131FE458065544312210831C073F6218081059B",
      "3B9F978131FE458065544353228231C073F62180810553",
    };
    CardType akis = akisWithBothPatterns();
    for (String atr : tcVariants) {
      Optional<CardType> hit =
          AtrPatternMatcher.matchByHistoricalBytes(atr, Collections.singletonList(akis));
      assertThat(hit).as("TC varyantı '%s' TC regex'ine düşmeli", atr).isPresent();
    }
  }

  /** Kullanıcının özellikle bahsettiği yeni kart. */
  @Test
  void userNewAkisCardMatchesTcFamily() {
    String atr = "3B9F978131FE4580655443D3230231C073F62180810552";
    CardType akis = akisWithBothPatterns();
    Optional<CardType> hit =
        AtrPatternMatcher.matchByHistoricalBytes(atr, Collections.singletonList(akis));
    assertThat(hit).isPresent();
    assertThat(hit.get().getName()).isEqualTo("AKIS");
  }

  /* ====================== Layer 2 — false-positive guards ====================== */

  @Test
  void aladdinAtrDoesNotMatchAkisPatterns() {
    String aladdin = "3B7F96000080318065B0846160FB120FFD829000";
    CardType akis = akisWithBothPatterns();
    Optional<CardType> hit =
        AtrPatternMatcher.matchByHistoricalBytes(aladdin, Collections.singletonList(akis));
    assertThat(hit).isEmpty();
  }

  @Test
  void safesignAtrDoesNotMatchAkisPatterns() {
    String safesign = "3BBB1800C01031FE4580670412B00303000081053C";
    CardType akis = akisWithBothPatterns();
    Optional<CardType> hit =
        AtrPatternMatcher.matchByHistoricalBytes(safesign, Collections.singletonList(akis));
    assertThat(hit).isEmpty();
  }

  @Test
  void gemplusAtrDoesNotMatchAkisPatterns() {
    String gemplus = "3B7D94000080318065B08301029083009000";
    CardType akis = akisWithBothPatterns();
    Optional<CardType> hit =
        AtrPatternMatcher.matchByHistoricalBytes(gemplus, Collections.singletonList(akis));
    assertThat(hit).isEmpty();
  }

  /** Eski AKIS v1.0 (historical bytes "UEKAE V1.0") regex'lerin hiçbirine düşmez — sadece L1. */
  @Test
  void oldAkisV1AtrDoesNotMatchModernRegexes() {
    String old = "3BBA11008131FE4D55454B41452056312E30AE";
    CardType akis = akisWithBothPatterns();
    // Bu kart L1 exact-match ile yakalanır; L2 regex'lere kasıtlı olarak düşmez.
    Optional<CardType> hit =
        AtrPatternMatcher.matchByHistoricalBytes(old, Collections.singletonList(akis));
    assertThat(hit).isEmpty();
  }

  /* ====================== ATR parsing — graceful failures ====================== */

  @Test
  void shortAtrReturnsEmptyOptional() {
    assertThat(AtrPatternMatcher.extractHistoricalBytesHex("3B")).isEmpty();
    assertThat(AtrPatternMatcher.extractHistoricalBytesHex("")).isEmpty();
  }

  @Test
  void nullAtrReturnsEmptyOptional() {
    assertThat(AtrPatternMatcher.extractHistoricalBytesHex(null)).isEmpty();
  }

  @Test
  void malformedHexReturnsEmptyOptional() {
    assertThat(AtrPatternMatcher.extractHistoricalBytesHex("ZZ")).isEmpty();
    assertThat(AtrPatternMatcher.extractHistoricalBytesHex("3B9")).isEmpty(); // odd length
    assertThat(AtrPatternMatcher.extractHistoricalBytesHex("not-a-hex")).isEmpty();
  }

  @Test
  void atrWithSpacesAndHexPrefixNormalises() {
    String atrWithSpaces = "3B 9F 97 81 31 FE 45 80 65 54 43 D3 23 02 31 C0 73 F6 21 80 81 05 52";
    Optional<String> hb = AtrPatternMatcher.extractHistoricalBytesHex(atrWithSpaces);
    assertThat(hb).isPresent();
    assertThat(hb.get()).startsWith("8065544");
  }

  @Test
  void hexPrefixHandled() {
    Optional<String> hb =
        AtrPatternMatcher.extractHistoricalBytesHex(
            "0x3B9F978131FE4580655443D3230231C073F62180810552");
    assertThat(hb).isPresent();
  }

  /* ====================== matches() boundary checks ====================== */

  @Test
  void matchesReturnsFalseForNullInputs() {
    assertThat(AtrPatternMatcher.matches(null, akisWithBothPatterns())).isFalse();
    assertThat(AtrPatternMatcher.matches("806755454B41450000318073B3A180", null)).isFalse();
  }

  @Test
  void matchesUsesAnchoredSemantics() {
    CardType akis = akisWithBothPatterns();
    // Düz historical bytes — eşleşmeli
    assertThat(AtrPatternMatcher.matches("806755454B41451112318073B3A180", akis)).isTrue();
    // Tail-suffix eklenmiş — anchored regex eşleşmemeli
    assertThat(AtrPatternMatcher.matches("806755454B41451112318073B3A180FF", akis)).isFalse();
  }

  @Test
  void emptyPatternListNeverMatches() {
    CardType bare =
        new CardType(
            "BARE",
            Collections.singletonList("dummy"),
            Collections.emptyMap(),
            Collections.emptyList());
    assertThat(AtrPatternMatcher.matches("806755454B41451112318073B3A180", bare)).isFalse();
    Optional<CardType> miss =
        AtrPatternMatcher.matchByHistoricalBytes(
            "3B9F968131FE45806755454B41451112318073B3A180E9", Collections.singletonList(bare));
    assertThat(miss).isEmpty();
  }

  /* ====================== Integration with full config ====================== */

  @Test
  void matchAgainstMultipleCardTypes() {
    CardType akis = akisWithBothPatterns();
    CardType aladdin =
        new CardType(
            "ALADDIN",
            Collections.singletonList("eTPKCS11"),
            Collections.emptyMap(),
            Collections.emptyList(),
            // ALADDIN için regex yok — bu kart sadece L1 exact match'le tanınır.
            Collections.emptyList());
    List<CardType> all = Arrays.asList(aladdin, akis);
    String atr = "3B9F978131FE4580655443D3230231C073F62180810552";
    Optional<CardType> hit = AtrPatternMatcher.matchByHistoricalBytes(atr, all);
    assertThat(hit).isPresent();
    assertThat(hit.get().getName()).isEqualTo("AKIS");
  }
}
