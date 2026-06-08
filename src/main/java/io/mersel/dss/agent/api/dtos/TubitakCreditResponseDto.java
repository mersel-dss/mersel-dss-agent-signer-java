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

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/** TÜBİTAK zaman damgası kontör sorgulama yanıtı. */
@Schema(description = "TÜBİTAK kontör sorgulama yanıtı")
public class TubitakCreditResponseDto {

  @Schema(description = "Kalan kontör miktarı", example = "1500")
  @JsonProperty("remainingCredit")
  private Long remainingCredit;

  @Schema(description = "Müşteri numarası", example = "12345")
  @JsonProperty("customerId")
  private Integer customerId;

  @Schema(description = "TSA'dan dönen ham yanıt / mesaj")
  @JsonProperty("message")
  private String message;

  public TubitakCreditResponseDto() {}

  public TubitakCreditResponseDto(Long remainingCredit, Integer customerId, String message) {
    this.remainingCredit = remainingCredit;
    this.customerId = customerId;
    this.message = message;
  }

  public Long getRemainingCredit() {
    return remainingCredit;
  }

  public void setRemainingCredit(Long remainingCredit) {
    this.remainingCredit = remainingCredit;
  }

  public Integer getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Integer customerId) {
    this.customerId = customerId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
