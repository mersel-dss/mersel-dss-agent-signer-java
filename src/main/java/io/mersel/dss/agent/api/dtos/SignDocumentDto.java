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

import org.springframework.web.multipart.MultipartFile;

import io.mersel.dss.agent.api.models.enums.XmlContentType;
import io.swagger.v3.oas.annotations.media.Schema;

/** XAdES / PAdES imzalama istek modeli (multipart {@code @ModelAttribute}). */
@Schema(description = "Doküman imzalama isteği")
public class SignDocumentDto {

  @Schema(description = "İmzalanacak içerik (PDF / XML).", required = true)
  private MultipartFile content;

  @NotBlank
  @Schema(description = "PCSC okuyucu (terminal) adı.", example = "ACS ACR39U-ND ICC Reader")
  private String terminalName;

  @NotBlank
  @Schema(description = "Akıllı kart PIN'i (4-6 hane).", example = "1234")
  private String pin;

  @NotBlank
  @Schema(
      description =
          "İmzalama için kullanılacak sertifikanın PKCS#11 alias'ı veya X.509 serial number'ı"
              + " (hex). `GET /smartcard/certificate` yanıtındaki `id` alanından alınır.")
  private String certificateId;

  @Schema(
      description =
          "İsteğe bağlı PKCS#11 paylaşımlı kütüphane yolu (bare ad veya tam path). Verilmezse"
              + " kart tipi ATR ile algılanıp varsayılan vendor lib aranır.")
  private String pkcs11LibraryPath;

  @Schema(
      description =
          "XAdES için XML içerik tipi. PAdES'te kullanılmaz. Değerler: XmlDocument |"
              + " HrXmlCounterSignature (case-insensitive).")
  private XmlContentType contentType;

  @Schema(
      description =
          "PDF üzerine ikinci bir imza alanı eklenirken incremental update modu. Default: false.")
  private Boolean appendMode;

  @Schema(
      description =
          "PAdES için opsiyonel imza nedeni — PDF'in imza panelinde 'Reason' alanında görünür. Boş"
              + " bırakılırsa varsayılan \"e-Belge imzalama\" kullanılır. XAdES'te kullanılmaz.")
  private String reason;

  @Schema(
      description =
          "PAdES için opsiyonel konum bilgisi — PDF'in imza panelinde 'Location' alanında görünür."
              + " Boş bırakılırsa konum boş yazılır. XAdES'te kullanılmaz.")
  private String location;

  public SignDocumentDto() {}

  public MultipartFile getContent() {
    return content;
  }

  public void setContent(MultipartFile content) {
    this.content = content;
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

  public XmlContentType getContentType() {
    return contentType;
  }

  public void setContentType(XmlContentType contentType) {
    this.contentType = contentType;
  }

  public Boolean getAppendMode() {
    return appendMode;
  }

  public void setAppendMode(Boolean appendMode) {
    this.appendMode = appendMode;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }
}
