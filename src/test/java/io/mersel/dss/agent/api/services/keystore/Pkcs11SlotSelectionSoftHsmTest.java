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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "Çok sürücülü / çok slotlu makine" slot-seçim fix'inin canlı (SoftHSM) regresyon testi.
 *
 * <p><b>Neden var?</b> {@link IaikPkcs11Signer#findTokenPresentSlotId} + {@link Pkcs11Session#open}
 * birden çok slot içeren (biri boş) bir PKCS#11 kütüphanesinde gerçek kartın slot'unu seçip
 * SunPKCS11 config'ine {@code slot = <id>} yazmalı; aksi halde SunPKCS11 default {@code
 * slotListIndex=0} davranışı boş bir sanal okuyucuya (Aladdin VR / Rainbow iKey Virtual Reader)
 * denk gelince {@code NoSuchAlgorithmException: no such algorithm: PKCS11} / "PKCS11 not found"
 * verir. Bu test SoftHSM ile tam o ortamı (token'lı slot + boş uninitialized slot) kurup fix'in
 * doğru slot'u seçtiğini ve {@code Pkcs11Session.open}'ın login olabildiğini doğrular.
 *
 * <p><b>Donanım gerekmez.</b> Ortam değişkenleri verilmediğinde {@code assumeTrue} ile atlanır
 * (CI'da yeşil kalır). Yerelde çalıştırmak için:
 *
 * <pre>
 * # 1) SoftHSM kur (mimari, JDK ile EŞLEŞMELİ: arm64 JDK ↔ arm64 softhsm)
 * brew install softhsm opensc
 * MOD=$(brew --prefix softhsm)/lib/softhsm/libsofthsm2.so
 *
 * # 2) İzole bir token deposu + token + RSA anahtar çifti
 * export SOFTHSM2_CONF=/tmp/mersel-softhsm/softhsm2.conf
 * mkdir -p /tmp/mersel-softhsm/tokens
 * printf 'directories.tokendir = /tmp/mersel-softhsm/tokens\nobjectstore.backend = file\n' &gt; "$SOFTHSM2_CONF"
 * softhsm2-util --init-token --free --label MerselTestKart --pin 1234 --so-pin 0000
 * pkcs11-tool --module "$MOD" --login --pin 1234 --keypairgen --key-type rsa:2048 --label imzaKey --id 01
 *
 * # 3) Testi çalıştır (SOFTHSM2_CONF env'i de geçir)
 * MERSEL_PKCS11_LIB="$MOD" MERSEL_PKCS11_PIN=1234 SOFTHSM2_CONF="$SOFTHSM2_CONF" \
 *   ./mvnw test -Dtest=Pkcs11SlotSelectionSoftHsmTest
 * </pre>
 *
 * <p>SoftHSM token'ı init edilince ek olarak <b>bir boş (uninitialized) spare slot</b> da sunar;
 * yani bu test zaten "fazladan boş slot var" koşulunu içerir. {@code findTokenPresentSlotId}
 * {@code getSlotList(true)} kullandığı için boş slot'u eler ve gerçek slotID'yi döndürür.
 */
class Pkcs11SlotSelectionSoftHsmTest {

  private static final String LIB_ENV = "MERSEL_PKCS11_LIB";
  private static final String PIN_ENV = "MERSEL_PKCS11_PIN";

  @Test
  @DisplayName("SoftHSM: fix token-present slot'u seçer, boş spare slot'a rağmen open() login olur")
  void slotSelectionPicksTokenPresentSlotAndLoginSucceeds() {
    String libPath = System.getenv(LIB_ENV);
    String pin = System.getenv(PIN_ENV);
    assumeTrue(
        libPath != null && !libPath.trim().isEmpty(),
        "Skip — " + LIB_ENV + " set edilmemiş (SoftHSM kurulumu için sınıf javadoc'una bakın).");
    Path lib = Paths.get(libPath);
    assumeTrue(Files.exists(lib), "Skip — PKCS#11 lib diskte yok: " + lib);

    // (1) Slot tespiti: boş spare slot da listede olsa token-present slot bulunmalı.
    OptionalLong slotId = IaikPkcs11Signer.findTokenPresentSlotId(lib);
    assertThat(slotId)
        .as("token-present slotID bulunmalı (getSlotList(true) boş slot'u eler)")
        .isPresent();

    // (2) Gerçek fixlenmiş yol: open() config'e slot=<id> yazar, doğru okuyucuya kilitlenir ve
    // PIN ile C_Login yapar. Boş slot 0 patolojisi yaşanmadan başarılı olmalı.
    try (Pkcs11Session session = Pkcs11Session.open(lib, pin)) {
      assertThat(session.getProviderName())
          .as("SunPKCS11 provider token-present slot üzerinde ayağa kalkmalı")
          .isNotEmpty();
    }
  }
}
