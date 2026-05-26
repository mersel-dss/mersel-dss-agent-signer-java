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

import java.io.ByteArrayOutputStream;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.services.signature.PadesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** PDF belgelerini PAdES-B (CADES) ile imzalar. */
@RestController
@Tag(name = "PAdES İmza", description = "PDF belgelerini PAdES-B ile imzalar.")
public class PadesController {

  private static final Logger log = LoggerFactory.getLogger(PadesController.class);

  private final PadesService padesService;

  public PadesController(PadesService padesService) {
    this.padesService = padesService;
  }

  @Operation(
      summary = "PDF belgesini PAdES-B (CADES) ile imzalar.",
      description =
          "Multipart form-data ile PDF yükleyin. Sertifika seçimi için önce"
              + " `GET /smartcard/certificate` çağırın ve dönen sertifikalardan `recommended=true`"
              + " olan veya `purpose=SIGNING` + `eligibleForSignature=true` olan birinin `id`"
              + " alanını `certificateId` olarak gönderin.")
  @PostMapping(value = "/pades/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ByteArrayResource> sign(@Valid @ModelAttribute SignDocumentDto dto) {

    if (dto.getContent() == null || dto.getContent().isEmpty()) {
      throw new IllegalArgumentException("'content' (PDF dosyası) zorunludur.");
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    padesService.sign(dto, out);
    byte[] signed = out.toByteArray();

    log.info("PAdES imzalama tamamlandı: {} byte", signed.length);
    String fileName = "signed-" + System.currentTimeMillis() + ".pdf";
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .body(new ByteArrayResource(signed));
  }
}
