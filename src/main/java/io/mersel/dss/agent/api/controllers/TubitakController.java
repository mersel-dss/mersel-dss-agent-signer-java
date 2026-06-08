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

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.mersel.dss.agent.api.dtos.TubitakCreditRequestDto;
import io.mersel.dss.agent.api.dtos.TubitakCreditResponseDto;
import io.mersel.dss.agent.api.services.timestamp.TimestampProvider;
import io.mersel.dss.agent.api.services.timestamp.tubitak.TubitakCreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * TÜBİTAK ESYA zaman damgası servisine özel işlemler.
 *
 * <p>Kimlik bilgileri (müşteri no, parola, sunucu adresi) ortam değişkeninden değil her istekte
 * parametre olarak alınır.
 */
@RestController
@Tag(name = "TÜBİTAK", description = "TÜBİTAK ESYA zaman damgası kontör sorgulama.")
public class TubitakController {

  private static final Logger log = LoggerFactory.getLogger(TubitakController.class);

  private final TubitakCreditService tubitakCreditService;

  public TubitakController(TubitakCreditService tubitakCreditService) {
    this.tubitakCreditService = tubitakCreditService;
  }

  @Operation(
      summary = "TÜBİTAK zaman damgası kontör bilgisini sorgular",
      description =
          "TÜBİTAK ESYA için kalan kontör miktarını döndürür. Müşteri no / parola / sunucu adresi"
              + " parametre olarak gönderilir.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                      schema = @Schema(implementation = TubitakCreditRequestDto.class))))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Kontör bilgisi sorgulandı.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = TubitakCreditResponseDto.class)))
  })
  @PostMapping(
      value = "/tubitak/credit",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TubitakCreditResponseDto> getCreditInfo(
      @Parameter(hidden = true) @Valid @ModelAttribute TubitakCreditRequestDto dto) {

    log.info("TÜBİTAK kontör sorgulama isteği alındı.");

    TimestampProvider provider =
        TimestampProvider.fromRequest(
            dto.getTsaUrl(), dto.getTsUserId(), dto.getTsUserPassword(), dto.getTubitak());

    TubitakCreditResponseDto creditInfo = tubitakCreditService.checkCredit(provider);
    log.info("TÜBİTAK kontör sorgulaması başarılı. Kalan kontör: {}", creditInfo.getRemainingCredit());
    return ResponseEntity.ok(creditInfo);
  }
}
