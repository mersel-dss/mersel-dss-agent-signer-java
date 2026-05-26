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
package io.mersel.dss.agent.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mersel.dss.agent.api.dtos.EInvoiceGibApplicationDto;

import tr.com.cs.imz.websocket.model.EFaturaBasvuruFormIstek;

/**
 * GİB başvuru formunun DTO → {@link EFaturaBasvuruFormIstek} map'lemesi.
 *
 * <p>Mevcut zorunlu alanlar (telefon, adres, email, kanuni merkez) geri uyumluluk için aynı şekilde
 * set'lenir; eklenen opsiyonel alanlar verilirse forma yazılır, verilmezse boş string ile geçilir
 * (eski projedeki davranış).
 */
class GibApplicationControllerBuildFormTest {

  private final GibApplicationController controller =
      new GibApplicationController(null, null, new ObjectMapper());

  @Test
  void corporateMinimalDtoFillsRequiredAndDefaultsOptionals() {
    EInvoiceGibApplicationDto dto = baseDto();

    EFaturaBasvuruFormIstek form = controller.buildForm(dto, "1234567890", "ACME LTD ŞTİ", false);

    assertThat(form.getVkn()).isEqualTo("1234567890");
    assertThat(form.getUnvan()).isEqualTo("ACME LTD ŞTİ");
    assertThat(form.getTelNo()).isEqualTo(dto.getPhoneNumber());
    assertThat(form.getAdres()).isEqualTo(dto.getAddress());
    assertThat(form.getePosta()).isEqualTo(dto.getEmail());
    assertThat(form.getKanuniMerkezi()).isEqualTo(dto.getCompanyHeadquarters());

    // Opsiyonel alanlar verilmediği için eski davranış: boş string.
    assertThat(form.getTicaretSicilNo()).isEmpty();
    assertThat(form.getTicaretSicilMemurlugu()).isEmpty();
    assertThat(form.getKurulusTarihi()).isEmpty();
    assertThat(form.getBagliBulunduguOda()).isEmpty();
    assertThat(form.getOdaSicilNo()).isEmpty();
    assertThat(form.getSorumluTckn()).isEmpty();
    assertThat(form.getSorumluAd()).isEmpty();
    assertThat(form.getSorumluSoyad()).isEmpty();
    assertThat(form.getSorumluCepTel()).isEmpty();
    assertThat(form.getSorumluEPosta()).isEmpty();
    assertThat(form.getWebSitesi()).isEmpty();
    assertThat(form.getFax()).isEmpty();
    assertThat(form.getMaliMuhurIstedi()).isZero();
  }

  @Test
  void corporateFullDtoMapsAllOptionalFields() {
    EInvoiceGibApplicationDto dto = baseDto();
    dto.setTradeRegistryNo("123456");
    dto.setTradeRegistryOffice("İSTANBUL TİCARET SİCİL MEMURLUĞU");
    dto.setFoundationDate("2010-04-15");
    dto.setChamberName("İSTANBUL TİCARET ODASI");
    dto.setChamberRegistryNo("987654");
    dto.setWebsite("https://acme.example");
    dto.setFax("+902120000000");
    dto.setResponsibleTckn("12345678901");
    dto.setResponsibleFirstName("AYŞE");
    dto.setResponsibleLastName("YILDIRIM");
    dto.setResponsibleMobilePhone("+905320000000");
    dto.setResponsibleEmail("[email protected]");
    dto.setRequestsFinancialSeal(1);

    EFaturaBasvuruFormIstek form = controller.buildForm(dto, "1234567890", "ACME LTD ŞTİ", false);

    assertThat(form.getTicaretSicilNo()).isEqualTo("123456");
    assertThat(form.getTicaretSicilMemurlugu()).isEqualTo("İSTANBUL TİCARET SİCİL MEMURLUĞU");
    assertThat(form.getKurulusTarihi()).isEqualTo("2010-04-15");
    assertThat(form.getBagliBulunduguOda()).isEqualTo("İSTANBUL TİCARET ODASI");
    assertThat(form.getOdaSicilNo()).isEqualTo("987654");
    assertThat(form.getWebSitesi()).isEqualTo("https://acme.example");
    assertThat(form.getFax()).isEqualTo("+902120000000");
    assertThat(form.getSorumluTckn()).isEqualTo("12345678901");
    assertThat(form.getSorumluAd()).isEqualTo("AYŞE");
    assertThat(form.getSorumluSoyad()).isEqualTo("YILDIRIM");
    assertThat(form.getSorumluCepTel()).isEqualTo("+905320000000");
    assertThat(form.getSorumluEPosta()).isEqualTo("[email protected]");
    assertThat(form.getMaliMuhurIstedi()).isEqualTo(1);
  }

  @Test
  void personSubjectIsSplitIntoFirstAndLastName() {
    EInvoiceGibApplicationDto dto = baseDto();

    EFaturaBasvuruFormIstek form =
        controller.buildForm(dto, "12345678901", "AYŞE GÜL YILDIRIM", true);

    assertThat(form.getTckn()).isEqualTo("12345678901");
    assertThat(form.getAd()).isEqualTo("AYŞE GÜL");
    assertThat(form.getSoyad()).isEqualTo("YILDIRIM");
  }

  @Test
  void personRespondentOverrideTakesPrecedence() {
    EInvoiceGibApplicationDto dto = baseDto();
    dto.setResponsibleFirstName("MEHMET");
    dto.setResponsibleLastName("KARA");
    dto.setResponsibleMobilePhone("+905300000000");

    EFaturaBasvuruFormIstek form = controller.buildForm(dto, "12345678901", "AYŞE YILDIRIM", true);

    assertThat(form.getAd()).isEqualTo("MEHMET");
    assertThat(form.getSoyad()).isEqualTo("KARA");
    assertThat(form.getSorumluCepTel()).isEqualTo("+905300000000");
  }

  @Test
  void blankResponsibleNamesFallBackToCertificateValues() {
    EInvoiceGibApplicationDto dto = baseDto();
    dto.setResponsibleFirstName("   ");
    dto.setResponsibleLastName("");

    EFaturaBasvuruFormIstek form = controller.buildForm(dto, "12345678901", "AYŞE YILDIRIM", true);

    assertThat(form.getAd()).isEqualTo("AYŞE");
    assertThat(form.getSoyad()).isEqualTo("YILDIRIM");
  }

  private static EInvoiceGibApplicationDto baseDto() {
    EInvoiceGibApplicationDto dto = new EInvoiceGibApplicationDto();
    dto.setPhoneNumber("+902120000000");
    dto.setAddress("Maslak Mahallesi, İstanbul");
    dto.setEmail("[email protected]");
    dto.setCompanyHeadquarters("İSTANBUL/SARIYER");
    dto.setTerminalName("Test Reader 0");
    dto.setPin("123456");
    dto.setCertificateId("DEADBEEF");
    return dto;
  }
}
