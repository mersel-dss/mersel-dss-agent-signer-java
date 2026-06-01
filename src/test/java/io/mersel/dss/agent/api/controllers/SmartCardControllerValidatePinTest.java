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
package io.mersel.dss.agent.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import io.mersel.dss.agent.api.dtos.ValidatePinDto;
import io.mersel.dss.agent.api.exceptions.Pkcs11AuthException;
import io.mersel.dss.agent.api.models.PinValidationResponse;
import io.mersel.dss.agent.api.services.certificate.CertificateListingService;
import io.mersel.dss.agent.api.services.smartcard.SmartCardPinValidator;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;

/**
 * {@link SmartCardController#validatePin(ValidatePinDto)} controller-level davranışı — {@link
 * SmartCardPinValidator}'ın başarılı sonucunun {@link PinValidationResponse}'a doğru map'lendiğini,
 * {@link Pkcs11AuthException}'ın çağıranın görmesi için yutulmadığını (GlobalExceptionHandler 401'e
 * çevirir) ve DTO alanlarının validator'a aynen iletildiğini doğrular.
 */
class SmartCardControllerValidatePinTest {

  @Test
  void successfulValidationMapsResultIntoResponse() {
    SmartCardPinValidator validator = mock(SmartCardPinValidator.class);
    Path resolvedLib = Paths.get("/usr/local/lib/libakisp11.dylib");
    when(validator.validate("ACR39U", "1234", null, null))
        .thenReturn(new SmartCardPinValidator.ValidationResult(resolvedLib, "AKIS"));

    SmartCardController controller =
        new SmartCardController(
            mock(SmartCardReaderService.class), mock(CertificateListingService.class), validator);

    ValidatePinDto dto = new ValidatePinDto();
    dto.setTerminalName("ACR39U");
    dto.setPin("1234");

    ResponseEntity<PinValidationResponse> resp = controller.validatePin(dto);
    assertThat(resp.getStatusCodeValue()).isEqualTo(200);
    PinValidationResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.isValid()).isTrue();
    assertThat(body.getTerminalName()).isEqualTo("ACR39U");
    assertThat(body.getCardType()).isEqualTo("AKIS");
    assertThat(body.getPkcs11LibraryPath()).isEqualTo(resolvedLib.toString());
  }

  @Test
  void overrideFieldsArePassedThroughToValidator() {
    SmartCardPinValidator validator = mock(SmartCardPinValidator.class);
    Path resolvedLib = Paths.get("/opt/proCertumCardManager/sc30pkcs11.so");
    when(validator.validate(eq("ACR39U"), eq("0000"), eq("/opt/sc30pkcs11.so"), eq("AKIS")))
        .thenReturn(new SmartCardPinValidator.ValidationResult(resolvedLib, "AKIS"));

    SmartCardController controller =
        new SmartCardController(
            mock(SmartCardReaderService.class), mock(CertificateListingService.class), validator);

    ValidatePinDto dto = new ValidatePinDto();
    dto.setTerminalName("ACR39U");
    dto.setPin("0000");
    dto.setPkcs11LibraryPath("/opt/sc30pkcs11.so");
    dto.setCardType("AKIS");

    ResponseEntity<PinValidationResponse> resp = controller.validatePin(dto);
    assertThat(resp.getStatusCodeValue()).isEqualTo(200);
    verify(validator).validate("ACR39U", "0000", "/opt/sc30pkcs11.so", "AKIS");
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().isValid()).isTrue();
    assertThat(resp.getBody().getCardType()).isEqualTo("AKIS");
  }

  @Test
  void wrongPinPropagatesPkcs11AuthExceptionForGlobalHandlerToTranslateTo401() {
    SmartCardPinValidator validator = mock(SmartCardPinValidator.class);
    when(validator.validate(any(), any(), any(), any()))
        .thenThrow(new Pkcs11AuthException("PIN doğrulaması başarısız."));

    SmartCardController controller =
        new SmartCardController(
            mock(SmartCardReaderService.class), mock(CertificateListingService.class), validator);

    ValidatePinDto dto = new ValidatePinDto();
    dto.setTerminalName("ACR39U");
    dto.setPin("9999");

    // Controller hata'yı yutmaz; GlobalExceptionHandler bunu 401 ErrorModel'e çevirir.
    assertThatThrownBy(() -> controller.validatePin(dto))
        .isInstanceOf(Pkcs11AuthException.class)
        .hasMessageContaining("PIN doğrulaması başarısız");
  }

  @Test
  void nullLibraryPathInResultMapsToNullJsonField() {
    SmartCardPinValidator validator = mock(SmartCardPinValidator.class);
    when(validator.validate(any(), any(), any(), any()))
        .thenReturn(new SmartCardPinValidator.ValidationResult(null, null));

    SmartCardController controller =
        new SmartCardController(
            mock(SmartCardReaderService.class), mock(CertificateListingService.class), validator);

    ValidatePinDto dto = new ValidatePinDto();
    dto.setTerminalName("ACR39U");
    dto.setPin("1234");

    ResponseEntity<PinValidationResponse> resp = controller.validatePin(dto);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().isValid()).isTrue();
    assertThat(resp.getBody().getPkcs11LibraryPath()).isNull();
    assertThat(resp.getBody().getCardType()).isNull();
    // Controller işi delege etti, kendisi opaque kalır.
    verify(validator).validate("ACR39U", "1234", null, null);
  }
}
