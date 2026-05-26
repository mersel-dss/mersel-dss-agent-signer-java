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
package io.mersel.dss.agent.api.models.enums;

import java.util.Optional;

/**
 * TÜBİTAK Kamu SM tarafından tahsis edilen ve Türkiye'deki elektronik imza / mali mühür
 * sertifikalarında {@code CertificatePolicies} (RFC 5280 §4.2.1.4, OID {@code 2.5.29.32})
 * extension'ında görülen politika OID'leri.
 *
 * <p>Bu OID'ler RFC 5280 generic karar ağacının çözemediği "bu kart üzerindeki hangi sertifika
 * imza, hangisi şifreleme?" sorusunu net şekilde cevaplar — TÜBİTAK ESYA SDK'sının ve e-Devlet
 * imzalama araçlarının cert seçiminde kullandığı asıl sinyal budur.
 *
 * <p>OID arc kaynağı: TÜBİTAK Kamu Sertifikasyon Merkezi sertifika ilke dokümanları (OID {@code
 * 2.16.792} = Türkiye Cumhuriyeti).
 *
 * <ul>
 *   <li>{@code 2.16.792.1.61.0.1.5070.*} — Nitelikli Elektronik Sertifika (QES) arc'ı.
 *   <li>{@code 2.16.792.1.2.1.1.5.7.50.*} — Mali Mühür (Tüzel Kişi) arc'ı.
 * </ul>
 */
public enum TurkishCertificatePolicy {
  /** {@code 2.16.792.1.61.0.1.5070.1.1} — Bireysel Nitelikli Elektronik İmza Sertifikası (QES). */
  KAMU_SM_QES_INDIVIDUAL("2.16.792.1.61.0.1.5070.1.1", CertificatePurpose.SIGNING, true),

  /** {@code 2.16.792.1.61.0.1.5070.1.2} — Kurumsal Nitelikli Elektronik İmza Sertifikası (QES). */
  KAMU_SM_QES_CORPORATE("2.16.792.1.61.0.1.5070.1.2", CertificatePurpose.SIGNING, true),

  /** {@code 2.16.792.1.2.1.1.5.7.50.1} — Mali Mühür İMZA sertifikası (e-Fatura, e-Arşiv, GİB). */
  MALI_MUHUR_SIGNING("2.16.792.1.2.1.1.5.7.50.1", CertificatePurpose.SIGNING, false),

  /** {@code 2.16.792.1.2.1.1.5.7.50.2} — Mali Mühür ŞİFRELEME sertifikası. */
  MALI_MUHUR_ENCRYPTION("2.16.792.1.2.1.1.5.7.50.2", CertificatePurpose.ENCRYPTION, false);

  private final String oid;
  private final CertificatePurpose impliedPurpose;
  private final boolean qualified;

  TurkishCertificatePolicy(String oid, CertificatePurpose impliedPurpose, boolean qualified) {
    this.oid = oid;
    this.impliedPurpose = impliedPurpose;
    this.qualified = qualified;
  }

  public String oid() {
    return oid;
  }

  /** Bu politikanın işaret ettiği iş amacı — {@code purpose()} algoritmasının kısa-yolu için. */
  public CertificatePurpose impliedPurpose() {
    return impliedPurpose;
  }

  /** ETSI EN 319 411-2 anlamında nitelikli (QES) sertifika mı? */
  public boolean qualified() {
    return qualified;
  }

  /** OID string'inden enum eşleştirmesi; bilinmiyorsa {@link Optional#empty()}. */
  public static Optional<TurkishCertificatePolicy> fromOid(String oid) {
    if (oid == null) {
      return Optional.empty();
    }
    String trimmed = oid.trim();
    for (TurkishCertificatePolicy v : values()) {
      if (v.oid.equals(trimmed)) {
        return Optional.of(v);
      }
    }
    return Optional.empty();
  }
}
