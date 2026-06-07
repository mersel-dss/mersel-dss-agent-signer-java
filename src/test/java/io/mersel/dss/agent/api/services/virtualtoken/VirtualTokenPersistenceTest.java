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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.mersel.dss.agent.api.testsupport.PfxTestKey;

/**
 * {@link VirtualTokenStore} + {@link VirtualTokenRegistry} kalıcılık round-trip'i: bir registry'de
 * tanımlanan sanal kartın, aynı dizine bağlı yeni bir registry'de (agent yeniden başlatması
 * simülasyonu) geri yüklendiğini doğrular.
 */
class VirtualTokenPersistenceTest {

  @Test
  void pkcs11SurvivesRestart(@TempDir Path dir) throws Exception {
    // libraryPath sadece "okunabilir dosya" kontrolünden geçmeli; sahte bir dosya yeterli.
    Path fakeLib = dir.resolve("libfake.so");
    Files.write(fakeLib, new byte[] {0x7f, 'E', 'L', 'F'});

    VirtualTokenStore store1 = new VirtualTokenStore(dir.resolve("store"));
    assumeTrue(store1.isEnabled(), "Depo hazırlanamadı");
    VirtualTokenRegistry reg1 = new VirtualTokenRegistry(store1);
    reg1.registerPkcs11("HSM Kart", fakeLib.toString());

    // Yeniden başlatma: aynı dizine bağlı taze registry.
    VirtualTokenStore store2 = new VirtualTokenStore(dir.resolve("store"));
    VirtualTokenRegistry reg2 = new VirtualTokenRegistry(store2);
    reg2.restoreFromDisk();

    VirtualToken restored = reg2.find("HSM Kart");
    assertThat(restored).isNotNull();
    assertThat(restored.isPkcs11()).isTrue();
    assertThat(restored.getPkcs11LibraryPath()).isEqualTo(fakeLib.toString());
  }

  @Test
  void removeDeletesFromDisk(@TempDir Path dir) throws Exception {
    Path fakeLib = dir.resolve("libfake.so");
    Files.write(fakeLib, new byte[] {0x7f, 'E', 'L', 'F'});

    VirtualTokenStore store1 = new VirtualTokenStore(dir.resolve("store"));
    assumeTrue(store1.isEnabled(), "Depo hazırlanamadı");
    VirtualTokenRegistry reg1 = new VirtualTokenRegistry(store1);
    reg1.registerPkcs11("Geçici", fakeLib.toString());
    reg1.remove("Geçici");

    VirtualTokenStore store2 = new VirtualTokenStore(dir.resolve("store"));
    VirtualTokenRegistry reg2 = new VirtualTokenRegistry(store2);
    reg2.restoreFromDisk();

    assertThat(reg2.find("Geçici")).isNull();
  }

  @Test
  void pkcs12SurvivesRestartWithEncryptedPassword(@TempDir Path dir) throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());

    byte[] pfx = Files.readAllBytes(key.getFile().toPath());

    VirtualTokenStore store1 = new VirtualTokenStore(dir.resolve("store"));
    assumeTrue(store1.isEnabled(), "Depo hazırlanamadı");
    VirtualTokenRegistry reg1 = new VirtualTokenRegistry(store1);
    reg1.registerPkcs12("PFX Kart", pfx, key.getPassword(), key.getFileName());

    // Yeniden başlatma simülasyonu.
    VirtualTokenStore store2 = new VirtualTokenStore(dir.resolve("store"));
    VirtualTokenRegistry reg2 = new VirtualTokenRegistry(store2);
    reg2.restoreFromDisk();

    VirtualToken restored = reg2.find("PFX Kart");
    assertThat(restored).isNotNull();
    assertThat(restored.isPkcs12()).isTrue();
    // Parola şifreli yazılıp doğru çözülmeli; keystore bu parolayla açılabilmeli.
    assertThat(restored.passwordString()).isEqualTo(new String(key.getPassword()));
    assertThat(restored.getKeyStore()).isNotNull();
    assertThat(restored.getKeyStore().aliases().hasMoreElements()).isTrue();

    // Parola şifreli yazılmalı: passwordEnc alanı düz metin parolaya EŞİT olmamalı.
    // (Not: test PFX'inin DOSYA ADI parolayı içerdiği için ham JSON contains kontrolü yanıltıcı
    // olurdu; bu yüzden doğrudan şifreli alanı kontrol ediyoruz.)
    String indexJson =
        new String(
            Files.readAllBytes(dir.resolve("store").resolve("virtual-cards.json")),
            java.nio.charset.StandardCharsets.UTF_8);
    String plain = new String(key.getPassword());
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("\"passwordEnc\"\\s*:\\s*\"([^\"]*)\"").matcher(indexJson);
    assertThat(m.find()).as("passwordEnc alanı bulunmalı").isTrue();
    assertThat(m.group(1)).as("parola şifreli saklanmalı").isNotEqualTo(plain);
  }
}
