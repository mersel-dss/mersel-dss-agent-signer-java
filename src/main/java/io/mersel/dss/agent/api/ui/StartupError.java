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
package io.mersel.dss.agent.api.ui;

/**
 * Başlatma (Spring Boot {@code app.run()}) sırasında oluşan bir hatanın kullanıcıya gösterilebilir
 * hâli. {@link StartupErrorClassifier} tarafından üretilir, {@link StartupErrorWindow} tarafından
 * render edilir.
 *
 * <p>Üç katman taşır:
 *
 * <ul>
 *   <li>{@code headline} — kısa, insan dostu başlık ("Uygulama Zaten Çalışıyor").
 *   <li>{@code message} — kullanıcıya ne olduğunu ve ne yapması gerektiğini anlatan birkaç cümle.
 *   <li>{@code details} — destek/operatör için cause zinciri / teknik döküm (kopyalanabilir).
 * </ul>
 */
public final class StartupError {

  /** Hatanın sınıfı; pencere ikon/renk seçiminde ve testlerde ayrım için kullanılır. */
  public enum Kind {
    /**
     * İlgili port işletim sisteminde zaten dinleniyor (büyük olasılıkla başka bir örnek çalışıyor).
     */
    PORT_IN_USE,
    /** Sınıflandırılamayan diğer tüm başlatma hataları. */
    GENERIC
  }

  private final Kind kind;
  private final String headline;
  private final String message;
  private final String details;

  public StartupError(Kind kind, String headline, String message, String details) {
    this.kind = kind == null ? Kind.GENERIC : kind;
    this.headline = headline;
    this.message = message;
    this.details = details;
  }

  public Kind getKind() {
    return kind;
  }

  public String getHeadline() {
    return headline;
  }

  public String getMessage() {
    return message;
  }

  public String getDetails() {
    return details;
  }
}
