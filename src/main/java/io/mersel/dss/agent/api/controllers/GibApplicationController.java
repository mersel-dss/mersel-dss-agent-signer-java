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
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mersel.dss.agent.api.dtos.EInvoiceGibApplicationDto;
import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.models.CertificateResponse;
import io.mersel.dss.agent.api.models.GibApplicationQueryResponse;
import io.mersel.dss.agent.api.models.GibApplicationResponse;
import io.mersel.dss.agent.api.services.certificate.CertificateListingService;
import io.mersel.dss.agent.api.services.signature.PadesService;
import io.mersel.dss.agent.api.util.InMemoryMultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import tr.com.cs.imz.websocket.PDFUtil;
import tr.com.cs.imz.websocket.impl.ServletURLImpl;
import tr.com.cs.imz.websocket.model.EFaturaBasvuruFormIstek;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** GİB e-Fatura başvuru uçları. */
@RestController
@Tag(name = "GİB Başvuru", description = "GİB e-Fatura başvuru sorgulama ve gönderme.")
public class GibApplicationController {

  private static final Logger log = LoggerFactory.getLogger(GibApplicationController.class);

  private static final String GIB_BASE = "https://ebelgebasvuru.gib.gov.tr/api/v1/efaturabasvuru";
  private static final String SORGULA_URL = GIB_BASE + "/basvuruSorgula";
  private static final String ESIGN_URL = GIB_BASE + "/basvuru/esign";

  private static final MediaType MEDIA_JSON = MediaType.parse("application/json");

  /** Özel Entegratör — eski projedeki sabit. */
  private static final int BASVURU_TIPI_OZEL_ENTEGRATOR = 2;

  private final CertificateListingService certificateListingService;
  private final PadesService padesService;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;

  public GibApplicationController(
      CertificateListingService certificateListingService,
      PadesService padesService,
      ObjectMapper objectMapper) {
    this.certificateListingService = certificateListingService;
    this.padesService = padesService;
    this.objectMapper = objectMapper;
    this.httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
  }

