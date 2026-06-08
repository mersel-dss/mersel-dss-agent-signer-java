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

import java.io.IOException;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.mersel.dss.agent.api.dtos.GetTimestampDto;
import io.mersel.dss.agent.api.dtos.TimestampResponseDto;
import io.mersel.dss.agent.api.dtos.TimestampStatusDto;
import io.mersel.dss.agent.api.dtos.TimestampValidationResponseDto;
import io.mersel.dss.agent.api.dtos.ValidateTimestampDto;
import io.mersel.dss.agent.api.exceptions.TimestampException;
import io.mersel.dss.agent.api.services.timestamp.TimestampProvider;
import io.mersel.dss.agent.api.services.timestamp.TimestampService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Zaman damgası (RFC 3161) uçları.
 *
 * <p>Sunucu kardeşinden farklı olarak TSA sağlayıcısı ve kimlik bilgileri ortam değişkeninden değil
 * her istekte parametre olarak ({@code tsaUrl}, {@code tsUserId}, {@code tsUserPassword}, {@code
 * tubitak}) alınır. TÜBİTAK ESYA dahil her sağlayıcı desteklenir; kimlik bilgileri agent'ta
 * saklanmaz.
 */
@RestController
@Tag(
    name = "Zaman Damgası",
    description = "RFC 3161 zaman damgası alma, doğrulama (TÜBİTAK ESYA dahil).")
public class TimestampController {

  private static final Logger log = LoggerFactory.getLogger(TimestampController.class);

  private final TimestampService timestampService;

  public TimestampController(TimestampService timestampService) {
    this.timestampService = timestampService;
  }

  @Operation(
      summary = "Binary belge için zaman damgası al",
      description =
          "Yüklenen dosya için RFC 3161 zaman damgası alır. TSA bilgileri (tsaUrl, tsUserId,"
              + " tsUserPassword, tubitak) parametre olarak gönderilir. Token binary (.tst) döner;"
              + " metadata HTTP header'larında gelir: X-Timestamp-Time, X-Timestamp-TSA,"
              + " X-Timestamp-Serial, X-Timestamp-Hash-Algorithm, X-Timestamp-Nonce.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                      schema = @Schema(implementation = GetTimestampDto.class))))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Zaman damgası alındı (binary .tst).",
        content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
  })
  @PostMapping(
      value = "/timestamp/get",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<ByteArrayResource> getTimestamp(
      @Parameter(hidden = true) @ModelAttribute GetTimestampDto dto) {

    if (dto.getDocument() == null || dto.getDocument().isEmpty()) {
      throw new IllegalArgumentException("'document' (zaman damgası alınacak dosya) zorunludur.");
    }

    TimestampProvider provider =
        TimestampProvider.fromRequest(
            dto.getTsaUrl(), dto.getTsUserId(), dto.getTsUserPassword(), dto.getTubitak());

    byte[] documentBytes = readBytes(dto.getDocument());
    boolean certReq = dto.getCertReq() == null || dto.getCertReq();
    // Sunucu (DSS OnlineTSPSource) varsayılanı: nonce gönderilmez. Null → false.
    boolean useNonce = dto.getUseNonce() != null && dto.getUseNonce();

    TimestampResponseDto response =
        timestampService.getTimestamp(
            documentBytes, dto.getHashAlgorithm(), provider, certReq, useNonce);

    byte[] tokenBytes = Base64.getDecoder().decode(response.getTimestampToken());
    log.info(
        "Zaman damgası alındı: {} byte, seri {}", tokenBytes.length, response.getSerialNumber());

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"timestamp.tst\"")
        .header("X-Timestamp-Time", nullToEmpty(response.getTimestamp()))
        .header("X-Timestamp-TSA", nullToEmpty(response.getTsaName()))
        .header("X-Timestamp-Serial", nullToEmpty(response.getSerialNumber()))
        .header("X-Timestamp-Hash-Algorithm", nullToEmpty(response.getHashAlgorithm()))
        .header("X-Timestamp-Nonce", nullToEmpty(response.getNonce()))
        .body(new ByteArrayResource(tokenBytes));
  }

  @Operation(
      summary = "Zaman damgasını doğrula",
      description =
          "RFC 3161 zaman damgası token'ını ayrıştırır; TSA sertifika geçerlilik tarihlerini ve"
              + " (orijinal belge verilirse) belge hash'ini doğrular. TSA'ya bağlanmaz.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                      schema = @Schema(implementation = ValidateTimestampDto.class))))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Doğrulama tamamlandı (başarılı veya başarısız olabilir).",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = TimestampValidationResponseDto.class)))
  })
  @PostMapping(
      value = "/timestamp/validate",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TimestampValidationResponseDto> validateTimestamp(
      @Parameter(hidden = true) @ModelAttribute ValidateTimestampDto dto) {

    if (dto.getTimestampToken() == null || dto.getTimestampToken().isEmpty()) {
      throw new IllegalArgumentException(
          "'timestampToken' (doğrulanacak token dosyası) zorunludur.");
    }

    byte[] tokenBytes = readBytes(dto.getTimestampToken());
    byte[] originalBytes = null;
    if (dto.getOriginalDocument() != null && !dto.getOriginalDocument().isEmpty()) {
      originalBytes = readBytes(dto.getOriginalDocument());
    }

    TimestampValidationResponseDto response =
        timestampService.validateTimestamp(tokenBytes, originalBytes);
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Zaman damgası özelliği durumu",
      description =
          "Zaman damgası özelliğinin desteklendiğini bildirir. Agent'ta kimlik bilgileri ortam"
              + " değişkeninden değil her istekte parametre olarak alınır.")
  @GetMapping(value = "/timestamp/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TimestampStatusDto> getStatus() {
    return ResponseEntity.ok(
        new TimestampStatusDto(
            true,
            "PARAMETER",
            "Zaman damgası destekleniyor. TSA adresi ve kimlik bilgilerini her istekte parametre"
                + " olarak gönderin (tsaUrl, tsUserId, tsUserPassword, tubitak)."));
  }

  private static byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new TimestampException("Yüklenen dosya okunamadı: " + e.getMessage(), e);
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
