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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.concurrent.atomic.AtomicInteger;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.exceptions.Pkcs11AuthException;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;

/**
 * {@link SmartCardPinValidator} davranışı.
 *
 * <p>Gerçek PKCS#11 token'a ihtiyaç duymadan {@link Pkcs11Session#wrapForTest} ile in-memory bir
 * sahte session döndürerek başarılı / başarısız doğrulama akışlarını izole ediyoruz. {@code
 * SmartCardManager.resolveLibrary} bir mock ile fake path'e yönlendirilir; {@code openSession}
 * package-private hook'u alt-sınıf override'i ile değiştirilir.
 */
class SmartCardPinValidatorTest {

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  @Test
  void blankTerminalNameRejectedBeforeAnyResolution() {
    SmartCardManager cardManager = mock(SmartCardManager.class);
    SmartCardPinValidator v = new SmartCardPinValidator(cardManager);

    assertThatThrownBy(() -> v.validate("", "1234", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("terminalName");
    assertThatThrownBy(() -> v.validate(null, "1234", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankPinRejectedBeforeAnyResolution() {
    SmartCardManager cardManager = mock(SmartCardManager.class);
    SmartCardPinValidator v = new SmartCardPinValidator(cardManager);

    assertThatThrownBy(() -> v.validate("ACR39U", "", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pin");
    assertThatThrownBy(() -> v.validate("ACR39U", null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void successfulValidationOpensAndClosesSessionAndReturnsResolvedLib() throws Exception {
    SmartCardManager cardManager = mock(SmartCardManager.class);
    Path resolvedLib = Paths.get("/usr/local/lib/libakisp11.dylib");
    when(cardManager.resolveLibrary(eq("ACR39U"), eq("/foo/lib.so"), eq("AKIS")))
        .thenReturn(resolvedLib);

    AtomicInteger openCount = new AtomicInteger(0);
    SmartCardPinValidator v =
        new SmartCardPinValidator(cardManager) {
          @Override
          Pkcs11Session openSession(Path libraryPath, String pin) {
            assertThat(libraryPath).isEqualTo(resolvedLib);
            assertThat(pin).isEqualTo("1234");
            openCount.incrementAndGet();
            return wrapEmpty();
          }
        };

    SmartCardPinValidator.ValidationResult result =
        v.validate("ACR39U", "1234", "/foo/lib.so", "AKIS");

    assertThat(openCount.get()).isEqualTo(1);
    assertThat(result.getPkcs11LibraryPath()).isEqualTo(resolvedLib);
    assertThat(result.getCardType()).isEqualTo("AKIS");
  }

  @Test
  void wrongPinPropagatesPkcs11AuthException() throws Exception {
    SmartCardManager cardManager = mock(SmartCardManager.class);
    when(cardManager.resolveLibrary("ACR39U", null, null))
        .thenReturn(Paths.get("/usr/local/lib/libakisp11.dylib"));

    SmartCardPinValidator v =
        new SmartCardPinValidator(cardManager) {
          @Override
          Pkcs11Session openSession(Path libraryPath, String pin) {
            throw new Pkcs11AuthException("PIN doğrulaması başarısız.");
          }
        };

    assertThatThrownBy(() -> v.validate("ACR39U", "9999", null, null))
        .isInstanceOf(Pkcs11AuthException.class)
        .hasMessageContaining("PIN doğrulaması başarısız");
  }

  /** Boş bir software KeyStore — wrapForTest için "PIN'i kabul etti" sentetik oturumu üretir. */
  private static Pkcs11Session wrapEmpty() {
    try {
      KeyStore ks = KeyStore.getInstance("PKCS12");
      ks.load(null, "1234".toCharArray());
      Provider bc = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
      return Pkcs11Session.wrapForTest(ks, bc, "1234");
    } catch (Exception e) {
      throw new IllegalStateException("Test session oluşturulamadı: " + e.getMessage(), e);
    }
  }
}
