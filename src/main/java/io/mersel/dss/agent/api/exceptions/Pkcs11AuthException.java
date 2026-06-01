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

/**
 * PIN doğrulaması veya PIN-ilişkili PKCS#11 işlemleri başarısız olduğunda fırlatılır.
 *
 * <p>Frontend'in akıllı kart girişimini disable etmesi / PUK reset yönlendirmesi yapabilmesi için
 * üç ek alan taşır:
 *
 * <ul>
 *   <li>{@link #getPkcs11Code()} — orijinal PKCS#11 v2.40 sembolik kodu (ör. {@code
 *       CKR_PIN_INCORRECT}, {@code CKR_PIN_LOCKED}); cause zinciri parse edilerek elde edilir.
 *   <li>{@link #isLocked()} — {@code true} ise kart kilitli, retry imkânsız.
 *   <li>{@link #getAttemptsRemainingHint()} — bilinen denenebilir hak sayısı veya nitel ipucu
 *       ({@code "0"} kilitli için; ileride {@code "1"} / {@code "low"} eklenebilir).
 * </ul>
 *
 * <p>Geriye uyumluluk: Eski 1-arg / 2-arg constructor'lar korunur; {@code errorCode} eskisi gibi
 * {@code PKCS11_AUTH_FAILED} olur ve yeni alanlar null kalır.
 */
public class Pkcs11AuthException extends SignerException {
  private static final long serialVersionUID = 1L;

  private static final String DEFAULT_CODE = "PKCS11_AUTH_FAILED";

  private final String pkcs11Code;
  private final boolean locked;
  private final String attemptsRemainingHint;

  public Pkcs11AuthException(String message) {
    super(DEFAULT_CODE, message);
    this.pkcs11Code = null;
    this.locked = false;
    this.attemptsRemainingHint = null;
  }

  public Pkcs11AuthException(String message, Throwable cause) {
    super(DEFAULT_CODE, message, cause);
    this.pkcs11Code = null;
    this.locked = false;
    this.attemptsRemainingHint = null;
  }

  /**
   * Yapısal constructor — {@link io.mersel.dss.agent.api.services.keystore.Pkcs11Errors#classify}
   * çıktısından gelen alanlarla zenginleştirilmiş hata fırlatmak için.
   *
   * @param errorCode wire-format kod, ör. {@code PKCS11_PIN_INCORRECT} veya {@code
   *     PKCS11_PIN_LOCKED}; null ise {@code PKCS11_AUTH_FAILED} kullanılır
   * @param message kullanıcıya gösterilebilecek Türkçe mesaj
   * @param cause kök sebep (sarmalanmış {@code PKCS11Exception} dahil)
   * @param pkcs11Code orijinal {@code CKR_xxx} sembolik kodu; bilgi amaçlı
   * @param locked {@code true} ise kart PIN kilidi durumunda
   * @param attemptsRemainingHint bilinen kalan hak sayısı / nitel ipucu, yoksa null
   */
  public Pkcs11AuthException(
      String errorCode,
      String message,
      Throwable cause,
      String pkcs11Code,
      boolean locked,
      String attemptsRemainingHint) {
    super(errorCode == null ? DEFAULT_CODE : errorCode, message, cause);
    this.pkcs11Code = pkcs11Code;
    this.locked = locked;
    this.attemptsRemainingHint = attemptsRemainingHint;
  }

  public String getPkcs11Code() {
    return pkcs11Code;
  }

  public boolean isLocked() {
    return locked;
  }

  public String getAttemptsRemainingHint() {
    return attemptsRemainingHint;
  }
}
