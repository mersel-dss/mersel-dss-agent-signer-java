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
package io.mersel.dss.agent.api.util;

import java.util.regex.Pattern;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback {@code %m} converter'ının PIN/PAN maskelenmiş varyantı.
 *
 * <p>{@code logback-spring.xml} içinde {@code %msg} yerine {@code %mskmsg} kullanılır; aşağıdaki
 * kalıplar değiştirilir:
 *
 * <ul>
 *   <li>{@code "pin":"1234"} → {@code "pin":"****"}
 *   <li>{@code pin=1234} → {@code pin=****}
 *   <li>13–19 haneli ardışık rakam grupları (PAN şüphesi) → ilk 6 + son 4 hariç maskeli
 * </ul>
 *
 * <p>Bu sınıf yalnızca ek bir güvenlik katmanıdır; PIN'in logback'e <em>hiç</em> gönderilmemesi
 * asıl hedeftir.
 */
public class SensitiveMaskingConverter extends MessageConverter {

  private static final Pattern PIN_JSON = Pattern.compile("(?i)(\"pin\"\\s*:\\s*\")[^\"]+(\")");
  private static final Pattern PIN_KV = Pattern.compile("(?i)(\\bpin\\s*[=:]\\s*)([^,\\s\\}\\]]+)");
  // 13–19 ardışık rakam: PAN şüphesi
  private static final Pattern PAN = Pattern.compile("\\b(\\d{6})(\\d{3,9})(\\d{4})\\b");

  @Override
  public String convert(ILoggingEvent event) {
    String msg = super.convert(event);
    if (msg == null || msg.isEmpty()) return msg;

    String out = PIN_JSON.matcher(msg).replaceAll("$1****$2");
    out = PIN_KV.matcher(out).replaceAll("$1****");
    out = PAN.matcher(out).replaceAll("$1********$3");
    return out;
  }
}
