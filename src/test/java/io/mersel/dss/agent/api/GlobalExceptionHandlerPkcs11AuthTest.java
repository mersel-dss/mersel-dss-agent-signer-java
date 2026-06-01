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
package io.mersel.dss.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.mersel.dss.agent.api.exceptions.Pkcs11AuthException;
import io.mersel.dss.agent.api.models.ErrorModel;

/**
 * {@link GlobalExceptionHandler#handlePkcs11Auth} yapısal alan + HTTP statü davranışı.
 *
 * <p>Frontend kontratını korumak için: legacy 1-arg ctor → 401 + {@code PKCS11_AUTH_FAILED};
 * yapısal ctor (PIN_INCORRECT) → 401 + structured fields; locked → {@code 423 LOCKED} + {@code
 * pinLocked=true} + {@code pinAttemptsRemainingHint="0"}.
 */
class GlobalExceptionHandlerPkcs11AuthTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void legacyAuthException_yieldsUnauthorized_withDefaultCodeNoStructuredFields() {
    Pkcs11AuthException ex = new Pkcs11AuthException("PIN yanlış (legacy)");
    ResponseEntity<ErrorModel> resp = handler.handlePkcs11Auth(ex);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    ErrorModel body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getCode()).isEqualTo("PKCS11_AUTH_FAILED");
    assertThat(body.getPkcs11Code()).isNull();
    assertThat(body.getPinLocked()).isNull();
    assertThat(body.getPinAttemptsRemainingHint()).isNull();
  }

  @Test
  void pinIncorrect_yieldsUnauthorized_withStructuredFields() {
    Pkcs11AuthException ex =
        new Pkcs11AuthException(
            "PKCS11_PIN_INCORRECT",
            "Girilen PIN yanlış. Dikkat: arka arkaya yanlış girişlerde kart kilitlenir.",
            new RuntimeException("orig"),
            "CKR_PIN_INCORRECT",
            false,
            null);

    ResponseEntity<ErrorModel> resp = handler.handlePkcs11Auth(ex);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    ErrorModel body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getCode()).isEqualTo("PKCS11_PIN_INCORRECT");
    assertThat(body.getPkcs11Code()).isEqualTo("CKR_PIN_INCORRECT");
    assertThat(body.getPinLocked()).isNull(); // false olduğunda alan JSON'a basılmasın
    assertThat(body.getPinAttemptsRemainingHint()).isNull();
    assertThat(body.getMessage()).contains("PIN yanlış");
  }

  @Test
  void pinLocked_yields423Locked_withPinLockedTrueAndZeroAttempts() {
    Pkcs11AuthException ex =
        new Pkcs11AuthException(
            "PKCS11_PIN_LOCKED",
            "Kart, art arda yanlış PIN girişleri nedeniyle kilitlendi.",
            null,
            "CKR_PIN_LOCKED",
            true,
            "0");

    ResponseEntity<ErrorModel> resp = handler.handlePkcs11Auth(ex);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    ErrorModel body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getCode()).isEqualTo("PKCS11_PIN_LOCKED");
    assertThat(body.getPkcs11Code()).isEqualTo("CKR_PIN_LOCKED");
    assertThat(body.getPinLocked()).isTrue();
    assertThat(body.getPinAttemptsRemainingHint()).isEqualTo("0");
  }

  @Test
  void emptyMessage_isReplacedWithUserFriendlyFallback() {
    Pkcs11AuthException ex = new Pkcs11AuthException("");
    ResponseEntity<ErrorModel> resp = handler.handlePkcs11Auth(ex);

    ErrorModel body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getMessage()).isNotEmpty();
    assertThat(body.getMessage()).contains("PIN");
  }
}
