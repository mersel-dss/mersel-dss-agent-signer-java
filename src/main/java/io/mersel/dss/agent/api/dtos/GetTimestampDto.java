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

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Zaman damgası alma isteği (multipart {@code @ModelAttribute}).
 *
 * <p>Sunucu projesinden temel fark: TSA sağlayıcı adresi ve kimlik bilgileri ({@code tsaUrl},
 * {@code tsUserId}, {@code tsUserPassword}, {@code tubitak}) ortam değişkeninden değil <b>her
 * istekte parametre olarak</b> alınır. Bu değerler masaüstü uygulamasında kayıtlı sağlayıcıdan
 * doldurulup gönderilir.
 */
@Schema(description = "Zaman damgası alma isteği")
public class GetTimestampDto {

  @Schema(
      description = "Zaman damgası alınacak dosya.",
      type = "string",
      format = "binary",
      required = true)
  private MultipartFile document;

  @Schema(
      description = "Hash algoritması.",
      example = "SHA256",
      allowableValues = {"SHA1", "SHA224", "SHA256", "SHA384", "SHA512"})
  private String hashAlgorithm = "SHA256";

  @Schema(
      description = "Zaman damgası sunucusu (TSA) adresi.",
      example = "http://zd.kamusm.gov.tr",
      required = true)
  private String tsaUrl;

  @Schema(
      description =
          "TSA kullanıcı / müşteri numarası. TÜBİTAK ESYA için zorunlu (sayısal müşteri no);"
              + " standart TSA'larda HTTP Basic kullanıcı adı.")
  private String tsUserId;

  @Schema(
      description = "TSA parolası. TÜBİTAK ESYA ve Basic-Auth'lı standart TSA'larda kullanılır.")
  private String tsUserPassword;

  @Schema(
      description =
          "TÜBİTAK ESYA protokolü zorlansın mı. Verilmezse KamuSM host'larından (zd/tzd.kamusm.gov.tr)"
              + " otomatik tespit edilir.",
      example = "true")
  private Boolean tubitak;

  @Schema(description = "TSA sertifikası yanıta dahil edilsin mi.", example = "true")
  private Boolean certReq = Boolean.TRUE;

  @Schema(
      description =
          "Nonce kullanılsın mı. Sunucu (mersel-dss-server-signer) ile birebir davranış için"
              + " varsayılan false; replay koruması istenirse true gönderin.",
      example = "false")
  private Boolean useNonce = Boolean.FALSE;

  public MultipartFile getDocument() {
    return document;
  }

  public void setDocument(MultipartFile document) {
    this.document = document;
  }

  public String getHashAlgorithm() {
    return hashAlgorithm;
  }

  public void setHashAlgorithm(String hashAlgorithm) {
    this.hashAlgorithm = hashAlgorithm;
  }

  public String getTsaUrl() {
    return tsaUrl;
  }

  public void setTsaUrl(String tsaUrl) {
    this.tsaUrl = tsaUrl;
  }

  public String getTsUserId() {
    return tsUserId;
  }

  public void setTsUserId(String tsUserId) {
    this.tsUserId = tsUserId;
  }

  public String getTsUserPassword() {
    return tsUserPassword;
  }

  public void setTsUserPassword(String tsUserPassword) {
    this.tsUserPassword = tsUserPassword;
  }

  public Boolean getTubitak() {
    return tubitak;
  }

  public void setTubitak(Boolean tubitak) {
    this.tubitak = tubitak;
  }

  public Boolean getCertReq() {
    return certReq;
  }

  public void setCertReq(Boolean certReq) {
    this.certReq = certReq;
  }

  public Boolean getUseNonce() {
    return useNonce;
  }

  public void setUseNonce(Boolean useNonce) {
    this.useNonce = useNonce;
  }
}
