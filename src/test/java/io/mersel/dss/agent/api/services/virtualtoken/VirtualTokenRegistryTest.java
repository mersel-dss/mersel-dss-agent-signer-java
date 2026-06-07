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
package io.mersel.dss.agent.api.services.virtualtoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.mersel.dss.agent.api.testsupport.PfxTestKey;

/** {@link VirtualTokenRegistry} kayıt/doğrulama/temizleme davranışları. */
class VirtualTokenRegistryTest {

  @Test
  void registersPkcs11WhenLibraryFileExists(@TempDir Path tmp) throws Exception {
    Path lib = Files.write(tmp.resolve("libfake.so"), new byte[] {1, 2, 3});
    VirtualTokenRegistry registry = new VirtualTokenRegistry();

    VirtualToken token = registry.registerPkcs11("HSM Slot 0", lib.toString());

    assertThat(token.isPkcs11()).isTrue();
    assertThat(token.getPkcs11LibraryPath()).isEqualTo(lib.toString());
    assertThat(registry.find("HSM Slot 0")).isSameAs(token);
    assertThat(registry.list()).hasSize(1);
  }

  @Test
  void rejectsPkcs11WhenLibraryMissing() {
    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    assertThatThrownBy(
            () -> registry.registerPkcs11("x", "/yok/merselNoSuchLib_" + System.nanoTime() + ".so"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(registry.size()).isZero();
  }

  @Test
  void rejectsBlankName() {
    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    assertThatThrownBy(() -> registry.registerPkcs11("  ", "/tmp/x.so"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void registersPkcs12WithCorrectPassword() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());
    byte[] pfx = Files.readAllBytes(key.getFile().toPath());

    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    VirtualToken token =
        registry.registerPkcs12("PFX firma", pfx, key.getPassword(), key.getFileName());

    assertThat(token.isPkcs12()).isTrue();
    assertThat(token.getKeyStore()).isNotNull();
    assertThat(token.getDisplayCardType()).isEqualTo("PKCS#12 (PFX)");
    assertThat(registry.find("PFX firma")).isSameAs(token);
  }

  @Test
  void rejectsPkcs12WithWrongPassword() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());
    byte[] pfx = Files.readAllBytes(key.getFile().toPath());

    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    assertThatThrownBy(
            () -> registry.registerPkcs12("PFX", pfx, "yanlis-parola".toCharArray(), "x.pfx"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(registry.size()).isZero();
  }

  @Test
  void rejectsDuplicateName(@TempDir Path tmp) throws Exception {
    Path lib = Files.write(tmp.resolve("libfake.so"), new byte[] {1});
    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    registry.registerPkcs11("dup", lib.toString());

    assertThatThrownBy(() -> registry.registerPkcs11("dup", lib.toString()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(registry.size()).isEqualTo(1);
  }

  @Test
  void removeWipesPasswordAndDropsEntry() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());
    byte[] pfx = Files.readAllBytes(key.getFile().toPath());

    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    VirtualToken token = registry.registerPkcs12("PFX", pfx, key.getPassword(), "x.pfx");

    VirtualToken removed = registry.remove("PFX");
    assertThat(removed).isSameAs(token);
    assertThat(registry.find("PFX")).isNull();
    // wipe sonrası parola sıfırlanmış olmalı (char[] '\0' ile dolu).
    assertThat(new String(token.passwordChars())).isEqualTo("\0\0\0\0\0\0");
  }

  @Test
  void removeUnknownReturnsNull() {
    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    assertThat(registry.remove("yok")).isNull();
  }
}
