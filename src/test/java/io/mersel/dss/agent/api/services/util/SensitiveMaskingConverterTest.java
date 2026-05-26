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
package io.mersel.dss.agent.api.services.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.util.SensitiveMaskingConverter;

import ch.qos.logback.classic.spi.LoggingEvent;

class SensitiveMaskingConverterTest {

  @Test
  void masksJsonPin() {
    SensitiveMaskingConverter c = new SensitiveMaskingConverter();
    LoggingEvent ev = new LoggingEvent();
    ev.setMessage("payload={\"terminalName\":\"t1\",\"pin\":\"1234\",\"certificateId\":\"AB\"}");
    String out = c.convert(ev);
    assertFalse(out.contains("\"1234\""));
    assertEquals(true, out.contains("\"pin\":\"****\""));
  }

  @Test
  void masksKeyValuePin() {
    SensitiveMaskingConverter c = new SensitiveMaskingConverter();
    LoggingEvent ev = new LoggingEvent();
    ev.setMessage("Auth attempt with pin=987654 user=erdem");
    String out = c.convert(ev);
    assertEquals(true, out.contains("pin=****"));
    assertFalse(out.contains("987654"));
  }

  @Test
  void masksPan() {
    SensitiveMaskingConverter c = new SensitiveMaskingConverter();
    LoggingEvent ev = new LoggingEvent();
    ev.setMessage("PAN candidate: 4111111111111111 end.");
    String out = c.convert(ev);
    assertEquals(true, out.contains("411111********1111"));
  }
}
