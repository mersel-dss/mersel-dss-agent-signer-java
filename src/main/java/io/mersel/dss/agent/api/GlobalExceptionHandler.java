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

import javax.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import io.mersel.dss.agent.api.exceptions.CertificateLookupException;
import io.mersel.dss.agent.api.exceptions.Pkcs11AuthException;
import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryException;
import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryNotFoundException;
import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.exceptions.SignerException;
import io.mersel.dss.agent.api.exceptions.SmartCardException;
import io.mersel.dss.agent.api.models.ErrorModel;

/**
 * Tüm HTTP cevap yollarını tek bir yerden tutarlı, yapısal JSON ({@link ErrorModel}) şekline sokar.
 * Domain hatalarını {@link SignerException} hiyerarşisi üzerinden HTTP statülerine eşler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /* ---------------- 400 Bad Request / Validation ---------------- */

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorModel> handleBadRequest(IllegalArgumentException ex) {
    LOGGER.warn("400 Bad Request: {}", ex.getMessage());
    return body(HttpStatus.BAD_REQUEST, new ErrorModel("BAD_REQUEST", ex.getMessage()));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
  public ResponseEntity<ErrorModel> handleValidation(Exception ex) {
    LOGGER.warn("400 Validation: {}", ex.getMessage());
    return body(HttpStatus.BAD_REQUEST, new ErrorModel("VALIDATION_FAILED", ex.getMessage()));
  }

  /* ---------------- 401 / 404 / 424 / 503 — domain ---------------- */

  @ExceptionHandler(Pkcs11AuthException.class)
  public ResponseEntity<ErrorModel> handlePkcs11Auth(Pkcs11AuthException ex) {
    LOGGER.warn("401 PIN/Auth: {}", ex.getMessage());
    return body(
        HttpStatus.UNAUTHORIZED,
        new ErrorModel(
            ex.getErrorCode(),
            "PIN doğrulanamadı veya yanlış. Karta erişim engellenebilir, dikkat."));
  }

  @ExceptionHandler(CertificateLookupException.class)
  public ResponseEntity<ErrorModel> handleCertLookup(CertificateLookupException ex) {
    LOGGER.warn("404 Cert not found: {}", ex.getMessage());
    return body(HttpStatus.NOT_FOUND, new ErrorModel(ex.getErrorCode(), ex.getMessage()));
  }

  @ExceptionHandler(SmartCardException.class)
  public ResponseEntity<ErrorModel> handleSmartCard(SmartCardException ex) {
    LOGGER.warn("424 SmartCard: {}", ex.getMessage());
    return body(HttpStatus.FAILED_DEPENDENCY, new ErrorModel(ex.getErrorCode(), ex.getMessage()));
  }

  @ExceptionHandler(Pkcs11LibraryException.class)
  public ResponseEntity<ErrorModel> handlePkcs11Lib(Pkcs11LibraryException ex) {
    LOGGER.error("503 PKCS11 lib unavailable", ex);
    return body(HttpStatus.SERVICE_UNAVAILABLE, new ErrorModel(ex.getErrorCode(), ex.getMessage()));
  }

  /**
   * Algılanan kart için PKCS#11 sürücüsü bulunamadığında zenginleştirilmiş yanıt: hata kodu + mesaj
   * + (varsa) {@code cardType}, {@code requiredLibrary}, {@code searchedPaths}, {@code
   * downloadHint}.
   */
  @ExceptionHandler(Pkcs11LibraryNotFoundException.class)
  public ResponseEntity<ErrorModel> handlePkcs11LibNotFound(Pkcs11LibraryNotFoundException ex) {
    LOGGER.warn(
        "503 PKCS11 lib not found (cardType={}, lib={}): {}",
        ex.getCardType(),
        ex.getRequiredLibrary(),
        ex.getMessage());
    ErrorModel model = new ErrorModel(ex.getErrorCode(), ex.getMessage());
    model.setCardType(ex.getCardType());
    model.setRequiredLibrary(ex.getRequiredLibrary());
    if (ex.getSearchedPaths() != null && !ex.getSearchedPaths().isEmpty()) {
      model.setSearchedPaths(ex.getSearchedPaths());
    }
    model.setDownloadHint(ex.getDownloadHint());
    if (ex.getCardTypeCandidates() != null && !ex.getCardTypeCandidates().isEmpty()) {
      model.setCardTypeCandidates(ex.getCardTypeCandidates());
    }
    if (ex.isUserSelectionRequired()) {
      model.setUserSelectionRequired(Boolean.TRUE);
    }
    return body(HttpStatus.SERVICE_UNAVAILABLE, model);
  }

  /* ---------------- 413 Payload Too Large ---------------- */

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorModel> handleMaxUpload(MaxUploadSizeExceededException ex) {
    LOGGER.warn("413 File too large: {}", ex.getMessage());
    return body(
        HttpStatus.PAYLOAD_TOO_LARGE,
        new ErrorModel("FILE_TOO_LARGE", "Yüklenen dosya maksimum izin verilen boyutu aşıyor."));
  }

  /* ---------------- 415 Unsupported Media Type ---------------- */

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorModel> handleMediaType(HttpMediaTypeNotSupportedException ex) {
    LOGGER.warn("415 Unsupported media type: {}", ex.getContentType());
    return body(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        new ErrorModel(
            "WRONG_CONTENT_TYPE", "İstek 'multipart/form-data' Content-Type'ı ile gönderilmeli."));
  }

  /* ---------------- 500 ---------------- */

  @ExceptionHandler(SignatureOperationException.class)
  public ResponseEntity<ErrorModel> handleSignatureOp(SignatureOperationException ex) {
    LOGGER.error("500 SignatureOperation", ex);
    return body(
        HttpStatus.INTERNAL_SERVER_ERROR, new ErrorModel(ex.getErrorCode(), ex.getMessage()));
  }

  @ExceptionHandler(SignerException.class)
  public ResponseEntity<ErrorModel> handleSigner(SignerException ex) {
    LOGGER.error("500 SignerException", ex);
    return body(
        HttpStatus.INTERNAL_SERVER_ERROR, new ErrorModel(ex.getErrorCode(), ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorModel> handleConflict(IllegalStateException ex) {
    LOGGER.warn("409 Conflict: {}", ex.getMessage());
    return body(HttpStatus.CONFLICT, new ErrorModel("ILLEGAL_STATE", ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorModel> handleGeneric(Exception ex) {
    LOGGER.error("500 Internal Server Error", ex);
    String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return body(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorModel("INTERNAL_ERROR", msg));
  }

  private static ResponseEntity<ErrorModel> body(HttpStatus status, ErrorModel model) {
    return ResponseEntity.status(status).body(model);
  }
}
