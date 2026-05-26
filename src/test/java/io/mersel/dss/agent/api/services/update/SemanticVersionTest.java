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
package io.mersel.dss.agent.api.services.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

  @Test
  void parsesStandardTriple() {
    Optional<SemanticVersion> v = SemanticVersion.parse("2.0.0");
    assertThat(v).isPresent();
    assertThat(v.get().segments()).containsExactly(2, 0, 0);
    assertThat(v.get().prerelease()).isNull();
  }

  @Test
  void parsesGitHubTagWithVPrefix() {
    Optional<SemanticVersion> v = SemanticVersion.parse("v2.1.0");
    assertThat(v).isPresent();
    assertThat(v.get().toString()).isEqualTo("2.1.0");
  }

  @Test
  void patchUpgradeIsNewer() {
    SemanticVersion a = SemanticVersion.parse("2.0.0").get();
    SemanticVersion b = SemanticVersion.parse("2.0.1").get();
    assertThat(b.isNewerThan(a)).isTrue();
    assertThat(a.isNewerThan(b)).isFalse();
  }

  @Test
  void minorUpgradeIsNewer() {
    SemanticVersion a = SemanticVersion.parse("2.0.5").get();
    SemanticVersion b = SemanticVersion.parse("2.1.0").get();
    assertThat(b.isNewerThan(a)).isTrue();
  }

  @Test
  void majorUpgradeIsNewer() {
    SemanticVersion a = SemanticVersion.parse("2.9.9").get();
    SemanticVersion b = SemanticVersion.parse("3.0.0").get();
    assertThat(b.isNewerThan(a)).isTrue();
  }

  @Test
  void equalVersionsAreNotNewer() {
    SemanticVersion a = SemanticVersion.parse("2.0.0").get();
    SemanticVersion b = SemanticVersion.parse("2.0.0").get();
    assertThat(a.isNewerThan(b)).isFalse();
    assertThat(b.isNewerThan(a)).isFalse();
    assertThat(a).isEqualTo(b);
  }

  @Test
  void nullAndEmptyReturnEmpty() {
    assertThat(SemanticVersion.parse(null)).isEmpty();
    assertThat(SemanticVersion.parse("")).isEmpty();
    assertThat(SemanticVersion.parse("   ")).isEmpty();
    assertThat(SemanticVersion.parse("v")).isEmpty();
  }

  @Test
  void malformedReturnsEmpty() {
    assertThat(SemanticVersion.parse("notaversion")).isEmpty();
    assertThat(SemanticVersion.parse("2.x.0")).isEmpty();
    assertThat(SemanticVersion.parse("..")).isEmpty();
  }

  @Test
  void shortVersionsParseToImplicitZero() {
    SemanticVersion v = SemanticVersion.parse("3").get();
    assertThat(v.segments()).containsExactly(3);
    SemanticVersion w = SemanticVersion.parse("3.1").get();
    assertThat(w.segments()).containsExactly(3, 1);
    // 3 vs 3.0.0 → equal (trailing zeros)
    assertThat(SemanticVersion.parse("3").get()).isEqualTo(SemanticVersion.parse("3.0.0").get());
    assertThat(SemanticVersion.parse("3.1").get()).isEqualTo(SemanticVersion.parse("3.1.0").get());
  }

  @Test
  void prereleaseIsOlderThanStable() {
    SemanticVersion stable = SemanticVersion.parse("2.0.0").get();
    SemanticVersion rc = SemanticVersion.parse("2.0.0-rc1").get();
    assertThat(stable.isNewerThan(rc)).isTrue();
    assertThat(rc.isNewerThan(stable)).isFalse();
  }

  @Test
  void stripsBuildMetadata() {
    SemanticVersion v = SemanticVersion.parse("2.0.0+build.123").get();
    assertThat(v.segments()).containsExactly(2, 0, 0);
  }
}
