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
package io.mersel.dss.agent.api.services.keystore;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * SunPKCS11'in fırlattığı sarmalanmış {@link java.io.IOException}'lar içinden orijinal {@code
 * sun.security.pkcs11.wrapper.PKCS11Exception}'ı bulup PKCS#11 v2.40 §A "Return Values"
 * tablosundaki {@code CKR_xxx} sembolik koduna dönüştüren yardımcı.
 *
 * <p>Tipik sarmalama (JDK 1.8'de {@code KeyStore.PKCS11.load()} → {@code C_Login} yolu):
 *
 * <pre>
 * IOException("load failed")
 *   └─ UnrecoverableKeyException(null)
 *       └─ FailedLoginException(null)
 *           └─ PKCS11Exception("CKR_PIN_INCORRECT")
 * </pre>
 *
 * <p>{@link Pkcs11Session#open} eskiden sadece üstteki "load failed" mesajına bakıyordu; bu yüzden
 * yanlış PIN'de bile {@code PKCS11_UNAVAILABLE} gibi yanıltıcı kodlar dönüyordu. Bu helper cause
 * zincirini gezerek {@code PKCS11Exception}'ı yakalıyor; reflection kullanmadan sadece sınıf adı +
 * mesaj string'i üzerinden iş görüyor (JDK iç paket erişim ihtiyacı yok).
 *
 * <p>{@link #classify(Throwable)} bilinen PIN-ilişkili kodları yapısal {@link Outcome} olarak
 * döndürür; bilinmeyen kodlar için {@code outcome.kind == Kind.UNKNOWN} olur ve çağıran kod {@code
 * Pkcs11LibraryException} fırlatabilir.
 */
public final class Pkcs11Errors {

  /** SunPKCS11 wrapper'ının dahili sınıf adı. Reflection değil, sadece string karşılaştırması. */
  private static final String PKCS11_EXCEPTION_FQN = "sun.security.pkcs11.wrapper.PKCS11Exception";

  /** {@link Throwable#getCause} loop koruması için maksimum derinlik. */
  private static final int MAX_DEPTH = 16;

  private Pkcs11Errors() {
    /* utility */
  }

  /**
   * {@code throwable} ve cause zincirinde {@code sun.security.pkcs11.wrapper.PKCS11Exception}
   * sınıfından bir node ararsa onun mesajını ({@code CKR_xxx}) döndürür; yoksa null. Cycle koruması
   * IdentityHashMap ile yapılır.
   */
  public static String extractCkrCode(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    Map<Throwable, Boolean> seen = new IdentityHashMap<Throwable, Boolean>();
    Throwable cur = throwable;
    int depth = 0;
    while (cur != null && depth < MAX_DEPTH) {
      if (seen.containsKey(cur)) {
        return null;
      }
      seen.put(cur, Boolean.TRUE);

      if (PKCS11_EXCEPTION_FQN.equals(cur.getClass().getName())) {
        String msg = cur.getMessage();
        if (msg != null) {
          // PKCS11Exception.toString() bazen "CKR_xxx (0x00000060)" döndürür; sadece ilk token'ı
          // al.
          String token = msg.trim();
          int sp = token.indexOf(' ');
          if (sp > 0) {
            token = token.substring(0, sp);
          }
          return token.toUpperCase(Locale.ROOT);
        }
      }
      cur = cur.getCause();
      depth++;
    }
    return null;
  }

  /**
   * Yakalanan exception'ı PKCS#11 spec'ine göre yapısal {@link Outcome}'a çevirir. PIN-ilişkili
   * bilinen kodlar tanımlıdır; geri kalanı {@link Kind#UNKNOWN} olarak işaretlenir ve çağıran
   * tarafa düşer (genelde {@code PKCS11_UNAVAILABLE}).
   *
   * <p>Frontend uyumluluğu için ürettiğimiz {@code errorCode}'lar {@link
   * io.mersel.dss.agent.api.models.ErrorModel#code} alanında olduğu gibi yer alır.
   */
  public static Outcome classify(Throwable throwable) {
    String ckr = extractCkrCode(throwable);
    if (ckr == null) {
      return Outcome.unknown(null);
    }
    switch (ckr) {
      case "CKR_PIN_INCORRECT":
        return new Outcome(
            Kind.PIN_INCORRECT,
            "PKCS11_PIN_INCORRECT",
            ckr,
            false,
            null,
            "Girilen PIN yanlış. Dikkat: arka arkaya yanlış girişlerde kart kilitlenir.");
      case "CKR_PIN_LOCKED":
        return new Outcome(
            Kind.PIN_LOCKED,
            "PKCS11_PIN_LOCKED",
            ckr,
            true,
            "0",
            "Kart, art arda yanlış PIN girişleri nedeniyle kilitlendi. PUK ile sıfırlamanız gerekir.");
      case "CKR_PIN_EXPIRED":
        return new Outcome(
            Kind.PIN_EXPIRED,
            "PKCS11_PIN_EXPIRED",
            ckr,
            false,
            null,
            "Kartın PIN süresi dolmuş. Kart yöneticisinden / SIM merkezinden PIN'i yenilemelisiniz.");
      case "CKR_PIN_INVALID":
      case "CKR_PIN_LEN_RANGE":
        return new Outcome(
            Kind.PIN_INVALID_FORMAT,
            "PKCS11_PIN_INVALID_FORMAT",
            ckr,
            false,
            null,
            "PIN formatı geçersiz (uzunluk ya da içerik kart politikasına uymuyor).");
      case "CKR_USER_ALREADY_LOGGED_IN":
        return new Outcome(
            Kind.SESSION_BUSY,
            "PKCS11_SESSION_BUSY",
            ckr,
            false,
            null,
            "Karta zaten oturum açılmış. Diğer uygulamayı kapatıp tekrar deneyin.");
      case "CKR_TOKEN_NOT_PRESENT":
      case "CKR_DEVICE_REMOVED":
      case "CKR_DEVICE_ERROR":
        return new Outcome(
            Kind.DEVICE_REMOVED,
            "SMARTCARD_REMOVED",
            ckr,
            false,
            null,
            "Akıllı kart okuyucudan çıkarıldı veya cihaz hatası oluştu. Kartı tekrar takın.");
      default:
        return Outcome.unknown(ckr);
    }
  }

  /** PKCS#11 hata kategorileri — controller / handler bu enum üzerinden HTTP status seçer. */
  public enum Kind {
    /** Yanlış PIN. Frontend retry önerebilir AMA kart-kilit sayacını da sayar. */
    PIN_INCORRECT,
    /** PIN kilitli. Frontend PIN alanını disable etmeli, PUK reset yönlendirmesi göstermeli. */
    PIN_LOCKED,
    /** PIN süresi dolmuş. Out-of-band reset gerekiyor. */
    PIN_EXPIRED,
    /** PIN format / uzunluk hatası. PIN bile harcanmadan reddedildi. */
    PIN_INVALID_FORMAT,
    /** Aynı slot'ta başka bir uygulama login durumda — concurrent kullanım. */
    SESSION_BUSY,
    /** Kart fiziksel olarak çekildi / sürücü hatası. */
    DEVICE_REMOVED,
    /** PKCS11Exception bulundu ama bilinen tablo eşleşmedi. */
    UNKNOWN
  }

  /** {@link Pkcs11Errors#classify} dönüş tipi — değişmez (immutable). */
  public static final class Outcome {
    private final Kind kind;
    private final String errorCode;
    private final String pkcs11Code;
    private final boolean locked;
    private final String attemptsRemainingHint;
    private final String message;

    public Outcome(
        Kind kind,
        String errorCode,
        String pkcs11Code,
        boolean locked,
        String attemptsRemainingHint,
        String message) {
      this.kind = kind;
      this.errorCode = errorCode;
      this.pkcs11Code = pkcs11Code;
      this.locked = locked;
      this.attemptsRemainingHint = attemptsRemainingHint;
      this.message = message;
    }

    static Outcome unknown(String pkcs11Code) {
      return new Outcome(Kind.UNKNOWN, null, pkcs11Code, false, null, null);
    }

    public Kind getKind() {
      return kind;
    }

    /**
     * Wire-format error code, ör. {@code PKCS11_PIN_INCORRECT}. {@link Kind#UNKNOWN} için null —
     * çağıran fallback bir kod (genellikle {@code PKCS11_UNAVAILABLE}) atamalıdır.
     */
    public String getErrorCode() {
      return errorCode;
    }

    /** Orijinal PKCS#11 sembolik kodu, ör. {@code CKR_PIN_INCORRECT}. */
    public String getPkcs11Code() {
      return pkcs11Code;
    }

    public boolean isLocked() {
      return locked;
    }

    /**
     * Frontend için kullanıcı dostu deneme hakkı ipucu. Şu anda yalnız {@code "0"} (kilitli) döner.
     * PKCS#11 v2.20 {@code CKF_USER_PIN_FINAL_TRY} / {@code CKF_USER_PIN_COUNT_LOW} bayraklarını
     * okuyup {@code "1"} / {@code "low"} döndürmek için ileride {@code C_GetTokenInfo} reflection
     * entegrasyonu eklenebilir.
     */
    public String getAttemptsRemainingHint() {
      return attemptsRemainingHint;
    }

    public String getMessage() {
      return message;
    }
  }
}
