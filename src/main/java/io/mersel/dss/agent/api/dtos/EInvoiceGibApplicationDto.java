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
import javax.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** GİB e-Fatura başvuru POST gövdesi. */
@Schema(description = "GİB e-Fatura başvuru isteği")
public class EInvoiceGibApplicationDto {

  @NotBlank
  @Size(min = 3)
  @Schema(description = "Mükellefin telefon numarası.", example = "+905555555555")
  private String phoneNumber;

  @NotBlank
  @Size(min = 3)
  @Schema(description = "Mükellef adresi (tam, açık adres).")
  private String address;

  @NotBlank
  @Size(min = 3)
  @Schema(description = "Mükellef e-posta adresi.", example = "[email protected]")
  private String email;

  @NotBlank
  @Size(min = 3)
  @Schema(description = "Şirketin kanuni merkezinin il/ilçe bilgisi (GİB form'una yazılır).")
  private String companyHeadquarters;

  @NotBlank
  @Size(min = 3)
  @Schema(description = "PCSC okuyucu (terminal) adı.")
  private String terminalName;

  @NotBlank
  @Size(min = 4, max = 6)
  @Schema(description = "Akıllı kart PIN'i (4-6 hane).")
  private String pin;

  @NotBlank
  @Schema(
      description =
          "İmzalama için kullanılacak sertifikanın PKCS#11 alias'ı / X.509 serial number'ı."
              + " `GET /smartcard/certificate` yanıtındaki `id` veya `x509SerialNumber`'dan alınır.")
  private String certificateId;

  @Schema(
      description =
          "İsteğe bağlı PKCS#11 paylaşımlı kütüphane yolu (bare ad veya tam path). Verilmezse"
              + " kart tipi ATR ile algılanıp varsayılan vendor lib aranır.")
  private String pkcs11LibraryPath;

  /* -------------------------------------------------------------------- */
  /* Tüzel kişi opsiyonel alanları — GİB formundaki ticaret sicili         */
  /* bloğu. Hiçbirisi `@NotBlank` değildir; eski projeyle geriye uyumluluk */
  /* için verilmezse boş geçilir, verilirse forma yazılır.                 */
  /* -------------------------------------------------------------------- */

  @Schema(description = "Ticaret sicil numarası (opsiyonel, sadece tüzel kişi).")
  private String tradeRegistryNo;

  @Schema(description = "Ticaret sicil memurluğu (opsiyonel, sadece tüzel kişi).")
  private String tradeRegistryOffice;

  @Schema(description = "Şirketin kuruluş tarihi, GİB beklediği serbest format (opsiyonel).")
  private String foundationDate;

  @Schema(description = "Bağlı bulunduğu oda (opsiyonel, sadece tüzel kişi).")
  private String chamberName;

  @Schema(description = "Oda sicil numarası (opsiyonel, sadece tüzel kişi).")
  private String chamberRegistryNo;

  @Schema(description = "Şirket web sitesi (opsiyonel). Verilmezse forma boş yazılır.")
  private String website;

  @Schema(description = "Faks numarası (opsiyonel). Verilmezse forma boş yazılır.")
  private String fax;

  /* -------------------------------------------------------------------- */
  /* Sorumlu (yetkili) kişi opsiyonel alanları — başvuruyu yapan gerçek    */
  /* kişiyi GİB formuna yazmak için. Tüzel kişi başvurusunda anlamlı.      */
  /* -------------------------------------------------------------------- */

  @Schema(description = "Sorumlu kişinin TCKN'si (opsiyonel, 11 hane).")
  private String responsibleTckn;

  @Schema(description = "Sorumlu kişinin adı (opsiyonel).")
  private String responsibleFirstName;

  @Schema(description = "Sorumlu kişinin soyadı (opsiyonel).")
  private String responsibleLastName;

  @Schema(description = "Sorumlu kişinin cep telefonu (opsiyonel).")
  private String responsibleMobilePhone;

  @Schema(description = "Sorumlu kişinin e-posta adresi (opsiyonel).")
  private String responsibleEmail;

  @Schema(
      description =
          "GİB Mali Mühür talep bayrağı (opsiyonel). 0 = istenmez, 1 = istenir. Verilmezse 0.")
  private Integer requestsFinancialSeal;

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getCompanyHeadquarters() {
    return companyHeadquarters;
  }

  public void setCompanyHeadquarters(String companyHeadquarters) {
    this.companyHeadquarters = companyHeadquarters;
  }

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

  public String getCertificateId() {
    return certificateId;
  }

  public void setCertificateId(String certificateId) {
    this.certificateId = certificateId;
  }

  public String getPkcs11LibraryPath() {
    return pkcs11LibraryPath;
  }

  public void setPkcs11LibraryPath(String pkcs11LibraryPath) {
    this.pkcs11LibraryPath = pkcs11LibraryPath;
  }

  public String getTradeRegistryNo() {
    return tradeRegistryNo;
  }

  public void setTradeRegistryNo(String tradeRegistryNo) {
    this.tradeRegistryNo = tradeRegistryNo;
  }

  public String getTradeRegistryOffice() {
    return tradeRegistryOffice;
  }

  public void setTradeRegistryOffice(String tradeRegistryOffice) {
    this.tradeRegistryOffice = tradeRegistryOffice;
  }

  public String getFoundationDate() {
    return foundationDate;
  }

  public void setFoundationDate(String foundationDate) {
    this.foundationDate = foundationDate;
  }

  public String getChamberName() {
    return chamberName;
  }

  public void setChamberName(String chamberName) {
    this.chamberName = chamberName;
  }

  public String getChamberRegistryNo() {
    return chamberRegistryNo;
  }

  public void setChamberRegistryNo(String chamberRegistryNo) {
    this.chamberRegistryNo = chamberRegistryNo;
  }

  public String getWebsite() {
    return website;
  }

  public void setWebsite(String website) {
    this.website = website;
  }

  public String getFax() {
    return fax;
  }

  public void setFax(String fax) {
    this.fax = fax;
  }

  public String getResponsibleTckn() {
    return responsibleTckn;
  }

  public void setResponsibleTckn(String responsibleTckn) {
    this.responsibleTckn = responsibleTckn;
  }

  public String getResponsibleFirstName() {
    return responsibleFirstName;
  }

  public void setResponsibleFirstName(String responsibleFirstName) {
    this.responsibleFirstName = responsibleFirstName;
  }

  public String getResponsibleLastName() {
    return responsibleLastName;
  }

  public void setResponsibleLastName(String responsibleLastName) {
    this.responsibleLastName = responsibleLastName;
  }

  public String getResponsibleMobilePhone() {
    return responsibleMobilePhone;
  }

  public void setResponsibleMobilePhone(String responsibleMobilePhone) {
    this.responsibleMobilePhone = responsibleMobilePhone;
  }

  public String getResponsibleEmail() {
    return responsibleEmail;
  }

  public void setResponsibleEmail(String responsibleEmail) {
    this.responsibleEmail = responsibleEmail;
  }

  public Integer getRequestsFinancialSeal() {
    return requestsFinancialSeal;
  }

  public void setRequestsFinancialSeal(Integer requestsFinancialSeal) {
    this.requestsFinancialSeal = requestsFinancialSeal;
  }
}
