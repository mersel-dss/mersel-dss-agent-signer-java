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
package io.mersel.dss.agent.api.exceptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Algılanan kart için PKCS#11 paylaşımlı kütüphanesi (DLL/SO/DYLIB) sistemde bulunamadı.
 *
 * <p>Genel {@link Pkcs11LibraryException}'dan farkı: bu exception kullanıcıyı yönlendirebilecek
 * yapısal bilgi taşır:
 *
 * <ul>
 *   <li>{@code cardType} — algılanan kart tipi (örn "AKIS"); ATR'den çözülür (null olabilir)
 *   <li>{@code requiredLibrary} — aranan ham lib adı (örn "akisp11")
 *   <li>{@code searchedPaths} — diskte taranıp bulunamayan yollar
 *   <li>{@code downloadHint} — vendor sürücü indirme URL'i (varsa)
 *   <li>{@code cardTypeCandidates} — Layer 5 fallback: kart otomatik tanınamadığında frontend'in
 *       kullanıcıya gösterebileceği seçenek listesi (alfabetik kart tipi adları)
 *   <li>{@code userSelectionRequired} — frontend'in "kart tipi seç" modal'ını açması gerektiği
 *       ipucu; {@code cardType == null && !cardTypeCandidates.isEmpty()} ise {@code true}
 * </ul>
 *
 * <p>{@code GlobalExceptionHandler} bu alanları zenginleştirilmiş JSON yanıta serialize eder ve 503
 * Service Unavailable döner.
 */
public class Pkcs11LibraryNotFoundException extends SignerException {
  private static final long serialVersionUID = 1L;

  /** {@link io.mersel.dss.agent.api.models.ErrorModel#getCode()} alanına yazılan kod. */
  public static final String CODE = "PKCS11_LIBRARY_NOT_FOUND";

  private final String cardType;
  private final String requiredLibrary;
  private final List<String> searchedPaths;
  private final String downloadHint;
  private final List<String> cardTypeCandidates;
  private final boolean userSelectionRequired;

  public Pkcs11LibraryNotFoundException(
      String cardType,
      String requiredLibrary,
      List<String> searchedPaths,
      String downloadHint,
      String message) {
    this(cardType, requiredLibrary, searchedPaths, downloadHint, message, null);
  }

  public Pkcs11LibraryNotFoundException(
      String cardType,
      String requiredLibrary,
      List<String> searchedPaths,
      String downloadHint,
      String message,
      List<String> cardTypeCandidates) {
    super(CODE, message);
    this.cardType = cardType;
    this.requiredLibrary = requiredLibrary;
    this.searchedPaths =
        searchedPaths == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(searchedPaths));
    this.downloadHint = downloadHint;
    this.cardTypeCandidates =
        cardTypeCandidates == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(cardTypeCandidates));
    // Layer 5 sözleşmesi: kart algılanamadı VE elimizde aday liste varsa,
    // frontend kullanıcıdan seçim istemeli.
    this.userSelectionRequired = (cardType == null) && !this.cardTypeCandidates.isEmpty();
  }

  public String getCardType() {
    return cardType;
  }

  public String getRequiredLibrary() {
    return requiredLibrary;
  }

  public List<String> getSearchedPaths() {
    return searchedPaths;
  }

  public String getDownloadHint() {
    return downloadHint;
  }

  public List<String> getCardTypeCandidates() {
    return cardTypeCandidates;
  }

  public boolean isUserSelectionRequired() {
    return userSelectionRequired;
  }
}
