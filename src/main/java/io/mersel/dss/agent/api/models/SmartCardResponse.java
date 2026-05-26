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
package io.mersel.dss.agent.api.models;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /smartcard} dönüş tipi.
 *
 * <p>Eski projedeki ({@code SmartCard(osName, osVersion, osArch, cards[])}) host metadata parametre
 * paritesi korunur — frontend platforma özgü vendor lib önerisi (örn. macOS .dylib vs Windows .dll)
 * ya da arch-specific dropdown sıralaması için bu alanları okuyabilir. JSON sızıntısı minimal:
 * alanlar {@code @JsonInclude(NON_NULL)} ile boş geldiğinde gizlenir.
 */
@Schema(description = "Sistemde algılanan akıllı kartların listesi + host ortam bilgisi")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmartCardResponse {

  @Schema(description = "İşletim sistemi adı (örn. \"Mac OS X\", \"Windows 11\", \"Linux\").")
  private String osName;

  @Schema(description = "İşletim sistemi sürümü (örn. \"14.5\", \"10.0\", \"6.6.0-pardus\").")
  private String osVersion;

  @Schema(description = "İşletim sistemi mimarisi (örn. \"x86_64\", \"aarch64\").")
  private String osArch;

  @Schema(description = "Servisin çalıştığı JRE sürümü (örn. \"1.8.0_392\").")
  private String javaVersion;

  @Schema(description = "Algılanan kartlar.")
  private List<SmartCardDetail> cards = new ArrayList<SmartCardDetail>();

  public SmartCardResponse() {}

  public SmartCardResponse(List<SmartCardDetail> cards) {
    this.cards = cards == null ? new ArrayList<SmartCardDetail>() : cards;
  }

  /**
   * Mevcut JVM'in {@code System.getProperty(...)} çıktısı üzerinden host metadata'yı set eder.
   * Test/diagnostic için ayrı set'ler ile dolu sürüm tercih edilebilir; controller burayı çağırır.
   */
  public SmartCardResponse withCurrentHost() {
    this.osName = System.getProperty("os.name");
    this.osVersion = System.getProperty("os.version");
    this.osArch = System.getProperty("os.arch");
    this.javaVersion = System.getProperty("java.version");
    return this;
  }

  public String getOsName() {
    return osName;
  }

  public void setOsName(String osName) {
    this.osName = osName;
  }

  public String getOsVersion() {
    return osVersion;
  }

  public void setOsVersion(String osVersion) {
    this.osVersion = osVersion;
  }

  public String getOsArch() {
    return osArch;
  }

  public void setOsArch(String osArch) {
    this.osArch = osArch;
  }

  public String getJavaVersion() {
    return javaVersion;
  }

  public void setJavaVersion(String javaVersion) {
    this.javaVersion = javaVersion;
  }

  public List<SmartCardDetail> getCards() {
    return cards;
  }

  public void setCards(List<SmartCardDetail> cards) {
    this.cards = cards;
  }
}
