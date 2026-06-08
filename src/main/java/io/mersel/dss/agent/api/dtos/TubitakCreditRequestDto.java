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

import javax.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * TÜBİTAK kontör sorgulama isteği. Kimlik bilgileri ortam değişkeni yerine parametre olarak
 * verildiğinden, sorgulama gövdesinde sağlayıcı adresi ve müşteri kimliği taşınır.
 */
@Schema(description = "TÜBİTAK kontör sorgulama isteği")
public class TubitakCreditRequestDto {

  @NotBlank(message = "Zaman damgası sunucu adresi (tsaUrl) zorunludur")
  @Schema(description = "TÜBİTAK ESYA / KamuSM zaman damgası adresi.", example = "http://zd.kamusm.gov.tr", required = true)
  private String tsaUrl;

  @NotBlank(message = "Müşteri numarası (tsUserId) zorunludur")
  @Schema(description = "TÜBİTAK müşteri numarası (sayısal).", example = "12345", required = true)
  private String tsUserId;

  @NotBlank(message = "Parola (tsUserPassword) zorunludur")
  @Schema(description = "TÜBİTAK müşteri parolası.", required = true)
  private String tsUserPassword;

  @Schema(
      description = "TÜBİTAK protokolü zorlansın mı. Verilmezse host'tan otomatik tespit edilir.",
      example = "true")
  private Boolean tubitak;

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
}
