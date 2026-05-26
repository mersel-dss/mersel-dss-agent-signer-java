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

import io.swagger.v3.oas.annotations.media.Schema;

/** Bir okuyucudaki kart hakkında özet bilgi. */
@Schema(description = "Okuyucuya takılı kart bilgisi")
public class SmartCardDetail {

  @Schema(description = "Okuyucu (terminal) adı.")
  private String terminalName;

  @Schema(description = "Kartın ATR'i (HEX).")
  private String atr;

  @Schema(description = "Tanınan kart tipi (AKIS, e-İmzaTR, vs.). Bilinmiyorsa null.")
  private String cardType;

  @Schema(description = "PKCS#11 kütüphane bare adı (örn. \"akisp11\").")
  private String pkcs11Library;

  @Schema(description = "PKCS#11 kütüphanesinin diske çözümlenmiş yolu.")
  private String pkcs11LibraryPath;

  public SmartCardDetail() {}

  public String getTerminalName() {
    return terminalName;
  }

  public void setTerminalName(String terminalName) {
    this.terminalName = terminalName;
  }

  public String getAtr() {
    return atr;
  }

  public void setAtr(String atr) {
    this.atr = atr;
  }

  public String getCardType() {
    return cardType;
  }

  public void setCardType(String cardType) {
    this.cardType = cardType;
  }

  public String getPkcs11Library() {
    return pkcs11Library;
  }

  public void setPkcs11Library(String pkcs11Library) {
    this.pkcs11Library = pkcs11Library;
  }

  public String getPkcs11LibraryPath() {
    return pkcs11LibraryPath;
  }

  public void setPkcs11LibraryPath(String pkcs11LibraryPath) {
    this.pkcs11LibraryPath = pkcs11LibraryPath;
  }
}
