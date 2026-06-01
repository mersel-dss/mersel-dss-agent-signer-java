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
package io.mersel.dss.agent.api.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * İmzalama hatasının "neden bu kartta çalışmıyor" sorusunu yanıtlayan tanılama bağlamı.
 *
 * <p>İki yerde doldurulur:
 *
 * <ul>
 *   <li>{@code XadesService}/{@code PadesService} hata yutarken, mevcut bilgileri ({@code
 *       cardType}, kullanılan algoritma URL'i, token mekanizma listesi, vb.) buraya yazar.
 *   <li>{@code POST /diagnostics/sign-probe} dry-run sonucu olarak (success path).
 * </ul>
 *
 * <p>Tüm alanlar opsiyoneldir; {@code @JsonInclude.NON_NULL} sayesinde dolu olmayan alanlar yanıtta
 * görünmez. Frontend bu alanları "Sorun bildir" panelinde aynen göstermeli; çoğu zaman destek bu
 * blok ile birkaç dakikada cevap verir.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignatureDiagnostics {

  private String terminalName;
  private String atr;
  private String cardType;
  private String pkcs11Library;

  /** Token'a ait tanımlayıcı bilgiler ({@code C_GetTokenInfo}). Hassas değil. */
  private String tokenLabel;

  private String tokenManufacturerId;
  private String tokenModel;
  private String tokenFirmwareVersion;
  private String tokenHardwareVersion;
  private String tokenSerialMasked;

  /** Sertifika public key tipi: {@code RSA}, {@code EC}, ... ; private key kontrol amaçlı. */
  private String keyAlgorithm;

  /** Sertifika anahtar uzunluğu bit cinsinden (RSA modül bit, EC field bit). */
  private Integer keySize;

  /**
   * Token üzerinde bulunan PKCS#11 mekanizmaları ({@code CKM_*}). Sembolik isim varsa onu, yoksa
   * "0x..." hex döner.
   */
  private List<String> tokenMechanisms;

  /**
   * xades4j / JSR-105 katmanına verilmesi planlanan / verilen XAdES Signature algoritma URL'i (ör.
   * {@code http://www.w3.org/2001/04/xmldsig-more#rsa-sha256}).
   */
  private String attemptedSignatureAlgorithm;

  /** İmzalama için seçilen Java JCA Signature algoritma adı (ör. {@code SHA256withRSA}). */
  private String resolvedJcaSignature;

  /** Karta gerçekte gönderilecek PKCS#11 mekanizma sembolik adı (ör. {@code CKM_RSA_PKCS}). */
  private String resolvedPkcs11Mechanism;

  /**
   * Resolver'ın "default profil bu kartta çalışmaz" diyerek devreye soktuğu fallback stratejisi.
   * Örn. {@code "raw-rsa-soft-digest"}; default işliyorsa null.
   */
  private String fallbackStrategy;

  /** Algoritma seçimi sırasında üretilen uyarılar (insan-okur, log dostu). */
  private List<String> warnings;

  /**
   * {@code SIGNATURE_ALGORITHM_UNSUPPORTED} gibi sorunlarda kullanıcıya / destek operatörüne
   * gösterilecek aksiyon listesi. Frontend bu listeyi olduğu gibi render edebilir.
   */
  private List<String> remediation;

  public SignatureDiagnostics() {
    /* Jackson */
  }

  /* getters / setters --------------------------------------------------- */

  public String getTerminalName() {
    return terminalName;
  }

  public void setTerminalName(String terminalName) {
    this.terminalName = terminalName;
  }

  public String getAtr() {
    return atr;
  }

  public void setAtr(String atr) {
    this.atr = atr;
  }

  public String getCardType() {
    return cardType;
  }

  public void setCardType(String cardType) {
    this.cardType = cardType;
  }

  public String getPkcs11Library() {
    return pkcs11Library;
  }

  public void setPkcs11Library(String pkcs11Library) {
    this.pkcs11Library = pkcs11Library;
  }

  public String getTokenLabel() {
    return tokenLabel;
  }

  public void setTokenLabel(String tokenLabel) {
    this.tokenLabel = tokenLabel;
  }

  public String getTokenManufacturerId() {
    return tokenManufacturerId;
  }

  public void setTokenManufacturerId(String tokenManufacturerId) {
    this.tokenManufacturerId = tokenManufacturerId;
  }

  public String getTokenModel() {
    return tokenModel;
  }

  public void setTokenModel(String tokenModel) {
    this.tokenModel = tokenModel;
  }

  public String getTokenFirmwareVersion() {
    return tokenFirmwareVersion;
  }

  public void setTokenFirmwareVersion(String tokenFirmwareVersion) {
    this.tokenFirmwareVersion = tokenFirmwareVersion;
  }

  public String getTokenHardwareVersion() {
    return tokenHardwareVersion;
  }

  public void setTokenHardwareVersion(String tokenHardwareVersion) {
    this.tokenHardwareVersion = tokenHardwareVersion;
  }

  public String getTokenSerialMasked() {
    return tokenSerialMasked;
  }

  public void setTokenSerialMasked(String tokenSerialMasked) {
    this.tokenSerialMasked = tokenSerialMasked;
  }

  public String getKeyAlgorithm() {
    return keyAlgorithm;
  }

  public void setKeyAlgorithm(String keyAlgorithm) {
    this.keyAlgorithm = keyAlgorithm;
  }

  public Integer getKeySize() {
    return keySize;
  }

  public void setKeySize(Integer keySize) {
    this.keySize = keySize;
  }

  public List<String> getTokenMechanisms() {
    return tokenMechanisms;
  }

  public void setTokenMechanisms(List<String> tokenMechanisms) {
    this.tokenMechanisms =
        tokenMechanisms == null
            ? null
            : Collections.unmodifiableList(new ArrayList<String>(tokenMechanisms));
  }

  public String getAttemptedSignatureAlgorithm() {
    return attemptedSignatureAlgorithm;
  }

  public void setAttemptedSignatureAlgorithm(String attemptedSignatureAlgorithm) {
    this.attemptedSignatureAlgorithm = attemptedSignatureAlgorithm;
  }

  public String getResolvedJcaSignature() {
    return resolvedJcaSignature;
  }

  public void setResolvedJcaSignature(String resolvedJcaSignature) {
    this.resolvedJcaSignature = resolvedJcaSignature;
  }

  public String getResolvedPkcs11Mechanism() {
    return resolvedPkcs11Mechanism;
  }

  public void setResolvedPkcs11Mechanism(String resolvedPkcs11Mechanism) {
    this.resolvedPkcs11Mechanism = resolvedPkcs11Mechanism;
  }

  public String getFallbackStrategy() {
    return fallbackStrategy;
  }

  public void setFallbackStrategy(String fallbackStrategy) {
    this.fallbackStrategy = fallbackStrategy;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings =
        warnings == null ? null : Collections.unmodifiableList(new ArrayList<String>(warnings));
  }

  public List<String> getRemediation() {
    return remediation;
  }

  public void setRemediation(List<String> remediation) {
    this.remediation =
        remediation == null
            ? null
            : Collections.unmodifiableList(new ArrayList<String>(remediation));
  }
}
