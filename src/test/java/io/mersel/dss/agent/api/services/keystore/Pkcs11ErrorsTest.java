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

/**
 * {@link Pkcs11Errors} cause zinciri parsing + CKR mapping davranışı.
 *
 * <p>Gerçek {@code sun.security.pkcs11.wrapper.PKCS11Exception} (JDK 1.8 internal) reflection ile
 * inşa edilir; test böylece kullanıcının ekran görüntüsündeki çağrı yığınını birebir taklit eder
 * ({@code IOException → UnrecoverableKeyException → FailedLoginException → PKCS11Exception}). Eğer
 * JDK'ta sınıf bulunamazsa test {@code Assumptions.assumeTrue} ile atlanır.
 */
class Pkcs11ErrorsTest {

  private static final String PKCS11_EXCEPTION_FQN = "sun.security.pkcs11.wrapper.PKCS11Exception";

  /** PKCS#11 v2.40 §A "Return Values" — değerler hex olarak spec'te sabittir. */
  private static final long CKR_PIN_INCORRECT = 0xA0L;

  private static final long CKR_PIN_INVALID = 0xA1L;
  private static final long CKR_PIN_LEN_RANGE = 0xA2L;
  private static final long CKR_PIN_EXPIRED = 0xA3L;
  private static final long CKR_PIN_LOCKED = 0xA4L;
  private static final long CKR_USER_ALREADY_LOGGED_IN = 0x100L;
  private static final long CKR_TOKEN_NOT_PRESENT = 0xE0L;
  private static final long CKR_DEVICE_ERROR = 0x30L;

  @Test
  void extractCkrCode_findsCodeInDeepCauseChain() throws Exception {
    Throwable inner = newPkcs11Exception(CKR_PIN_INCORRECT);
    assumeTrue(inner != null, "JDK'ta PKCS11Exception yok, test atlandı");

    Throwable failed = new FailedLoginException();
    failed.initCause(inner);
    Throwable unrecoverable = new UnrecoverableKeyException();
    unrecoverable.initCause(failed);
    IOException top = new IOException("load failed", unrecoverable);

    String code = Pkcs11Errors.extractCkrCode(top);
    assertThat(code).isEqualTo("CKR_PIN_INCORRECT");
  }

  @Test
  void extractCkrCode_returnsNullWhenChainHasNoPkcs11Exception() {
    IOException io = new IOException("disk full", new RuntimeException("network"));
    assertThat(Pkcs11Errors.extractCkrCode(io)).isNull();
  }

  @Test
  void extractCkrCode_returnsNullForNullInput() {
    assertThat(Pkcs11Errors.extractCkrCode(null)).isNull();
  }

  @Test
  void extractCkrCode_handlesCircularCauseChainGracefully() {
    // Patolojik durum — initCause döngüsel kuruluma izin vermez ama eski Throwable kodları (bazı
    // legacy lib'ler) reflection üzerinden cause loop kurabilir; helper'ın bu durumda sonsuz
    // döngüye girmemesi gerekir.
    RuntimeException a = new RuntimeException("a");
    RuntimeException b = new RuntimeException("b");
    setCauseReflectively(a, b);
    setCauseReflectively(b, a);

    // Helper, max derinlikte sessizce null döner — exception fırlatmaz.
    assertThat(Pkcs11Errors.extractCkrCode(a)).isNull();
  }

  @Test
  void classify_pinIncorrect_mapsToStructuredAuthOutcome() throws Exception {
    Throwable wrapped = wrapTypicalChain(CKR_PIN_INCORRECT);
    assumeTrue(wrapped != null, "JDK'ta PKCS11Exception yok, test atlandı");

    Pkcs11Errors.Outcome out = Pkcs11Errors.classify(wrapped);
    assertThat(out.getKind()).isEqualTo(Pkcs11Errors.Kind.PIN_INCORRECT);
    assertThat(out.getErrorCode()).isEqualTo("PKCS11_PIN_INCORRECT");
    assertThat(out.getPkcs11Code()).isEqualTo("CKR_PIN_INCORRECT");
    assertThat(out.isLocked()).isFalse();
    assertThat(out.getAttemptsRemainingHint()).isNull();
    assertThat(out.getMessage()).contains("PIN yanlış");
  }

  @Test
  void classify_pinLocked_setsLockedTrueAndZeroAttempts() throws Exception {
    Throwable wrapped = wrapTypicalChain(CKR_PIN_LOCKED);
    assumeTrue(wrapped != null, "JDK'ta PKCS11Exception yok, test atlandı");

    Pkcs11Errors.Outcome out = Pkcs11Errors.classify(wrapped);
    assertThat(out.getKind()).isEqualTo(Pkcs11Errors.Kind.PIN_LOCKED);
    assertThat(out.getErrorCode()).isEqualTo("PKCS11_PIN_LOCKED");
    assertThat(out.getPkcs11Code()).isEqualTo("CKR_PIN_LOCKED");
    assertThat(out.isLocked()).isTrue();
    assertThat(out.getAttemptsRemainingHint()).isEqualTo("0");
    assertThat(out.getMessage()).containsIgnoringCase("kilitlendi");
  }