  @Operation(summary = "GİB e-Fatura başvurusunu VKN/TCKN ile sorgular.")
  @GetMapping("/gibApplication")
  public ResponseEntity<GibApplicationQueryResponse> query(
      @Parameter(description = "Mükellef VKN (10 hane) veya TCKN (11 hane).", required = true)
          @RequestParam("taxId")
          String taxId)
      throws IOException {

    String trimmed = taxId == null ? "" : taxId.trim();
    if (trimmed.length() != 10 && trimmed.length() != 11) {
      throw new IllegalArgumentException(
          "taxId 10 (VKN) veya 11 (TCKN) haneli olmalı, alınan: " + trimmed.length());
    }

    String jsonBody = objectMapper.writeValueAsString(Collections.singletonMap("vknTckn", trimmed));
    Request request =
        new Request.Builder()
            .url(SORGULA_URL)
            .post(okhttp3.RequestBody.create(jsonBody, MEDIA_JSON))
            .addHeader("Content-Type", "application/json")
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      ResponseBody body = response.body();
      String payload = body == null ? "" : body.string();
      log.debug(
          "GIB basvuruSorgula yanıt: status={}, contentType={}, body={}",
          response.code(),
          body == null ? "<null>" : String.valueOf(body.contentType()),
          payload);

      GibApplicationQueryResponse parsed = parseQueryResponse(payload, response.isSuccessful());
      if (!response.isSuccessful()) {
        log.warn(
            "GIB basvuruSorgula HTTP başarısız: status={}, message={}",
            response.code(),
            parsed.getMessage());
      }
      return ResponseEntity.status(HttpStatus.OK).body(parsed);
    }
  }

  /**
   * GİB {@code basvuruSorgula} ucu **iki farklı response shape** dönebilir:
   *
   * <ol>
   *   <li>Başarılı durumda obje: {@code {"message":"...","status":"...","result":"..."}}
   *   <li>Validasyon/arama hatasında JSON-encoded string: {@code "VKN/TCKN bulunamadı"}
   * </ol>
   *
   * <p>Eski projede Gson doğrudan obje deserialization denediği için ikinci durumda {@code Expected
   * BEGIN_OBJECT but was STRING} ile 500 patlıyordu. Burada {@link JsonNode} üzerinden tipi tespit
   * edip her iki shape'i de yumuşak şekilde {@link GibApplicationQueryResponse}'a çeviriyoruz; raw
   * payload her durumda {@code rawResponse}'a yazılarak tanıya yardımcı oluyor.
   */
  GibApplicationQueryResponse parseQueryResponse(String payload, boolean httpSuccessful) {
    GibApplicationQueryResponse fallback = new GibApplicationQueryResponse();
    fallback.setRawResponse(payload);
    fallback.setSuccess(false);

    if (payload == null || payload.isEmpty()) {
      fallback.setStatus("ERROR");
      fallback.setMessage("GİB sunucusu boş yanıt döndü.");
      return fallback;
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(payload);
    } catch (JsonProcessingException ex) {
      log.warn("GIB yanıtı JSON olarak parse edilemedi, raw payload mesaja yazılıyor.", ex);
      fallback.setStatus("ERROR");
      fallback.setMessage(payload);
      return fallback;
    }

    if (root.isTextual()) {
      // GIB hata durumunda düz string döndü — örn. "VKN/TCKN bulunamadı". HTTP başarılı dönmüş
      // olsa bile bu shape semantik olarak ERROR'a denk geliyor.
      fallback.setStatus("ERROR");
      fallback.setMessage(root.asText());
      return fallback;
    }

    if (root.isObject()) {
      try {
        GibApplicationQueryResponse parsed =
            objectMapper.treeToValue(root, GibApplicationQueryResponse.class);
        parsed.setRawResponse(payload);

        // success kararı: GİB otoritedir. Yanıtta `success` alanı varsa Jackson onu zaten
        // parse etti — biz dokunmuyoruz. Yoksa status-heuristic fallback'i uygulanır. HTTP
        // katmanı başarısızsa her halükârda false'a zorlanır.
        if (!httpSuccessful) {
          parsed.setSuccess(false);
        } else if (!root.has("success")) {
          parsed.setSuccess(
              parsed.getStatus() == null
                  || "OK".equalsIgnoreCase(parsed.getStatus())
                  || "SUCCESS".equalsIgnoreCase(parsed.getStatus()));
        }

        if (parsed.getStatus() == null) {
          parsed.setStatus(parsed.isSuccess() ? "OK" : "ERROR");
        }
        return parsed;
      } catch (JsonProcessingException ex) {
        log.warn("GIB JSON object'i model'e map edilemedi, raw payload mesaja yazılıyor.", ex);
        fallback.setStatus("ERROR");
        fallback.setMessage(payload);
        return fallback;
      }
    }

    fallback.setStatus("ERROR");
    fallback.setMessage("Beklenmeyen yanıt tipi: " + root.getNodeType());
    return fallback;
  }

  @Operation(
      summary = "GİB e-Fatura başvurusunu akıllı kart ile imzalayıp gönderir.",
      description =
          "Başvuru formunu PDF olarak üretir, PAdES (CADES) ile imzalar ve GİB başvuru sunucusuna"
              + " iletir.")
  @PostMapping("/gibApplication")
  public ResponseEntity<GibApplicationResponse> apply(
      @Valid @RequestBody EInvoiceGibApplicationDto body) throws Exception {

    // 1) Kart üzerinden sertifika listesini al ve seçileni bul. Listeleme PIN'siz (no-login)
    // çalışır — PIN yalnız aşağıdaki PAdES imzalama adımında kullanılır.
    List<CertificateResponse> certs =
        certificateListingService.listCertificates(
            body.getTerminalName(), body.getPkcs11LibraryPath());

    CertificateResponse selected =
        certs.stream()
            .filter(c -> body.getCertificateId().equalsIgnoreCase(c.getId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Belirtilen sertifika kartta bulunamadı: " + body.getCertificateId()));

    String partyIdentification = selected.getTaxId(); // VKN (10) veya TCKN (11)
    String certificateSubject = selected.getSubject();
    boolean isPerson = partyIdentification != null && partyIdentification.length() == 11;

    // 2) GİB başvuru formunu doldur.
    EFaturaBasvuruFormIstek form =
        buildForm(body, partyIdentification, certificateSubject, isPerson);

    // 3) PDF üret.
    String fileName = UUID.randomUUID().toString();
    byte[] pdfBytes = PDFUtil.createPdf(form, fileName);

    // 4) PDF'i akıllı kart ile PAdES olarak imzala.
    SignDocumentDto signRequest = new SignDocumentDto();
    signRequest.setContent(
        new InMemoryMultipartFile("basvuru.pdf", "basvuru.pdf", "application/pdf", pdfBytes));
    signRequest.setTerminalName(body.getTerminalName());
    signRequest.setPin(body.getPin());
    signRequest.setCertificateId(body.getCertificateId());
    signRequest.setPkcs11LibraryPath(body.getPkcs11LibraryPath());

    ByteArrayOutputStream signedPdf = new ByteArrayOutputStream();
    padesService.sign(signRequest, signedPdf);

    // 5) İmzalı PDF'i GİB başvuru servisine yolla.
    ServletURLImpl servlet = new ServletURLImpl(ESIGN_URL);
    LinkedList<String> reply = servlet.writeObjects(form, fileName, signedPdf.toByteArray());

    return ResponseEntity.ok(mapReply(reply));
  }

  /**
   * GİB başvuru formunu doldurur. DTO'daki opsiyonel alanlar verilmemişse boş string ile geçilir
   * (eski projedeki davranış: {@code form.setX("")}). Sorumlu kişi ad/soyad için ayrı override
   * yoksa sertifika subject'inden türetilen değer kullanılır — böylece tüzel kişi başvurusu sorumlu
   * detayı vermese bile GİB tarafı boş alanlardan complain etmez.
   */
  EFaturaBasvuruFormIstek buildForm(
      EInvoiceGibApplicationDto body,
      String partyIdentification,
      String certificateSubject,
      boolean isPerson) {
    EFaturaBasvuruFormIstek form = new EFaturaBasvuruFormIstek();
    form.setBasvuruTipi(BASVURU_TIPI_OZEL_ENTEGRATOR);
    form.setTelNo(body.getPhoneNumber());
    form.setAdres(body.getAddress());
    form.setFax(nullSafe(body.getFax()));
    form.setePosta(body.getEmail());
    form.setWebSitesi(nullSafe(body.getWebsite()));
    form.setKanuniMerkezi(body.getCompanyHeadquarters());
    form.setMaliMuhurIstedi(
        body.getRequestsFinancialSeal() == null ? 0 : body.getRequestsFinancialSeal());

    if (!isPerson) {
      form.setVkn(partyIdentification);
      form.setUnvan(certificateSubject);
      form.setTicaretSicilNo(nullSafe(body.getTradeRegistryNo()));
      form.setTicaretSicilMemurlugu(nullSafe(body.getTradeRegistryOffice()));
      form.setKurulusTarihi(nullSafe(body.getFoundationDate()));
      form.setBagliBulunduguOda(nullSafe(body.getChamberName()));
      form.setOdaSicilNo(nullSafe(body.getChamberRegistryNo()));
      form.setSorumluTckn(nullSafe(body.getResponsibleTckn()));
      form.setSorumluAd(nullSafe(body.getResponsibleFirstName()));
      form.setSorumluSoyad(nullSafe(body.getResponsibleLastName()));
      form.setSorumluCepTel(nullSafe(body.getResponsibleMobilePhone()));
      form.setSorumluEPosta(nullSafe(body.getResponsibleEmail()));
    } else {
      form.setTckn(partyIdentification);
      String[] split = splitFullName(certificateSubject);
      // Tüzel kişi başvurusu değilse de sorumlu detayı override edilebilsin: birincil
      // ad/soyad sertifikadan, DTO override'ı varsa onun değeri ile değiştirilir.
      String firstName = preferDtoValue(body.getResponsibleFirstName(), split[0]);
      String lastName = preferDtoValue(body.getResponsibleLastName(), split[1]);
      form.setAd(firstName);
      form.setSoyad(lastName);
      // TCKN başvurularında bile sorumlu telefon/eposta opsiyonel set edilebilir; default boş.
      form.setSorumluTckn(nullSafe(body.getResponsibleTckn()));
      form.setSorumluCepTel(nullSafe(body.getResponsibleMobilePhone()));
      form.setSorumluEPosta(nullSafe(body.getResponsibleEmail()));
    }
    return form;
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * DTO'da değer varsa (non-blank) onu, yoksa fallback'i döner. Sertifikadan türetilen ad/soyad
   * default; kullanıcı override etmek istemezse o değer korunur.
   */
  private static String preferDtoValue(String dtoValue, String fallback) {
    if (dtoValue == null) {
      return fallback;
    }
    String trimmed = dtoValue.trim();
    return trimmed.isEmpty() ? fallback : trimmed;
  }

  private String[] splitFullName(String fullName) {
    if (fullName == null) {
      return new String[] {"", ""};
    }
    String trimmed = fullName.trim();
    int idx = trimmed.lastIndexOf(' ');
    if (idx < 0) {
      return new String[] {trimmed, ""};
    }
    return new String[] {trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim()};
  }

  private GibApplicationResponse mapReply(LinkedList<String> reply) {
    if (reply == null || reply.isEmpty()) {
      return new GibApplicationResponse("Boş yanıt", null, false);
    }
    String type = reply.get(0);
    if ("efatura".equalsIgnoreCase(type)) {
      String resultCode = reply.size() > 1 ? reply.get(1) : "";
      String documentNumber = reply.size() > 2 ? reply.get(2) : null;
      String message = reply.getLast();
      boolean success = "1".equals(resultCode);
      return new GibApplicationResponse(message, success ? documentNumber : null, success);
    }
    String message = reply.size() > 1 ? reply.get(1) : reply.get(0);
    return new GibApplicationResponse(message, null, true);
  }
}
