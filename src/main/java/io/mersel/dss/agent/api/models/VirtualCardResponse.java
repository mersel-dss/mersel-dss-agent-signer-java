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

import com.fasterxml.jackson.annotation.JsonInclude;

import io.mersel.dss.agent.api.services.virtualtoken.VirtualToken;
import io.swagger.v3.oas.annotations.media.Schema;

/** Tanımlı bir sanal kart ("Dummy Card") özeti. Parola asla yansıtılmaz. */
@Schema(description = "Tanımlı sanal kart (PKCS#11 / PKCS#12) özeti")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VirtualCardResponse {

  @Schema(
      description = "Sanal kartın adı; terminalName olarak kullanılır.",
      example = "PFX - firma.pfx")
  private String name;

  @Schema(description = "Kaynak tipi: PKCS11 | PKCS12.")
  private String type;

  @Schema(description = "Gösterilecek kart tipi etiketi.", example = "PKCS#12 (PFX)")
  private String cardType;

  @Schema(description = "Kaynak açıklaması (PFX dosya adı veya PKCS#11 lib yolu).")
  private String source;

  public VirtualCardResponse() {}

  public VirtualCardResponse(String name, String type, String cardType, String source) {
    this.name = name;
    this.type = type;
    this.cardType = cardType;
    this.source = source;
  }

  public static VirtualCardResponse from(VirtualToken token) {
    return new VirtualCardResponse(
        token.getName(), token.getSourceType(), token.getDisplayCardType(), token.getSource());
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getCardType() {
    return cardType;
  }

  public void setCardType(String cardType) {
    this.cardType = cardType;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
