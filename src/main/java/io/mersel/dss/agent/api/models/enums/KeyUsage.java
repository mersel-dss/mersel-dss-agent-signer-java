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

/**
 * RFC 5280 §4.2.1.3 KeyUsage extension bit'leri (OID {@code 2.5.29.15}).
 *
 * <p>{@code X509Certificate.getKeyUsage()} JDK API'si 9 elemanlı bir boolean dizisi döner; bu enum
 * her bir bit'i tipli temsil eder. Sertifika hangi amaç için kullanılabilir bilgisini taşır.
 */
public enum KeyUsage {
  /** Bit 0: digitalSignature — genel imzalama (PAdES/XAdES için kritik). */
  DIGITAL_SIGNATURE(0),
  /** Bit 1: nonRepudiation / contentCommitment — inkâr edilemez imza (QES için kritik). */
  NON_REPUDIATION(1),
  /** Bit 2: keyEncipherment — anahtar şifreleme (TLS RSA key exchange, S/MIME). */
  KEY_ENCIPHERMENT(2),
  /** Bit 3: dataEncipherment — doğrudan veri şifreleme. */
  DATA_ENCIPHERMENT(3),
  /** Bit 4: keyAgreement — Diffie-Hellman / ECDH anahtar anlaşması. */
  KEY_AGREEMENT(4),
  /** Bit 5: keyCertSign — başka sertifikaları imzalama (CA). */
  KEY_CERT_SIGN(5),
  /** Bit 6: cRLSign — CRL imzalama (CA / CRL issuer). */
  CRL_SIGN(6),
  /** Bit 7: encipherOnly — sadece şifreleme tarafında (keyAgreement ile). */
  ENCIPHER_ONLY(7),
  /** Bit 8: decipherOnly — sadece deşifreleme tarafında (keyAgreement ile). */
  DECIPHER_ONLY(8);

  private final int bit;

  KeyUsage(int bit) {
    this.bit = bit;
  }

  /** Bu enum değerinin RFC 5280'deki bit index'i. */
  public int bit() {
    return bit;
  }
}
