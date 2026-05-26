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

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * XAdES imzalama akışında istemcinin gönderdiği içerik tipi.
 *
 * <p>JSON / form-data deserializasyonu {@link #fromString(String)} ile <em>case-insensitive</em>
 * yapılır: {@code XmlDocument}, {@code xmlDocument}, {@code XML_DOCUMENT}, {@code xml-document}
 * gibi varyasyonların hepsi kabul edilir. Wire değerleri ({@code XmlDocument}, {@code
 * HrXmlCounterSignature}) OpenAPI schema'da bu hâliyle görünür.
 */
public enum XmlContentType {
  /** Düz XML belgesi (UBL fatura vb.) — referans hash + signed properties. */
  XmlDocument,

  /**
   * Var olan imzalı XML belgesi üzerine ETSI XAdES counter-signature ekler — İK / kurum içi sarmal
   * imzalı XML akışları için.
   */
  HrXmlCounterSignature;

  /** Jackson + Spring converter için case-insensitive parser. */
  @JsonCreator
  public static XmlContentType fromString(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().replaceAll("[_\\-\\s]", "");
    for (XmlContentType v : values()) {
      if (v.name().equalsIgnoreCase(normalized)) {
        return v;
      }
    }
    throw new IllegalArgumentException(
        "Geçersiz XmlContentType: '" + value + "'. Beklenen: XmlDocument | HrXmlCounterSignature.");
  }
}
