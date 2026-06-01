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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotBlank;

import org.slf4j.MDC;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.mersel.dss.agent.api.config.TraceIdFilter;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecord;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecorder;
import io.mersel.dss.agent.api.services.signature.SignatureProbeService;
import io.mersel.dss.agent.api.services.signature.SupportBundleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * "Sorun çöz" ve "destek bildir" akışları için tanılama uçları.
 *
 * <p>Endpoint'ler PIN harcamaz, kart sayacını etkilemez. Frontend'in "Sorun bildir" panelinde
 * otomatik tetiklenir; çıktı destek operatörüne yapıştırılır veya ZIP olarak indirilip ticket'a
 * iliştirilir.
 */
@RestController
@Tag(name = "Tanılama", description = "İmzalama akışı tanılama ve destek araçları.")
public class DiagnosticsController {

  private final SignatureProbeService probeService;
  private final SupportBundleService supportBundleService;
  private final TraceRecorder traceRecorder;

  public DiagnosticsController(
      SignatureProbeService probeService,
      SupportBundleService supportBundleService,
      TraceRecorder traceRecorder) {
    this.probeService = probeService;
    this.supportBundleService = supportBundleService;
    this.traceRecorder = traceRecorder;
  }

  @Operation(
      summary = "PIN'siz dry-run imzalama tanılaması.",
      description =
          "Mekanizma listesini ve sertifika tipi varsayımlarını kullanarak xades4j'in default"
              + " profili veya Mersel resolver'ın seçeceği fallback ile imzalamanın çalışıp"
              + " çalışmayacağını döner. Hiç PIN gönderilmediği için kart sayacı harcanmaz."
              + " Yanıt RSA ve ECDSA için ayrı dallar içerir; en az biri başarılıysa overall"
              + " `WOULD_SUCCEED`. Aksi halde `blockingReason` + `remediation` alanları"
              + " kullanıcıya gösterilecek ipuçlarını taşır.")
  @PostMapping(value = "/diagnostics/sign-probe", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SignatureProbeService.Result> signProbe(
      @Parameter(description = "PCSC terminal adı.", required = true)
          @NotBlank
          @RequestParam("terminalName")
          String terminalName,
      @Parameter(description = "İsteğe bağlı PKCS#11 paylaşımlı kütüphane yolu.")
          @RequestParam(value = "pkcs11LibraryPath", required = false)
          String pkcs11LibraryPath,
      @Parameter(description = "Layer 5 fallback: kullanıcı tarafından seçilen kart tipi.")
          @RequestParam(value = "cardType", required = false)
          String cardType) {
    return ResponseEntity.ok(probeService.probe(terminalName, pkcs11LibraryPath, cardType));
  }

  @Operation(
      summary = "Tek tıkla destek paketi (ZIP).",
      description =
          "Uygulama sürümü, JVM/OS özet, PCSC tanılaması, takılı her kart için mekanizma listesi"
              + " ve dry-run imzalama sonucu içeren küçük bir ZIP üretir. PII yoktur (PIN ve kart"
              + " serisi tam asla yer almaz; serial maskelenir). Kullanıcı bu ZIP'i destek"
              + " talebine iliştirir; destek tarafı 5 dakika içinde sorunu tespit eder.")
  @GetMapping(value = "/diagnostics/support-bundle", produces = "application/zip")
  public ResponseEntity<ByteArrayResource> supportBundle() {
    String traceId = MDC.get(TraceIdFilter.MDC_KEY);
    byte[] zip = supportBundleService.build(traceId);
    String fileName = "mersel-support-bundle-" + System.currentTimeMillis() + ".zip";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .contentLength(zip.length)
        .body(new ByteArrayResource(zip));
  }

  /* ---------------- in-memory trace recorder API ---------------- */

  @Operation(
      summary = "Bellek içi trace kayıtlarını listele.",
      description =
          "TraceRecorder ring buffer'ından en yeni → en eski sırayla kayıtları döner. Her kayıt;"
              + " traceId, HTTP path/status, errorCode, cause chain ve (varsa) signatureDiagnostics"
              + " içerir. PIN ve ham gövde tutulmaz; query string'de hassas anahtarlar maskelenir."
              + " Recorder kapalıysa boş liste döner.")
  @GetMapping(value = "/diagnostics/traces", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TracesResponse> listTraces(
      @Parameter(description = "Maksimum kayıt sayısı (0 = limit yok).")
          @RequestParam(value = "limit", defaultValue = "100")
          int limit,
      @Parameter(description = "Sadece hata kayıtlarını döner (status >= 400 veya errorCode dolu).")
          @RequestParam(value = "errorOnly", defaultValue = "false")
          boolean errorOnly) {
    List<TraceRecord> records = traceRecorder.snapshot(limit, errorOnly);
    Map<String, Object> stats = new LinkedHashMap<String, Object>();
    stats.put("enabled", traceRecorder.isEnabled());
    stats.put("capacity", traceRecorder.getCapacity());
    stats.put("currentSize", traceRecorder.currentSize());
    stats.put("totalRecorded", traceRecorder.getTotalRecorded());
    stats.put("totalDropped", traceRecorder.getTotalDropped());
    return ResponseEntity.ok(new TracesResponse(stats, records));
  }

  @Operation(
      summary = "Bellek içi trace buffer'ını temizler.",
      description = "Sayaçlar (totalRecorded/totalDropped) korunur; sadece buffer boşaltılır.")
  @DeleteMapping(value = "/diagnostics/traces", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> clearTraces() {
    traceRecorder.clear();
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("cleared", true);
    body.put("currentSize", traceRecorder.currentSize());
    return ResponseEntity.ok(body);
  }

  @Operation(
      summary = "Trace recorder aç/kapa.",
      description =
          "Runtime'da kaydı kapatmak için. Kapalıyken filter buffer'a yazmaz; UI panelde de"
              + " yeni kayıt görünmez. Mevcut kayıtlar silinmez.")
  @PostMapping(value = "/diagnostics/traces/enabled", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> setEnabled(
      @Parameter(description = "Yeni durum.", required = true) @RequestParam("enabled")
          boolean enabled) {
    boolean previous = traceRecorder.setEnabled(enabled);
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("enabled", traceRecorder.isEnabled());
    body.put("previous", previous);
    return ResponseEntity.ok(body);
  }

  /** Trace listesi + sayaçlar için yanıt zarfı. */
  public static final class TracesResponse {
    private final Map<String, Object> stats;
    private final List<TraceRecord> records;

    public TracesResponse(Map<String, Object> stats, List<TraceRecord> records) {
      this.stats = stats;
      this.records = records;
    }

    public Map<String, Object> getStats() {
      return stats;
    }

    public List<TraceRecord> getRecords() {
      return records;
    }
  }
}
