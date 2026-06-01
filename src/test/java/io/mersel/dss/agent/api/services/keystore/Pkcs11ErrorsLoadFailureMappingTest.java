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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.UnrecoverableKeyException;

import javax.security.auth.login.FailedLoginException;

import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.exceptions.Pkcs11AuthException;
import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryException;

/**
 * {@link Pkcs11Errors#mapKeyStoreLoadFailure(Throwable)} davranışı. {@link Pkcs11ErrorsTest} {@code
 * classify} + {@code extractCkrCode} metotlarını kapsar; bu test üst seviye exception
 * saramalamasını ({@code Pkcs11AuthException} / {@code Pkcs11LibraryException}) doğrular.
 *
 * <pre>
 * IOException("load failed")
 *   └─ UnrecoverableKeyException(null)
 *       └─ FailedLoginException(null)
 *           └─ PKCS11Exception("CKR_PIN_INCORRECT")
 * </pre>
 *
 * <p>Eski davranış: {@code Pkcs11LibraryException(PKCS11_UNAVAILABLE)} → 503 dönüş, frontend
 * "yanlış PIN mi network mü?" ayrımını yapamıyordu. Yeni davranış: {@code Pkcs11AuthException}
 * fırlatılır, {@code errorCode = PKCS11_PIN_INCORRECT}, {@code pkcs11Code = CKR_PIN_INCORRECT}.
 */
class Pkcs11ErrorsLoadFailureMappingTest {

  private static final String PKCS11_EX = "sun.security.pkcs11.wrapper.PKCS11Exception";

  @Test
  void wrongPin_mapsToPkcs11AuthExceptionWithStructuredFields() throws Exception {
    Throwable load = buildLoadFailure(0xA0L); // CKR_PIN_INCORRECT
    assumeTrue(load != null, "JDK'ta PKCS11Exception yok, test atlandı");

    RuntimeException mapped = Pkcs11Errors.mapKeyStoreLoadFailure(load);

    assertThat(mapped).isInstanceOf(Pkcs11AuthException.class);
    Pkcs11AuthException auth = (Pkcs11AuthException) mapped;
    assertThat(auth.getErrorCode()).isEqualTo("PKCS11_PIN_INCORRECT");
    assertThat(auth.getPkcs11Code()).isEqualTo("CKR_PIN_INCORRECT");
    assertThat(auth.isLocked()).isFalse();
    assertThat(auth.getAttemptsRemainingHint()).isNull();
    assertThat(auth.getMessage()).contains("PIN yanlış");
    assertThat(auth.getCause()).isSameAs(load);
  }

  @Test
  void lockedPin_mapsWithLockedTrueAndZeroAttempts() throws Exception {
    Throwable load = buildLoadFailure(0xA4L); // CKR_PIN_LOCKED
    assumeTrue(load != null, "JDK'ta PKCS11Exception yok, test atlandı");

    RuntimeException mapped = Pkcs11Errors.mapKeyStoreLoadFailure(load);

    assertThat(mapped).isInstanceOf(Pkcs11AuthException.class);
    Pkcs11AuthException auth = (Pkcs11AuthException) mapped;
    assertThat(auth.getErrorCode()).isEqualTo("PKCS11_PIN_LOCKED");
    assertThat(auth.getPkcs11Code()).isEqualTo("CKR_PIN_LOCKED");
    assertThat(auth.isLocked()).isTrue();
    assertThat(auth.getAttemptsRemainingHint()).isEqualTo("0");
    assertThat(auth.getMessage()).containsIgnoringCase("kilitlendi");
  }

  @Test
  void deviceRemoved_mapsToLibraryExceptionNotAuth() throws Exception {
    Throwable load = buildLoadFailure(0xE0L); // CKR_TOKEN_NOT_PRESENT
    assumeTrue(load != null, "JDK'ta PKCS11Exception yok, test atlandı");

    RuntimeException mapped = Pkcs11Errors.mapKeyStoreLoadFailure(load);

    assertThat(mapped).isInstanceOf(Pkcs11LibraryException.class);
    assertThat(mapped.getMessage()).contains("CKR_TOKEN_NOT_PRESENT");
  }

  @Test
  void unknownCkr_fallsBackToLibraryException_withCkrInMessage() throws Exception {
    Throwable load = buildLoadFailure(0x80000050L); // CKR_VENDOR_DEFINED bölgesi
    assumeTrue(load != null, "JDK'ta PKCS11Exception yok, test atlandı");

    RuntimeException mapped = Pkcs11Errors.mapKeyStoreLoadFailure(load);

    assertThat(mapped).isInstanceOf(Pkcs11LibraryException.class);
    // Bilinmeyen kodlar bile mesajda CKR sembolüne yer açar — operasyon ekibinin tanı
    // kabiliyetini korur.
    assertThat(mapped.getMessage()).contains("PKCS#11 keystore yüklenemedi");
  }

  @Test
  void chainWithoutPkcs11Exception_fallsBackToLibraryException() {
    IOException load = new IOException("disk error");
    RuntimeException mapped = Pkcs11Errors.mapKeyStoreLoadFailure(load);

    // PKCS#11 ile ilgisi yok — generic library hatası.
    assertThat(mapped).isInstanceOf(Pkcs11LibraryException.class);
    assertThat(mapped.getMessage()).contains("disk error");
  }

  /* ---------------- helpers ---------------- */

  private static Throwable buildLoadFailure(long ckrErrorCode) throws Exception {
    Throwable pkcs11Ex = newPkcs11Exception(ckrErrorCode);
    if (pkcs11Ex == null) {
      return null;
    }
    Throwable failed = new FailedLoginException();
    failed.initCause(pkcs11Ex);
    Throwable unrecoverable = new UnrecoverableKeyException();
    unrecoverable.initCause(failed);
    return new IOException("load failed", unrecoverable);
  }

  private static Throwable newPkcs11Exception(long errorCode) throws Exception {
    Class<?> cls;
    try {
      cls = Class.forName(PKCS11_EX);
    } catch (ClassNotFoundException notFound) {
      return null;
    }
    Constructor<?> ctor;
    try {
      ctor = cls.getDeclaredConstructor(long.class);
    } catch (NoSuchMethodException nsme) {
      ctor = cls.getDeclaredConstructor(long.class, String.class);
      ctor.setAccessible(true);
      return (Throwable) ctor.newInstance(errorCode, null);
    }
    ctor.setAccessible(true);
    return (Throwable) ctor.newInstance(errorCode);
  }
}
