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
 * {@code POST /smartcard/pin/validate} JSON istek modeli.
 *
 * <p>Frontend'in (özellikle giriş ekranı) PIN'i imzalama akışına geçmeden önce ucuz bir{@code
 * C_Login}+{@code C_Logout} ile doğrulamasını sağlar. Pin doğru ise 200 + {@code
 * PinValidationResponse} döner; yanlış ise standart {@code 401 PKCS11_AUTH_FAILED}
 * ({@code ErrorModel}) döner.
 *
 * <p><b>Dikkat:</b> Bu uç gerçek {@code C_Login} yapar — yanlış denemeler kartın PIN sayacını
 * harcar (KamuSM kartlarında tipik 3 deneme; PIN kilitlenirse PUK ile reset gerekir). Frontend
 * ardışık başarısız doğrulamalarda kullanıcıyı uyarmalı, otomatik retry yapmamalıdır.
 */
@Schema(description = "PIN doğrulama (C_Login) isteği")
public class ValidatePinDto {

  @NotBlank
  @Schema(
      description = "PCSC okuyucu (terminal) adı.",
      example = "ACS ACR39U-ND ICC Reader",
      required = true)
  private String terminalName;

  @NotBlank
  @Schema(description = "Akıllı kart PIN'i (4-6 hane).", example = "1234", required = true)
  private String pin;

  @Schema(
      description =
          "İsteğe bağlı PKCS#11 paylaşımlı kütüphane yolu (bare ad veya tam path). Verilmezse"
              + " kart tipi ATR ile algılanıp varsayılan vendor lib aranır.")
  private String pkcs11LibraryPath;

  @Schema(
      description =
          "Layer 5 fallback: ATR algılaması başarısızken kullanıcının manuel seçtiği kart tipi"
              + " (örn. AKIS, ALADDIN).")
  private String cardType;

  public ValidatePinDto() {}

  public String getTerminalName() {
    return terminalName;
  }

  public void setTerminalName(String terminalName) {
    this.terminalName = terminalName;
  }

  public String getPin() {
    return pin;
  }

  public void setPin(String pin) {
    this.pin = pin;
  }

  public String getPkcs11LibraryPath() {
    return pkcs11LibraryPath;
  }

  public void setPkcs11LibraryPath(String pkcs11LibraryPath) {
    this.pkcs11LibraryPath = pkcs11LibraryPath;
  }

  public String getCardType() {
    return cardType;
  }

  public void setCardType(String cardType) {
    this.cardType = cardType;
  }
}
