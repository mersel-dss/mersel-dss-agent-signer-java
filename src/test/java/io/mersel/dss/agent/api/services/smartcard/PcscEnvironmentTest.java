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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.mersel.dss.agent.api.services.smartcard.PcscEnvironment.OperatingSystem;

/** PCSC native lib resolution mantığını her platform için doğrular. */
class PcscEnvironmentTest {

  private String previousSysProp;

  @BeforeEach
  void clearSysProp() {
    previousSysProp = System.getProperty(PcscEnvironment.SYSPROP_KEY);
    System.clearProperty(PcscEnvironment.SYSPROP_KEY);
  }

  @AfterEach
  void restoreSysProp() {
    if (previousSysProp != null) {
      System.setProperty(PcscEnvironment.SYSPROP_KEY, previousSysProp);
    } else {
      System.clearProperty(PcscEnvironment.SYSPROP_KEY);
    }
  }

  @Test
  void detectOsReturnsCurrentPlatform() {
    OperatingSystem os = PcscEnvironment.detectOs();
    assertThat(os).isNotEqualTo(OperatingSystem.UNKNOWN);
  }

  @Test
  @EnabledOnOs(OS.MAC)
  void macOsAlwaysResolvesPcscFrameworkPathEvenWhenDyldCached() {
    // Big Sur+ üzerinde dosya sisteminde "boş" görünür ama symlink path'ini set ederiz.
    String resolved = PcscEnvironment.detectLibraryFor(OperatingSystem.MACOS);
    assertThat(resolved).isEqualTo(PcscEnvironment.MACOS_PCSC_FRAMEWORK);
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void windowsReturnsNullSoJdkUsesWinScard() {
    String resolved = PcscEnvironment.detectLibraryFor(OperatingSystem.WINDOWS);
    assertThat(resolved).isNull();
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void linuxResolvesFirstExistingPcscliteCandidateIfAvailable() {
    // CI runner'ında libpcsclite kurulu değilse skip; kurulu ise aday listesinden biri
    // bulunmalı.
    String resolved = PcscEnvironment.detectLibraryFor(OperatingSystem.LINUX);
    assumeTrue(
        resolved != null,
        "libpcsclite.so.1 sistemde kurulu değil; aday yollar: "
            + PcscEnvironment.LINUX_PCSC_CANDIDATES);
    assertThat(PcscEnvironment.LINUX_PCSC_CANDIDATES).contains(resolved);
  }

  @Test
  void initializeHonoursExistingSystemProperty() {
    System.setProperty(PcscEnvironment.SYSPROP_KEY, "/custom/path/libpcsclite.so.1");

    PcscEnvironment env = new PcscEnvironment();
    env.initialize();

    assertThat(env.getResolvedLibraryPath()).isEqualTo("/custom/path/libpcsclite.so.1");
    assertThat(env.getResolutionReason()).contains("önceden set");
    assertThat(System.getProperty(PcscEnvironment.SYSPROP_KEY))
        .isEqualTo("/custom/path/libpcsclite.so.1");
  }

  @Test
  void snapshotIncludesOsAndPcscMetadata() {
    PcscEnvironment env = new PcscEnvironment();
    env.initialize();

    java.util.Map<String, String> snap = env.snapshot();
    assertThat(snap).containsKey("os").containsKey("osArch").containsKey("javaVersion");
  }
}
