package io.mersel.dss.agent.api.services.keystore;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyStoreException;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.exceptions.SignerException;

/**
 * {@link IaikPkcs11Signer#requiresIaikFallback(Throwable)} pattern eşleştiricisinin sahada görülen
 * tüm CKA_ID collision + CKR_USER_NOT_LOGGED_IN varyasyonlarını doğru tanıdığını ve unrelated
 * hatalarda false-positive üretmediğini doğrular. Fallback dispatch bu metodun doğruluğuna güvenir;
 * regresyon olursa native path tetiklenmez veya yanlış yerde tetiklenir.
 */
class IaikPkcs11SignerErrorMatchTest {

  @Test
  @DisplayName("OpenJDK 1.8 P11KeyStore.mapPrivateKeys mesajı: 'invalid KeyStore state'+CKA_ID")
  void detectsClassicSunPkcs11Message() {
    KeyStoreException kse =
        new KeyStoreException(
            "invalid KeyStore state: found 2 private keys sharing CKA_ID"
                + " 0x5dba00cc74e842ccc17d4154fdf1062824d73419");

    assertThat(IaikPkcs11Signer.requiresIaikFallback(kse)).isTrue();
  }

  @Test
  @DisplayName("Cause zinciri içinde CKA_ID: KeyStore.load() → IOException → KeyStoreException")
  void detectsAcrossCauseChain() {
    KeyStoreException root =
        new KeyStoreException("invalid KeyStore state: found 2 private keys sharing CKA_ID 0xABC");
    java.io.IOException io = new java.io.IOException("KeyStore.load failed", root);
    SignerException wrapper = new SignerException("KEYSTORE_LOAD_FAILED", "Smart card load", io);

    assertThat(IaikPkcs11Signer.requiresIaikFallback(wrapper)).isTrue();
  }

  @Test
  @DisplayName("Alternatif mesaj formu: 'private keys sharing CKA_ID' (büyük/küçük harf farkları)")
  void detectsAlternativeWording() {
    RuntimeException e =
        new RuntimeException("PKCS#11 anomaly: 3 PRIVATE KEYS SHARING cka_id 0xDEAD on slot 0");

    assertThat(IaikPkcs11Signer.requiresIaikFallback(e)).isTrue();
  }

  @Test
  @DisplayName("PIN incorrect → false (login hatasını CKA_ID ile karıştırmamalı)")
  void doesNotMatchPinIncorrect() {
    KeyStoreException kse = new KeyStoreException("Cannot login: CKR_PIN_INCORRECT (0x000000A0)");

    assertThat(IaikPkcs11Signer.requiresIaikFallback(kse)).isFalse();
  }

  @Test
  @DisplayName("EC named curves hatası → false")
  void doesNotMatchEcParametersError() {
    java.io.IOException ioe = new java.io.IOException("Only named ECParameters supported");

    assertThat(IaikPkcs11Signer.requiresIaikFallback(ioe)).isFalse();
  }

  @Test
  @DisplayName("null → false (NPE atmamalı)")
  void nullInputReturnsFalse() {
    assertThat(IaikPkcs11Signer.requiresIaikFallback(null)).isFalse();
  }

  @Test
  @DisplayName("Cause-loop (kendi kendine cause olan) → sonsuz döngüye girmemeli")
  void handlesCircularCauseChainGracefully() {
    RuntimeException self = new RuntimeException("loop");
    try {
      java.lang.reflect.Field causeField = Throwable.class.getDeclaredField("cause");
      causeField.setAccessible(true);
      causeField.set(self, self);
    } catch (ReflectiveOperationException e) {
      // JVM Throwable.cause set edemezse test atlanır — eski JDK'larda self-cause izin verilmiyor.
      Assumptions.assumeFalse(true, "Throwable.cause self-reference not supported on this JVM");
    }

    assertThat(IaikPkcs11Signer.requiresIaikFallback(self)).isFalse();
  }

  @Test
  @DisplayName("CKR_USER_NOT_LOGGED_IN — AKİS session-scoped login bug'ı IAIK fallback tetiklemeli")
  void detectsUserNotLoggedIn() {
    // Sahada gözlenen tipik cause chain:
    //   SignatureOperationException → ProviderException(Initialization failed)
    //   → PKCS11Exception(CKR_USER_NOT_LOGGED_IN)
    sun.security.pkcs11.wrapper.PKCS11Exception p11e =
        new sun.security.pkcs11.wrapper.PKCS11Exception(0x00000101L);
    // P11Exception toString'i "CKR_USER_NOT_LOGGED_IN" mesajını üretir; ham mesajı kullanarak
    // SunPKCS11'in gerçek çıktısına en yakın simülasyonu yapalım.
    java.security.ProviderException pex =
        new java.security.ProviderException("Initialization failed", p11e);
    SignerException wrapper =
        new SignerException("SIGNATURE_FAILED", "XAdES-BES imzalama başarısız", pex);

    assertThat(IaikPkcs11Signer.requiresIaikFallback(wrapper)).isTrue();
  }

  @Test
  @DisplayName("CKR_USER_NOT_LOGGED_IN düz mesaj (büyük/küçük harf farkları)")
  void detectsUserNotLoggedInPlainMessage() {
    RuntimeException e =
        new RuntimeException(
            "SunPKCS11 wrapper PKCS11Exception: CKR_USER_NOT_LOGGED_IN at C_SignInit");

    assertThat(IaikPkcs11Signer.requiresIaikFallback(e)).isTrue();
  }
}
