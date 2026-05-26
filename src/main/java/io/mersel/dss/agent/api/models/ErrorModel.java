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

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tüm REST endpoint'lerinde standardize edilmiş hata yanıt zarfı.
 *
 * <p>{@code code} alanı bir machine-readable error code'dur (ör. {@code PKCS11_AUTH_FAILED});
 * {@code message} ise insan tarafından okunabilir Türkçe açıklama. {@code timestamp} her cevaba
 * dinamik olarak yazılır.
 *
 * <p>PKCS#11 kütüphanesi bulunamadığında ek tanılama alanları doldurulur: {@code cardType}, {@code
 * requiredLibrary}, {@code searchedPaths}, {@code downloadHint}, {@code cardTypeCandidates}, {@code
 * userSelectionRequired}. {@code @JsonInclude.NON_NULL} sayesinde bu alanlar diğer hatalarda JSON
 * çıktısında görünmez.
 *
 * <p>Layer 5 fallback sözleşmesi: {@code cardType == null && userSelectionRequired == true} →
 * frontend kullanıcıya {@code cardTypeCandidates} listesinden seçim modalı göstermeli ve sonra
 * {@code GET /smartcard/certificate?cardType=...} ile manual override yapmalı.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorModel {

  private String code;
  private String message;
  private String timestamp;

  // PKCS#11 lib bulunamadı tanılama alanları (sadece PKCS11_LIBRARY_NOT_FOUND için).
  private String cardType;
  private String requiredLibrary;
  private List<String> searchedPaths;
  private String downloadHint;

  // Layer 5 fallback alanları — frontend kart seçim modal'ı için.
  private List<String> cardTypeCandidates;
  private Boolean userSelectionRequired;

  public ErrorModel() {
    this.timestamp = OffsetDateTime.now().toString();
  }

  public ErrorModel(String code, String message) {
    this();
    this.code = code;
    this.message = message;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public String getCardType() {
    return cardType;
  }

  public void setCardType(String cardType) {
    this.cardType = cardType;
  }

  public String getRequiredLibrary() {
    return requiredLibrary;
  }

  public void setRequiredLibrary(String requiredLibrary) {
    this.requiredLibrary = requiredLibrary;
  }

  public List<String> getSearchedPaths() {
    return searchedPaths;
  }

  public void setSearchedPaths(List<String> searchedPaths) {
    this.searchedPaths = searchedPaths;
  }

  public String getDownloadHint() {
    return downloadHint;
  }

  public void setDownloadHint(String downloadHint) {
    this.downloadHint = downloadHint;
  }

  public List<String> getCardTypeCandidates() {
    return cardTypeCandidates;
  }

  public void setCardTypeCandidates(List<String> cardTypeCandidates) {
    this.cardTypeCandidates = cardTypeCandidates;
  }

  public Boolean getUserSelectionRequired() {
    return userSelectionRequired;
  }

  public void setUserSelectionRequired(Boolean userSelectionRequired) {
    this.userSelectionRequired = userSelectionRequired;
  }
}
