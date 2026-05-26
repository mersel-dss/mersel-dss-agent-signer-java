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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Akıllı kart sürücüleri için resmi indirme/destek sayfası eşleştirmesi.
 *
 * <p>PKCS#11 kütüphanesi sistemde bulunamadığında kullanıcıya "{cardType} kartı algılandı, lütfen
 * şu adresten sürücüyü indirin" yönlendirmesi yapılır. Adresler vendor'un resmi (veya en yetkili
 * kamuya açık) destek sayfasıdır; doğrudan binary'ye değil sayfaya yönlendiriyoruz, böylece vendor
 * sayfa düzenini değiştirse bile mesaj geçerliliğini korur.
 *
 * <p>Bu sınıf bir bean değildir; tamamen statik veri lookup'ıdır.
 */
public final class Pkcs11VendorHints {

  /**
   * Bilinen kart sürücü adresleri tablosu. Anahtar: {@link
   * io.mersel.dss.agent.api.services.smartcard.CardType#getName()} büyük harf hâli
   * (case-insensitive lookup için). Değer: vendor'un resmi indirme veya destek URL'i.
   */
  private static final Map<String, String> DOWNLOAD_URLS;

  static {
    Map<String, String> m = new LinkedHashMap<String, String>();

    // Türkiye'nin en yaygın e-imza kartları — Kamu SM AKİS sürücü paketi
    m.put(
        "AKIS",
        "https://kamusm.bilgem.tubitak.gov.tr/islemler/uretici_pkcs11_kart_yazilimlari.jsp");
    m.put(
        "AKIS_KK",
        "https://kamusm.bilgem.tubitak.gov.tr/islemler/uretici_pkcs11_kart_yazilimlari.jsp");
    m.put(
        "ATIKKG",
        "https://kamusm.bilgem.tubitak.gov.tr/islemler/uretici_pkcs11_kart_yazilimlari.jsp");
    m.put(
        "ATIKHSM",
        "https://kamusm.bilgem.tubitak.gov.tr/islemler/uretici_pkcs11_kart_yazilimlari.jsp");
    m.put(
        "DIRAKHSM",
        "https://kamusm.bilgem.tubitak.gov.tr/islemler/uretici_pkcs11_kart_yazilimlari.jsp");
    m.put(
        "TKART",
        "https://kamusm.bilgem.tubitak.gov.tr/islemler/uretici_pkcs11_kart_yazilimlari.jsp");

    // SafeNet / Thales / Aladdin
    m.put("ALADDIN", "https://safenet.gemalto.com/sas/sac-download/");
    m.put("SAFENET", "https://supportportal.thalesgroup.com/csm");
    m.put("GEMALTO", "https://supportportal.thalesgroup.com/csm");
    m.put("GEMPLUS", "https://supportportal.thalesgroup.com/csm");

    // A.E.T. SafeSign
    m.put("SAFESIGN", "https://www.aeteurope.com/products/safesign-identity-client/");

    // SecMaker / Net iD
    m.put("NETID", "https://secmaker.com/en/products/net-id/");

    // KOBIL mIDentity
    m.put("KOBIL", "https://www.kobil.com/products/identity-access-management/");

    // Atos CardOS
    m.put(
        "CARDOS",
        "https://atos.net/en/solutions/cyber-security/data-protection-and-governance/cardos");

    // ACS (Advanced Card Systems)
    m.put("ACS", "https://www.acs.com.hk/en/drivers/");

    // KeyCorp / DataKey
    m.put("KEYCORP", "https://www.entrust.com/products/digital-certificates/safenet-tokens");
    m.put("DATAKEY", "https://www.entrust.com/products/digital-certificates/safenet-tokens");

    // AEP HSM
    m.put("AEPKEYPER", "https://www.ultra-electronics.com/cyber-security/keyper-hsm");

    // nCipher / Entrust nShield
    m.put("NCIPHER", "https://www.entrust.com/products/hsm/nshield");

    // Utimaco HSM
    m.put("UTIMACO", "https://utimaco.com/products/categories/general-purpose-hsm");
    m.put("UTIMACO_R2", "https://utimaco.com/products/categories/general-purpose-hsm");

    // Yerli HSM
    m.put("PROCENNEHSM", "https://www.procenne.com.tr/cozumler/donanim-guvenlik-modulu/");

    // Yazılım HSM (geliştirici / test)
    m.put("OPENDNSSOFTHSM", "https://github.com/opendnssec/SoftHSMv2/releases");

    // Sefirot
    m.put("SEFIROT", "https://www.sefirot.com.tr/");

    DOWNLOAD_URLS = Collections.unmodifiableMap(m);
  }

  private Pkcs11VendorHints() {
    /* utility */
  }

  /**
   * Belirtilen kart tipi için vendor indirme/destek URL'ini döndürür.
   *
   * @param cardTypeName {@link io.mersel.dss.agent.api.services.smartcard.CardType#getName()};
   *     büyük/küçük harf önemsiz
   * @return URL string veya bilinmiyorsa {@code null}
   */
  public static String downloadUrlFor(String cardTypeName) {
    if (cardTypeName == null) return null;
    return DOWNLOAD_URLS.get(cardTypeName.toUpperCase(Locale.ROOT));
  }

  /**
   * Bilinen kart için "X kartı için Y'den indirin" formatında kısa Türkçe ipucu döndürür.
   *
   * @param cardTypeName kart tipi adı (örn "AKIS")
   * @return tek satırlık öneri veya bilinmiyorsa <em>null</em>
   */
  public static String hintFor(String cardTypeName) {
    String url = downloadUrlFor(cardTypeName);
    if (url == null) return null;
    return cardTypeName + " kartı için PKCS#11 sürücüsünü " + url + " adresinden indirin.";
  }

  /** İçindeki tüm eşleştirmeleri (test / debug amaçlı). */
  public static Map<String, String> knownHints() {
    return DOWNLOAD_URLS;
  }
}
