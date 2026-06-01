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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xipki.pkcs11.wrapper.PKCS11Constants;
import org.xipki.pkcs11.wrapper.PKCS11Exception;

/**
 * {@link IaikPkcs11Signer#classifyNativeInitFailure} regresyon testleri — NULL-args fallback
 * yolundaki {@code CKR_CRYPTOKI_ALREADY_INITIALIZED} kontratının leak yapmadığını ve diğer CKR_*
 * hatalarının doğru şekilde xipki exception'a sarıldığını doğrular.
 *
 * <h2>Neden bu test?</h2>
 *
 * <p>Sahada gözlenen senaryo: agent sertifika listeleme akışında SunPKCS11 üzerinden ilk kez AKİS
 * .dylib'i init ediyor; ardından xades4j path patladığında IAIK fallback'e düşüyor; IAIK fallback
 * {@code module.initialize()} → {@code CKR_ARGUMENTS_BAD} (AKIS args validation), NULL-args yoluna
 * düşüyor → {@code C_Initialize(NULL)} → {@code CKR_CRYPTOKI_ALREADY_INITIALIZED} (çünkü SunPKCS11
 * zaten init etmişti).
 *
 * <p>Eski davranışta NULL-args yolu ALREADY_INITIALIZED'i ölümcül hata olarak işliyordu → {@code
 * Pkcs11LibraryException("PKCS#11 modülü initialize edilemedi: ...
 * CKR_CRYPTOKI_ALREADY_INITIALIZED")} → IAIK fallback hiçbir zaman çalışamıyordu. Bu test, yeni
 * davranışın ALREADY_INITIALIZED'i {@link IaikPkcs11Signer.InitOwnership#SHARED} olarak
 * sınıflandırıp paylaşımlı Cryptoki state ile devam ettiğini doğrular.
 */
class IaikPkcs11SignerInitTest {

  @Test
  @DisplayName("CKR_CRYPTOKI_ALREADY_INITIALIZED → SHARED (no leak, paylaşımlı state kullanılır)")
  void alreadyInitializedReturnsSharedOwnership() throws PKCS11Exception {
    iaik.pkcs.pkcs11.wrapper.PKCS11Exception cause =
        new iaik.pkcs.pkcs11.wrapper.PKCS11Exception(
            PKCS11Constants.CKR_CRYPTOKI_ALREADY_INITIALIZED);

    IaikPkcs11Signer.InitOwnership ownership =
        IaikPkcs11Signer.classifyNativeInitFailure(cause, "/usr/local/lib/libakisp11.dylib");

    assertThat(ownership).isEqualTo(IaikPkcs11Signer.InitOwnership.SHARED);
  }

  @Test
  @DisplayName("CKR_GENERAL_ERROR → high-level xipki PKCS11Exception olarak fırlatılır")
  void otherCkrCodesAreRethrownAsXipkiException() {
    iaik.pkcs.pkcs11.wrapper.PKCS11Exception cause =
        new iaik.pkcs.pkcs11.wrapper.PKCS11Exception(PKCS11Constants.CKR_GENERAL_ERROR);

    assertThatThrownBy(
            () ->
                IaikPkcs11Signer.classifyNativeInitFailure(
                    cause, "/usr/local/lib/libakisp11.dylib"))
        .isInstanceOf(PKCS11Exception.class)
        .matches(t -> ((PKCS11Exception) t).getErrorCode() == PKCS11Constants.CKR_GENERAL_ERROR);
  }

  @Test
  @DisplayName("CKR_DEVICE_ERROR → xipki PKCS11Exception (caller library exception'a sarar)")
  void deviceErrorIsRethrownAsXipkiException() {
    iaik.pkcs.pkcs11.wrapper.PKCS11Exception cause =
        new iaik.pkcs.pkcs11.wrapper.PKCS11Exception(PKCS11Constants.CKR_DEVICE_ERROR);

    assertThatThrownBy(() -> IaikPkcs11Signer.classifyNativeInitFailure(cause, "/path/to/lib"))
        .isInstanceOf(PKCS11Exception.class)
        .matches(t -> ((PKCS11Exception) t).getErrorCode() == PKCS11Constants.CKR_DEVICE_ERROR);
  }

  @Test
  @DisplayName(
      "Native cause IAIK PKCS11Exception değilse IllegalStateException (programlama hatası)")
  void nonIaikCauseRaisesIllegalState() {
    RuntimeException unexpected =
        new RuntimeException("ipkcs11wrapper sürüm uyumsuzluğu — bilinmeyen cause");

    assertThatThrownBy(() -> IaikPkcs11Signer.classifyNativeInitFailure(unexpected, "/path/to/lib"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Beklenmedik native C_Initialize hatası");
  }

  @Test
  @DisplayName("CKR_FUNCTION_FAILED → high-level xipki PKCS11Exception (donanım sorunu)")
  void functionFailedIsRethrownAsXipkiException() {
    iaik.pkcs.pkcs11.wrapper.PKCS11Exception cause =
        new iaik.pkcs.pkcs11.wrapper.PKCS11Exception(PKCS11Constants.CKR_FUNCTION_FAILED);

    assertThatThrownBy(() -> IaikPkcs11Signer.classifyNativeInitFailure(cause, "/path/to/lib"))
        .isInstanceOf(PKCS11Exception.class)
        .matches(t -> ((PKCS11Exception) t).getErrorCode() == PKCS11Constants.CKR_FUNCTION_FAILED);
  }
}
