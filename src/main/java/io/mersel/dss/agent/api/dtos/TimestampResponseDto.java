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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Zaman damgası alma yanıtı. Binary {@code .tst} token döndürülürken metadata HTTP header'larına da
 * yazılır.
 */
@Schema(description = "Zaman damgası alma yanıtı")
public class TimestampResponseDto {

  @Schema(description = "Zaman damgası token'ı (RFC 3161 TST) — Base64 kodlu")
  private String timestampToken;

  @Schema(description = "Zaman damgası zamanı (ISO 8601)", example = "2026-06-08T14:30:00Z")
  private String timestamp;

  @Schema(
      description = "TSA (Time Stamp Authority) bilgisi",
      example = "CN=TÜBİTAK ESYA TSS, O=TÜBİTAK, C=TR")
  private String tsaName;

  @Schema(description = "Kullanılan hash algoritması", example = "SHA-256")
  private String hashAlgorithm;

  @Schema(description = "Seri numarası", example = "123456789")
  private String serialNumber;

  @Schema(description = "Nonce değeri (varsa)", example = "1234567890123456")
  private String nonce;

  public String getTimestampToken() {
    return timestampToken;
  }

  public void setTimestampToken(String timestampToken) {
    this.timestampToken = timestampToken;
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
}
