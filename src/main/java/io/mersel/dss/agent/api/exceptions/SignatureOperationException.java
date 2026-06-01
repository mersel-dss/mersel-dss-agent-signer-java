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
 * İmzalama akışı içinde (DOM canonicalization, iText stamping, OkHttp, XAdES production) yakalanan
 * beklenmedik teknik hata.
 *
 * <p>Default error code {@code SIGNATURE_FAILED}. Algoritma uyumsuzluğu gibi alt-tipler için {@code
 * SignatureOperationException(errorCode, message, cause)} ctor'ı kullanılabilir; örnek: {@code
 * SIGNATURE_ALGORITHM_UNSUPPORTED}.
 */
public class SignatureOperationException extends SignerException {
  private static final long serialVersionUID = 1L;

  /** İmzalama akışı genel hatası. */
  public static final String CODE_FAILED = "SIGNATURE_FAILED";

  /**
   * Token imzalama mekanizmasını desteklemiyor (ör. {@code CKR_MECHANISM_INVALID} veya "Unsupported
   * parameters"). Frontend bu kodu görürse kullanıcıya kart firmware güncelleme veya fallback
   * algoritma denemesi yönlendirmesi göstermelidir.
   */
  public static final String CODE_ALGORITHM_UNSUPPORTED = "SIGNATURE_ALGORITHM_UNSUPPORTED";

  public SignatureOperationException(String message) {
    super(CODE_FAILED, message);
  }

  public SignatureOperationException(String message, Throwable cause) {
    super(CODE_FAILED, message, cause);
  }

  public SignatureOperationException(String errorCode, String message, Throwable cause) {
    super(errorCode == null ? CODE_FAILED : errorCode, message, cause);
  }
}