  @Test
  void classify_pinExpired_isAuthOutcomeButNotLocked() throws Exception {
    Throwable wrapped = wrapTypicalChain(CKR_PIN_EXPIRED);
    assumeTrue(wrapped != null, "JDK'ta PKCS11Exception yok, test atlandı");

    Pkcs11Errors.Outcome out = Pkcs11Errors.classify(wrapped);
    assertThat(out.getKind()).isEqualTo(Pkcs11Errors.Kind.PIN_EXPIRED);
    assertThat(out.getErrorCode()).isEqualTo("PKCS11_PIN_EXPIRED");
    assertThat(out.isLocked()).isFalse();
  }

  @Test
  void classify_pinInvalidFormat_groupsBothCkrCodes() throws Exception {
    Throwable invalid = wrapTypicalChain(CKR_PIN_INVALID);
    Throwable lenRange = wrapTypicalChain(CKR_PIN_LEN_RANGE);
    assumeTrue(invalid != null, "JDK'ta PKCS11Exception yok, test atlandı");

    assertThat(Pkcs11Errors.classify(invalid).getKind())
        .isEqualTo(Pkcs11Errors.Kind.PIN_INVALID_FORMAT);
    assertThat(Pkcs11Errors.classify(lenRange).getKind())
        .isEqualTo(Pkcs11Errors.Kind.PIN_INVALID_FORMAT);
  }

  @Test
  void classify_sessionBusy_mapsToSessionBusy() throws Exception {
    Throwable wrapped = wrapTypicalChain(CKR_USER_ALREADY_LOGGED_IN);
    assumeTrue(wrapped != null, "JDK'ta PKCS11Exception yok, test atlandı");

    assertThat(Pkcs11Errors.classify(wrapped).getKind()).isEqualTo(Pkcs11Errors.Kind.SESSION_BUSY);
  }

  @Test
  void classify_deviceErrors_mapToDeviceRemoved() throws Exception {
    Throwable removed = wrapTypicalChain(CKR_TOKEN_NOT_PRESENT);
    Throwable hwErr = wrapTypicalChain(CKR_DEVICE_ERROR);
    assumeTrue(removed != null, "JDK'ta PKCS11Exception yok, test atlandı");

    assertThat(Pkcs11Errors.classify(removed).getKind())
        .isEqualTo(Pkcs11Errors.Kind.DEVICE_REMOVED);
    assertThat(Pkcs11Errors.classify(hwErr).getKind()).isEqualTo(Pkcs11Errors.Kind.DEVICE_REMOVED);
  }

  @Test
  void classify_unknownPkcs11Code_returnsUnknownButPreservesCkr() throws Exception {
    // Spec dışı CKR_VENDOR_DEFINED gibi nadir durumlar.
    Throwable wrapped = wrapTypicalChain(0x80000050L);
    assumeTrue(wrapped != null, "JDK'ta PKCS11Exception yok, test atlandı");

    Pkcs11Errors.Outcome out = Pkcs11Errors.classify(wrapped);
    assertThat(out.getKind()).isEqualTo(Pkcs11Errors.Kind.UNKNOWN);
    assertThat(out.getErrorCode()).isNull();
    assertThat(out.getPkcs11Code()).isNotNull(); // CKR string yine de yansıtılır
  }

  @Test
  void classify_chainWithoutPkcs11Exception_returnsUnknownNullCode() {
    Pkcs11Errors.Outcome out = Pkcs11Errors.classify(new IOException("disk full"));
    assertThat(out.getKind()).isEqualTo(Pkcs11Errors.Kind.UNKNOWN);
    assertThat(out.getErrorCode()).isNull();
    assertThat(out.getPkcs11Code()).isNull();
  }

  /* ---------------- helpers ---------------- */

  /**
   * Tipik SunPKCS11 sarmalaması: IOException ← UnrecoverableKey ← FailedLogin ← PKCS11Exception.
   */
  private static Throwable wrapTypicalChain(long ckrErrorCode) throws Exception {
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

  /**
   * JDK 1.8 internal {@code sun.security.pkcs11.wrapper.PKCS11Exception(long)} ctor'unu reflection
   * ile çağırır. Sınıf bulunamazsa null döner — test {@code Assumptions.assumeTrue} ile atlanır.
   */
  private static Throwable newPkcs11Exception(long errorCode) throws Exception {
    Class<?> cls;
    try {
      cls = Class.forName(PKCS11_EXCEPTION_FQN);
    } catch (ClassNotFoundException notFound) {
      return null;
    }
    Constructor<?> ctor;
    try {
      ctor = cls.getDeclaredConstructor(long.class);
    } catch (NoSuchMethodException nsme) {
      // JDK içi imza değişmişse: alternatif (long, String) ctor'u dene.
      ctor = cls.getDeclaredConstructor(long.class, String.class);
      ctor.setAccessible(true);
      return (Throwable) ctor.newInstance(errorCode, null);
    }
    ctor.setAccessible(true);
    return (Throwable) ctor.newInstance(errorCode);
  }

  /** Reflection ile cause set eder (initCause idempotent değildir → bypass için). */
  private static void setCauseReflectively(Throwable target, Throwable cause) {
    try {
      java.lang.reflect.Field f = Throwable.class.getDeclaredField("cause");
      f.setAccessible(true);
      f.set(target, cause);
    } catch (Exception e) {
      throw new AssertionError("Cause field reflection başarısız", e);
    }
  }
}
