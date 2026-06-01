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
package io.mersel.dss.agent.api.services.signature;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.models.SignatureDiagnostics;
import io.mersel.dss.agent.api.services.smartcard.SmartCardInfo;
import io.mersel.dss.agent.api.services.smartcard.SmartCardManager;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;

/**
 * "Bu kart imzalayabilir mi?" sorusunu PIN harcamadan yanıtlayan dry-run.
 *
 * <p>{@link SignatureProfileResolver}'ı sertifika tipi varsayımı (RSA / EC) ile çağırır, mekanizma
 * listesini çıkarır, default profile uygunsa {@code WOULD_SUCCEED}, değilse {@code WOULD_FAIL}
 * döner. Hiç PIN gönderilmediği için kart sayacı harcanmaz; frontend "Sorun bildir" akışında
 * otomatik tetikleyebilir.
 */
@Service
public class SignatureProbeService {

  private static final Logger log = LoggerFactory.getLogger(SignatureProbeService.class);

  private final SmartCardManager cardManager;
  private final SmartCardReaderService readerService;

  public SignatureProbeService(SmartCardManager cardManager, SmartCardReaderService readerService) {
    this.cardManager = cardManager;
    this.readerService = readerService;
  }

  public Result probe(String terminalName, String pkcs11LibraryPath, String cardTypeOverride) {
    Path libraryPath =
        cardManager.resolveLibrary(terminalName, pkcs11LibraryPath, cardTypeOverride);
    log.info(
        "Sign-probe: terminal={}, lib={}, override={}",
        terminalName,
        libraryPath,
        cardTypeOverride);

    SignatureDiagnostics base = new SignatureDiagnostics();
    base.setTerminalName(terminalName);
    base.setPkcs11Library(libraryPath.toString());
    if (terminalName != null) {
      try {
        SmartCardInfo info = readerService.findByTerminalName(terminalName);
        if (info != null) {
          base.setAtr(info.getAtrHex());
          if (info.getCardType() != null) {
            base.setCardType(info.getCardType().getName());
          }
        }
      } catch (RuntimeException re) {
        // Probe best-effort: terminal metadata zenginleştirmesi başarısız olursa probe'a
        // engel olmamalı; ama tanılama için mutlaka log'a düşsün ki destek paketinden görünsün.
        log.warn(
            "Sign-probe: terminal metadata okunamadı (terminal={}): {}",
            terminalName,
            re.toString());
      }
    }

    // Tipik Türkiye senaryosunda RSA-2048 NES; ECDSA QES vakaları için override edilemiyorsa
    // her iki tip için de probe ederiz ve sonucu birlikte sunarız.
    Result result = new Result();
    result.terminalName = terminalName;
    result.atr = base.getAtr();
    result.cardType = base.getCardType();
    result.pkcs11Library = libraryPath.toString();

    SignatureDiagnostics rsaDiag = copyOf(base);
    SignatureProfileResolver.Resolution rsa =
        SignatureProfileResolver.resolve(libraryPath, "RSA", rsaDiag);

    SignatureDiagnostics ecDiag = copyOf(base);
    SignatureProfileResolver.Resolution ec =
        SignatureProfileResolver.resolve(libraryPath, "EC", ecDiag);

    result.rsa = toBranch(rsa, rsaDiag);
    result.ecdsa = toBranch(ec, ecDiag);

    boolean rsaOk = rsa.isSuccess();
    boolean ecOk = ec.isSuccess();
    if (rsaOk || ecOk) {
      result.outcome = "WOULD_SUCCEED";
    } else {
      result.outcome = "WOULD_FAIL";
      result.blockingReason =
          "Token RSA ve ECDSA imzalama mekanizmalarından hiçbirini desteklemiyor.";
      // Hangi remediation listesi varsa onu üst seviyeye taşı.
      List<String> rem =
          rsaDiag.getRemediation() != null ? rsaDiag.getRemediation() : ecDiag.getRemediation();
      result.remediation = rem;
    }
    return result;
  }

  private static Branch toBranch(
      SignatureProfileResolver.Resolution res, SignatureDiagnostics diag) {
    Branch b = new Branch();
    b.tokenSupports = res.isSuccess();
    if (res.isSuccess()) {
      b.outcome = "WOULD_SUCCEED";
    } else {
      b.outcome = "WOULD_FAIL";
      SignatureOperationException err = res.getError();
      if (err != null) {
        b.errorCode = err.getErrorCode();
        b.errorMessage = err.getMessage();
      }
    }
    b.diagnostics = diag;
    return b;
  }

  private static SignatureDiagnostics copyOf(SignatureDiagnostics src) {
    SignatureDiagnostics copy = new SignatureDiagnostics();
    copy.setTerminalName(src.getTerminalName());
    copy.setAtr(src.getAtr());
    copy.setCardType(src.getCardType());
    copy.setPkcs11Library(src.getPkcs11Library());
    return copy;
  }

  /** {@code POST /diagnostics/sign-probe} JSON yanıtı. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Result {
    private String terminalName;
    private String atr;
    private String cardType;
    private String pkcs11Library;

    /** {@code WOULD_SUCCEED} veya {@code WOULD_FAIL} (en az biri başarılıysa SUCCEED). */
    private String outcome;

    /** WOULD_FAIL durumunda kullanıcıya gösterilecek tek-cümlelik özet. */
    private String blockingReason;

    private List<String> remediation;

    private Branch rsa;
    private Branch ecdsa;

    public String getTerminalName() {
      return terminalName;
    }

    public String getAtr() {
      return atr;
    }

    public String getCardType() {
      return cardType;
    }

    public String getPkcs11Library() {
      return pkcs11Library;
    }

    public String getOutcome() {
      return outcome;
    }

    public String getBlockingReason() {
      return blockingReason;
    }

    public List<String> getRemediation() {
      return remediation == null ? null : new ArrayList<String>(remediation);
    }

    public Branch getRsa() {
      return rsa;
    }

    public Branch getEcdsa() {
      return ecdsa;
    }
  }

  /** Tek anahtar tipi için sonuç (RSA / ECDSA). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Branch {
    private String outcome;
    private boolean tokenSupports;
    private String errorCode;
    private String errorMessage;
    private SignatureDiagnostics diagnostics;

    public String getOutcome() {
      return outcome;
    }

    public boolean isTokenSupports() {
      return tokenSupports;
    }

    public String getErrorCode() {
      return errorCode;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public SignatureDiagnostics getDiagnostics() {
      return diagnostics;
    }
  }
}
