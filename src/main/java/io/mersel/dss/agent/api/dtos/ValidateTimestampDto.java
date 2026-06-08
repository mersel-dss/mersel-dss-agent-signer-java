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

/** Zaman damgası doğrulama isteği (multipart {@code @ModelAttribute}). Bu işlem TSA'ya bağlanmaz. */
@Schema(description = "Zaman damgası doğrulama isteği")
public class ValidateTimestampDto {

  @Schema(
      description = "Doğrulanacak zaman damgası token'ı (.tst / binary).",
      type = "string",
      format = "binary",
      required = true)
  private MultipartFile timestampToken;

  @Schema(
      description = "Orijinal belge — hash doğrulaması için opsiyonel.",
      type = "string",
      format = "binary")
  private MultipartFile originalDocument;

  public MultipartFile getTimestampToken() {
    return timestampToken;
  }

  public void setTimestampToken(MultipartFile timestampToken) {
    this.timestampToken = timestampToken;
  }

  public MultipartFile getOriginalDocument() {
    return originalDocument;
  }

  public void setOriginalDocument(MultipartFile originalDocument) {
    this.originalDocument = originalDocument;
  }
}
