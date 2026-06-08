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
package io.mersel.dss.agent.api.dtos;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Zaman damgası doğrulama yanıtı. */
@Schema(description = "Zaman damgası doğrulama yanıtı")
public class TimestampValidationResponseDto {

  @Schema(description = "Zaman damgası geçerli mi", example = "true")
  private boolean valid;

  @Schema(description = "Zaman damgası zamanı (ISO 8601)", example = "2026-06-08T14:30:00Z")
  private String timestamp;

  @Schema(description = "TSA bilgisi", example = "CN=TÜBİTAK ESYA TSS, O=TÜBİTAK, C=TR")
  private String tsaName;

  @Schema(description = "Hash algoritması (human-readable)", example = "SHA-256")
  private String hashAlgorithm;

  @Schema(description = "Hash algoritması OID", example = "2.16.840.1.101.3.4.2.1")
  private String hashAlgorithmOid;

  @Schema(description = "Seri numarası", example = "123456789")
  private String serialNumber;

  @Schema(description = "Nonce değeri (varsa)", example = "1234567890123456")
  private String nonce;

  @Schema(description = "İmza algoritması (human-readable)", example = "SHA-256 with RSA")
  private String signatureAlgorithm;

  @Schema(description = "İmza algoritması OID", example = "1.2.840.113549.1.1.1")
  private String signatureAlgorithmOid;

  @Schema(description = "TSA sertifikası (Base64 kodlu DER)")
  private String tsaCertificate;

  @Schema(description = "Sertifika geçerli mi", example = "true")
  private boolean certificateValid;

  @Schema(description = "Sertifika başlangıç tarihi", example = "2024-01-01T00:00:00Z")
  private String certificateNotBefore;

  @Schema(description = "Sertifika bitiş tarihi", example = "2026-01-01T00:00:00Z")
  private String certificateNotAfter;

  @Schema(
      description = "Hash doğrulaması başarılı mı (originalDocument sağlanmışsa)",
      example = "true")
  private Boolean hashVerified;

  @Schema(description = "Doğrulama hataları veya uyarılar")
  private List<String> errors;

  @Schema(description = "Doğrulama mesajı", example = "Zaman damgası geçerli ve doğrulandı")
  private String message;

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public String getTsaName() {
    return tsaName;
  }

  public void setTsaName(String tsaName) {
    this.tsaName = tsaName;
  }

  public String getHashAlgorithm() {
    return hashAlgorithm;
  }

  public void setHashAlgorithm(String hashAlgorithm) {
    this.hashAlgorithm = hashAlgorithm;
  }

  public String getHashAlgorithmOid() {
    return hashAlgorithmOid;
  }

  public void setHashAlgorithmOid(String hashAlgorithmOid) {
    this.hashAlgorithmOid = hashAlgorithmOid;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }

  public String getNonce() {
    return nonce;
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }

  public String getSignatureAlgorithm() {
    return signatureAlgorithm;
  }

  public void setSignatureAlgorithm(String signatureAlgorithm) {
    this.signatureAlgorithm = signatureAlgorithm;
  }

  public String getSignatureAlgorithmOid() {
    return signatureAlgorithmOid;
  }

  public void setSignatureAlgorithmOid(String signatureAlgorithmOid) {
    this.signatureAlgorithmOid = signatureAlgorithmOid;
  }

  public String getTsaCertificate() {
    return tsaCertificate;
  }

  public void setTsaCertificate(String tsaCertificate) {
    this.tsaCertificate = tsaCertificate;
  }

  public boolean isCertificateValid() {
    return certificateValid;
  }

  public void setCertificateValid(boolean certificateValid) {
    this.certificateValid = certificateValid;
  }

  public String getCertificateNotBefore() {
    return certificateNotBefore;
  }

  public void setCertificateNotBefore(String certificateNotBefore) {
    this.certificateNotBefore = certificateNotBefore;
  }

  public String getCertificateNotAfter() {
    return certificateNotAfter;
  }

  public void setCertificateNotAfter(String certificateNotAfter) {
    this.certificateNotAfter = certificateNotAfter;
  }

  public Boolean getHashVerified() {
    return hashVerified;
  }

  public void setHashVerified(Boolean hashVerified) {
    this.hashVerified = hashVerified;
  }

  public List<String> getErrors() {
    return errors;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
