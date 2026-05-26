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
 * RFC 5280 §4.2.1.12 ExtendedKeyUsage (EKU) extension'ı için bilinen OID'lerin enum'u.
 *
 * <p>EKU genellikle anahtar amacını KeyUsage'tan daha açık ifade eder (örn. TLS client cert, e-mail
 * imzalama, time-stamping). Bilinmeyen OID'ler {@link Optional#empty()} döner; üst katman raw OID
 * stringini ayrı bir set'te tutmalıdır.
 */
public enum ExtendedKeyUsage {
  /** 1.3.6.1.5.5.7.3.1 — TLS server authentication. */
  SERVER_AUTH("1.3.6.1.5.5.7.3.1"),
  /** 1.3.6.1.5.5.7.3.2 — TLS client authentication. */
  CLIENT_AUTH("1.3.6.1.5.5.7.3.2"),
  /** 1.3.6.1.5.5.7.3.3 — code signing. */
  CODE_SIGNING("1.3.6.1.5.5.7.3.3"),
  /** 1.3.6.1.5.5.7.3.4 — e-mail protection (S/MIME). */
  EMAIL_PROTECTION("1.3.6.1.5.5.7.3.4"),
  /** 1.3.6.1.5.5.7.3.8 — time stamping (TSA). */
  TIME_STAMPING("1.3.6.1.5.5.7.3.8"),
  /** 1.3.6.1.5.5.7.3.9 — OCSP signing. */
  OCSP_SIGNING("1.3.6.1.5.5.7.3.9"),
  /** 1.3.6.1.4.1.311.10.3.12 — Microsoft document signing (Office, PDF). */
  DOCUMENT_SIGNING("1.3.6.1.4.1.311.10.3.12");

  private final String oid;

  ExtendedKeyUsage(String oid) {
    this.oid = oid;
  }

  public String oid() {
    return oid;
  }

  /** OID stringinden enum eşleştirmesi; bilinmiyorsa {@link Optional#empty()}. */
  public static Optional<ExtendedKeyUsage> fromOid(String oid) {
    if (oid == null) {
      return Optional.empty();
    }
    String trimmed = oid.trim();
    for (ExtendedKeyUsage v : values()) {
      if (v.oid.equals(trimmed)) {
        return Optional.of(v);
      }
    }
    return Optional.empty();
  }
}
