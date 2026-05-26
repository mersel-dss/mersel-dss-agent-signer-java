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
 * Sertifikanın iş amacı — KeyUsage + ExtendedKeyUsage bit'lerinden türetilir.
 *
 * <p>Akıllı kartlar genelde aynı anda hem imza ({@code *SIGN0}) hem şifreleme ({@code *ENCR0})
 * sertifikası taşır. Frontend hangisinin imzalama için kullanılması gerektiğini bu enum üzerinden
 * filtreler; {@code CertificateInspector.purpose(X509Certificate)} hesaplar.
 */
public enum CertificatePurpose {
  /**
   * PAdES/XAdES imzalama için uygun — digital signature ve/veya non-repudiation, encipherment yok.
   */
  SIGNING,
  /** Sadece şifreleme — keyEncipherment / dataEncipherment, imza bit'i yok. */
  ENCRYPTION,
  /** TLS auth — digital signature + EKU clientAuth/serverAuth. */
  AUTHENTICATION,
  /** Çift kullanım — bazı kartlar tek bir cert ile hem imza hem şifreleme yapar. */
  MIXED,
  /** CA, CRL signer, OCSP, kullanıcı tarafında geçersiz veya bilinmeyen. */
  OTHER
}
