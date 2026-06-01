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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /smartcard/pin/validate} başarılı yanıtı.
 *
 * <p>{@code valid=true} → kart üzerinde {@code C_Login} başarıyla yapıldı, oturum hemen ardından
 * kapatıldı (sertifika / private key okunmadı). PIN yanlış olduğunda bu uç bu yanıtı
 * <em>döndürmez</em>; standart {@code ErrorModel} ile {@code 401 PKCS11_AUTH_FAILED} döner — yani
 * frontend yalnızca 200 alındığında {@code valid=true} bekler ve farklı bir branch'te 401 yakalar.
 *
 * <p>Yan bilgilerle ({@code terminalName}, {@code cardType}, {@code pkcs11LibraryPath}) frontend
 * cache zenginleştirmesi yapabilir: kullanıcı PIN doğrulamasından sonra aynı {@code cardType}'ı
 * imzalama isteğinde {@code cardType} parametresi olarak gönderirse Layer 5 fallback'e düşmek
 * gerekmez.
 */
@Schema(description = "PIN doğrulama başarılı sonucu")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PinValidationResponse {

  @Schema(description = "C_Login başarılı mı? Bu uçtan dönen yanıtta her zaman `true`.")
  private boolean valid;

  @Schema(description = "Doğrulamanın yapıldığı PCSC okuyucu adı.")
  private String terminalName;

  @Schema(
      description =
          "SmartCardManager 4-katmanlı strateji ile çözdüğü kart tipi (örn. AKIS). Layer 5"
              + " fallback'ten geçmediyse `null` olabilir.")
  private String cardType;

  @Schema(
      description =
          "Çözümlenen PKCS#11 kütüphanesinin tam yolu. Frontend bunu cache'leyip imzalama isteğinde"
              + " `pkcs11LibraryPath` olarak geri gönderebilir.")
  private String pkcs11LibraryPath;

  public PinValidationResponse() {}

  public PinValidationResponse(
      boolean valid, String terminalName, String cardType, String pkcs11LibraryPath) {
    this.valid = valid;
    this.terminalName = terminalName;
    this.cardType = cardType;
    this.pkcs11LibraryPath = pkcs11LibraryPath;
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public String getTerminalName() {
    return terminalName;
  }

  public void setTerminalName(String terminalName) {
    this.terminalName = terminalName;
  }

  public String getCardType() {
    return cardType;
  }

  public void setCardType(String cardType) {
    this.cardType = cardType;
  }

  public String getPkcs11LibraryPath() {
    return pkcs11LibraryPath;
  }

  public void setPkcs11LibraryPath(String pkcs11LibraryPath) {
    this.pkcs11LibraryPath = pkcs11LibraryPath;
  }
}
