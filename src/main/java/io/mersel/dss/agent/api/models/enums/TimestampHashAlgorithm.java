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

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.tsp.TSPAlgorithms;

/**
 * RFC 3161 zaman damgası taleplerinde kullanılan hash algoritmaları.
 *
 * <p>Sunucu projesi (mersel-dss-server-signer) bu eşleştirmeyi Avrupa DSS kütüphanesinin {@code
 * DigestAlgorithm} enum'undan alır. Agent projesi DSS bağımlılığı taşımadığından (sadece
 * BouncyCastle + OkHttp), aynı eşleştirme burada elle tutulur: her algoritma için BouncyCastle
 * {@link TSPAlgorithms} OID sabiti (TSQ üretimi için), Java {@link java.security.MessageDigest}
 * adı (digest hesaplama için) ve OID string'i (TSR doğrulamasında ters arama için) saklanır.
 */
public enum TimestampHashAlgorithm {
  SHA1("SHA-1", "1.3.14.3.2.26", "SHA-1", TSPAlgorithms.SHA1),
  SHA224("SHA-224", "2.16.840.1.101.3.4.2.4", "SHA-224", TSPAlgorithms.SHA224),
  SHA256("SHA-256", "2.16.840.1.101.3.4.2.1", "SHA-256", TSPAlgorithms.SHA256),
  SHA384("SHA-384", "2.16.840.1.101.3.4.2.2", "SHA-384", TSPAlgorithms.SHA384),
  SHA512("SHA-512", "2.16.840.1.101.3.4.2.3", "SHA-512", TSPAlgorithms.SHA512);

  private final String displayName;
  private final String oid;
  private final String javaName;
  private final ASN1ObjectIdentifier tspOid;

  TimestampHashAlgorithm(
      String displayName, String oid, String javaName, ASN1ObjectIdentifier tspOid) {
    this.displayName = displayName;
    this.oid = oid;
    this.javaName = javaName;
    this.tspOid = tspOid;
  }

  /** İnsan tarafından okunabilir ad (ör. {@code SHA-256}). */
  public String getDisplayName() {
    return displayName;
  }

  /** Algoritma OID'i (ör. {@code 2.16.840.1.101.3.4.2.1}). */
  public String getOid() {
    return oid;
  }

  /** {@link java.security.MessageDigest#getInstance(String)} için Java adı. */
  public String getJavaName() {
    return javaName;
  }

  /** BouncyCastle {@code TimeStampRequestGenerator.generate(...)} için ASN.1 OID sabiti. */
  public ASN1ObjectIdentifier getTspOid() {
    return tspOid;
  }

  /**
   * İstemciden gelen serbest metin algoritma adını enum değerine çevirir. {@code SHA256}, {@code
   * sha-256}, {@code SHA_256} gibi varyasyonlar tolere edilir. Boş / null verilirse {@link #SHA256}
   * varsayılanı döner.
   *
   * @throws IllegalArgumentException tanınmayan algoritma için
   */
  public static TimestampHashAlgorithm fromName(String name) {
    if (name == null || name.trim().isEmpty()) {
      return SHA256;
    }
    String normalized = name.trim().toUpperCase().replaceAll("[\\-_\\s]", "");
    for (TimestampHashAlgorithm alg : values()) {
      if (alg.name().equals(normalized)) {
        return alg;
      }
    }
    throw new IllegalArgumentException(
        "Geçersiz hash algoritması: '"
            + name
            + "'. Beklenen: SHA1 | SHA224 | SHA256 | SHA384 | SHA512.");
  }

  /**
   * OID string'inden enum değerini bulur. Eşleşme yoksa {@code null} döner — çağıran taraf OID'i
   * ham haliyle raporlayabilir.
   */
  public static TimestampHashAlgorithm forOid(String oid) {
    if (oid == null) {
      return null;
    }
    for (TimestampHashAlgorithm alg : values()) {
      if (alg.oid.equals(oid)) {
        return alg;
      }
    }
    return null;
  }
}
