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

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.config.TraceIdFilter;
import io.mersel.dss.agent.api.exceptions.CauseChainExtractor;
import io.mersel.dss.agent.api.exceptions.CertificateLookupException;
import io.mersel.dss.agent.api.exceptions.Pkcs11AuthException;
import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryException;
import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryNotFoundException;
import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.exceptions.SignerException;
import io.mersel.dss.agent.api.exceptions.SmartCardException;
import io.mersel.dss.agent.api.models.ErrorModel;
import io.mersel.dss.agent.api.models.SignatureDiagnostics;
import io.mersel.dss.agent.api.services.diagnostics.TraceErrorContext;

/**
 * Tüm HTTP cevap yollarını tek bir yerden tutarlı, yapısal JSON ({@link ErrorModel}) şekline sokar.
 * Domain hatalarını {@link SignerException} hiyerarşisi üzerinden HTTP statülerine eşler.
 *
 * <h2>Tanılama (diagnostics) kontratı</h2>
 *
 * Her yanıt {@code traceId} alanını taşır ({@link TraceIdFilter} tarafından MDC'ye yazılır). Hata
 * yanıtları ek olarak {@code causeChain} (cause zinciri düz liste) ve {@code signatureDiagnostics}
 * (sadece imzalama yolu) alanlarını taşıyabilir. {@code
 * mersel.signer.diagnostics.expose-cause-chain=false} verilirse {@code causeChain} JSON'a yazılmaz
 * (mesaj + traceId yine yer alır; log'ta tam zincir bulunabilir).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final boolean exposeCauseChain;

  @org.springframework.beans.factory.annotation.Autowired
  public GlobalExceptionHandler(SignerProperties properties) {
    this.exposeCauseChain = properties != null && properties.getDiagnostics().isExposeCauseChain();
  }

  /** Test fixture: eski davranışla backward-compat için direct flag. */
  public GlobalExceptionHandler(boolean exposeCauseChain) {
    this.exposeCauseChain = exposeCauseChain;
  }

  /* ---------------- 400 Bad Request / Validation ---------------- */

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorModel> handleBadRequest(IllegalArgumentException ex) {
    LOGGER.warn("400 Bad Request: {}", ex.getMessage());
    return body(HttpStatus.BAD_REQUEST, enrich(new ErrorModel("BAD_REQUEST", ex.getMessage()), ex));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
  public ResponseEntity<ErrorModel> handleValidation(Exception ex) {
    LOGGER.warn("400 Validation: {}", ex.getMessage());
    return body(
        HttpStatus.BAD_REQUEST, enrich(new ErrorModel("VALIDATION_FAILED", ex.getMessage()), ex));
  }

  /* ---------------- 401 / 404 / 424 / 503 — domain ---------------- */

  /**
   * PIN / auth hatalarını yapısal olarak yansıtır. PKCS#11 cause zincirinden çıkarılan {@code
   * CKR_xxx} kodu ve "locked" / kalan deneme ipucu varsa {@code ErrorModel}'e işlenir; locked
   * durumda HTTP 423 (RFC 4918 Locked) dönülür ki frontend retry yolunu kapatabilsin.
   */
  @ExceptionHandler(Pkcs11AuthException.class)
  public ResponseEntity<ErrorModel> handlePkcs11Auth(Pkcs11AuthException ex) {
    String pkcs11Code = ex.getPkcs11Code();
    boolean locked = ex.isLocked();
    LOGGER.warn(
        "{} PIN/Auth: code={} pkcs11Code={} locked={} message={}",
        locked ? "423" : "401",
        ex.getErrorCode(),
        pkcs11Code,
        locked,
        ex.getMessage());

    String message =
        ex.getMessage() != null && !ex.getMessage().isEmpty()
            ? ex.getMessage()
            : "PIN doğrulanamadı. Karta erişim engellenebilir, dikkat.";

    ErrorModel model = new ErrorModel(ex.getErrorCode(), message);
    if (pkcs11Code != null) {
      model.setPkcs11Code(pkcs11Code);
    }
    if (locked) {
      model.setPinLocked(Boolean.TRUE);
    }
    if (ex.getAttemptsRemainingHint() != null) {
      model.setPinAttemptsRemainingHint(ex.getAttemptsRemainingHint());
    }
    return body(locked ? HttpStatus.LOCKED : HttpStatus.UNAUTHORIZED, enrich(model, ex));
  }

  @ExceptionHandler(CertificateLookupException.class)
  public ResponseEntity<ErrorModel> handleCertLookup(CertificateLookupException ex) {
    LOGGER.warn("404 Cert not found: {}", ex.getMessage());
    return body(
        HttpStatus.NOT_FOUND, enrich(new ErrorModel(ex.getErrorCode(), ex.getMessage()), ex));
  }

  @ExceptionHandler(SmartCardException.class)
  public ResponseEntity<ErrorModel> handleSmartCard(SmartCardException ex) {
    LOGGER.warn("424 SmartCard: {}", ex.getMessage());
    return body(
        HttpStatus.FAILED_DEPENDENCY,
        enrich(new ErrorModel(ex.getErrorCode(), ex.getMessage()), ex));
  }

  @ExceptionHandler(Pkcs11LibraryException.class)
  public ResponseEntity<ErrorModel> handlePkcs11Lib(Pkcs11LibraryException ex) {
    LOGGER.error("503 PKCS11 lib unavailable", ex);
    return body(
        HttpStatus.SERVICE_UNAVAILABLE,
        enrich(new ErrorModel(ex.getErrorCode(), ex.getMessage()), ex));
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
    return body(HttpStatus.SERVICE_UNAVAILABLE, enrich(model, ex));
  }

  /* ---------------- 413 Payload Too Large ---------------- */

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorModel> handleMaxUpload(MaxUploadSizeExceededException ex) {
    LOGGER.warn("413 File too large: {}", ex.getMessage());
    return body(
        HttpStatus.PAYLOAD_TOO_LARGE,
        enrich(
            new ErrorModel("FILE_TOO_LARGE", "Yüklenen dosya maksimum izin verilen boyutu aşıyor."),
            ex));
  }

  /* ---------------- 415 Unsupported Media Type ---------------- */

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorModel> handleMediaType(HttpMediaTypeNotSupportedException ex) {
    LOGGER.warn("415 Unsupported media type: {}", ex.getContentType());
    return body(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        enrich(
            new ErrorModel(
                "WRONG_CONTENT_TYPE",
                "İstek 'multipart/form-data' Content-Type'ı ile gönderilmeli."),
            ex));
  }

  /* ---------------- 500 ---------------- */

  @ExceptionHandler(SignatureOperationException.class)
  public ResponseEntity<ErrorModel> handleSignatureOp(SignatureOperationException ex) {
    LOGGER.error("500 SignatureOperation code={}", ex.getErrorCode(), ex);
    return body(
        HttpStatus.INTERNAL_SERVER_ERROR,
        enrich(new ErrorModel(ex.getErrorCode(), ex.getMessage()), ex));
  }

  @ExceptionHandler(SignerException.class)
  public ResponseEntity<ErrorModel> handleSigner(SignerException ex) {
    LOGGER.error("500 SignerException", ex);
    return body(
        HttpStatus.INTERNAL_SERVER_ERROR,
        enrich(new ErrorModel(ex.getErrorCode(), ex.getMessage()), ex));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorModel> handleConflict(IllegalStateException ex) {
    LOGGER.warn("409 Conflict: {}", ex.getMessage());
    return body(HttpStatus.CONFLICT, enrich(new ErrorModel("ILLEGAL_STATE", ex.getMessage()), ex));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorModel> handleGeneric(Exception ex) {
    LOGGER.error("500 Internal Server Error", ex);
    String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return body(
        HttpStatus.INTERNAL_SERVER_ERROR, enrich(new ErrorModel("INTERNAL_ERROR", msg), ex));
  }

  /* ---------------- helpers ---------------- */

  /**
   * Tüm error model'leri tek noktadan zenginleştirir: trace ID, cause zinciri ve (varsa) imzalama
   * tanılama bağlamı. {@code expose-cause-chain=false} ise zincir gizlenir.
   */
  ErrorModel enrich(ErrorModel model, Throwable ex) {
    if (model == null) {
      return null;
    }
    String traceId = MDC.get(TraceIdFilter.MDC_KEY);
    if (traceId == null && ex instanceof SignerException) {
      traceId = ((SignerException) ex).getTraceId();
    }
    if (traceId != null && model.getTraceId() == null) {
      model.setTraceId(traceId);
    }
    if (exposeCauseChain && model.getCauseChain() == null) {
      model.setCauseChain(CauseChainExtractor.flatten(ex));
    }
    if (ex instanceof SignerException) {
      SignerException se = (SignerException) ex;
      if (traceId != null && se.getTraceId() == null) {
        se.setTraceId(traceId);
      }
      SignatureDiagnostics diag = se.getDiagnostics();
      if (diag != null && model.getSignatureDiagnostics() == null) {
        model.setSignatureDiagnostics(diag);
      }
    }
    publishTraceErrorContext(model, ex);
    return model;
  }

  /**
   * {@link io.mersel.dss.agent.api.config.TraceRecordingFilter}'ın okuyacağı yapısal hata bağlamını
   * mevcut request'e iliştirir. Çağrıldığı an MDC'de traceId zaten var; filter {@code
   * chain.doFilter} sonrası bu attribute'tan okuyacak. Request context yoksa (örn. unit testlerde
   * direct controller invocation) sessizce atlar.
   */
  private void publishTraceErrorContext(ErrorModel model, Throwable ex) {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs == null) {
        return;
      }
      HttpServletRequest req = attrs.getRequest();
      if (req == null) {
        return;
      }
      String exceptionType = ex == null ? null : ex.getClass().getName();
      // Cause chain her zaman buffer'a yazılır; expose-cause-chain=false olsa bile yerel UI
      // tanılama paneli ve GET /diagnostics/traces ucu üzerinden erişilebilir. Bu localhost-only
      // uçlar; dış HTTP yanıtında sadece yukarıdaki `enrich()` kontrolü cause chain gizler.
      TraceErrorContext ctx =
          new TraceErrorContext(
              model == null ? null : model.getCode(),
              model == null ? null : model.getMessage(),
              exceptionType,
              CauseChainExtractor.flatten(ex),
              model == null ? null : model.getSignatureDiagnostics());
      TraceErrorContext.attach(req, ctx);
    } catch (RuntimeException re) {
      LOGGER.debug("TraceErrorContext attach hatası: {}", re.toString());
    }
  }

  private static ResponseEntity<ErrorModel> body(HttpStatus status, ErrorModel model) {
    return ResponseEntity.status(status).body(model);
  }
}
