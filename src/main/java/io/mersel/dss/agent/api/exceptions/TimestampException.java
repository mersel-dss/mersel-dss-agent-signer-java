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
 * Zaman damgası (RFC 3161 / TÜBİTAK ESYA) işlemleri başarısız olduğunda fırlatılan exception.
 *
 * <p>TSA bağlantı sorunları, kimlik doğrulama hataları (yanlış müşteri no / parola), kontör
 * tükenmesi ve geçersiz hash algoritması gibi durumları kapsar. {@code TIMESTAMP_ERROR} kodu ile
 * {@link io.mersel.dss.agent.api.GlobalExceptionHandler} tarafından HTTP 500'e eşlenir.
 */
public class TimestampException extends SignerException {

  private static final long serialVersionUID = 1L;

  public TimestampException(String message) {
    super("TIMESTAMP_ERROR", message);
  }

  public TimestampException(String message, Throwable cause) {
    super("TIMESTAMP_ERROR", message, cause);
  }
}
