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

/**
 * Zaman damgası özelliğinin durum bilgisi.
 *
 * <p>Sunucu projesinden farklı olarak agent'ta sağlayıcı kimlik bilgileri ortam değişkeninden değil
 * her istekte parametre olarak alındığından, bu uç yalnızca özelliğin <em>desteklendiğini</em> ve
 * parametre tabanlı kullanım modelini bildirir.
 */
@Schema(description = "Zaman damgası özelliği durum bilgisi")
public class TimestampStatusDto {

  @Schema(description = "Zaman damgası özelliği destekleniyor mu", example = "true")
  @JsonProperty("available")
  private boolean available;

  @Schema(description = "Kimlik bilgilerinin nasıl sağlanacağını belirtir", example = "PARAMETER")
  @JsonProperty("credentialSource")
  private String credentialSource;

  @Schema(description = "Durum mesajı")
  @JsonProperty("message")
  private String message;

  public TimestampStatusDto() {}

  public TimestampStatusDto(boolean available, String credentialSource, String message) {
    this.available = available;
    this.credentialSource = credentialSource;
    this.message = message;
  }

  public boolean isAvailable() {
    return available;
  }

  public void setAvailable(boolean available) {
    this.available = available;
  }

  public String getCredentialSource() {
    return credentialSource;
  }

  public void setCredentialSource(String credentialSource) {
    this.credentialSource = credentialSource;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
