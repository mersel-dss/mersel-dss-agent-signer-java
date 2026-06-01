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
package io.mersel.dss.agent.api.services.signature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.mersel.dss.agent.api.services.diagnostics.TraceRecord;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecorder;
import io.mersel.dss.agent.api.services.smartcard.PcscDiagnostics;
import io.mersel.dss.agent.api.services.smartcard.SmartCardInfo;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;
import io.mersel.dss.agent.api.services.update.VersionProvider;

/**
 * "Sorun bildir" akışı için tek tıkla destek paketi üretir.
 *
 * <p>İçerik (ZIP):
 *
 * <ul>
 *   <li>{@code overview.json} — uygulama sürümü, Java/OS özet, üretim zamanı, traceId.
 *   <li>{@code pcsc-diagnostics.json} — {@link SmartCardReaderService#diagnose}.
 *   <li>{@code mechanisms-{terminal}.json} — kart başına {@link MechanismCapabilityResponse}.
 *   <li>{@code sign-probe-{terminal}.json} — kart başına {@link SignatureProbeService.Result}.
 * </ul>
 *
 * <p>PII riski olan alanlar (PIN, kart serisi tam) hiçbir zaman bu pakete dahil edilmez. Token
 * serisi {@link
 * io.mersel.dss.agent.api.services.keystore.Pkcs11MechanismProbe.ProbeResult#getTokenSerialMasked()}
 * üzerinden zaten maskelenmiş hâlde gelir.
 */
@Service
public class SupportBundleService {

  private static final Logger log = LoggerFactory.getLogger(SupportBundleService.class);

  private final SmartCardReaderService readerService;
  private final MechanismCapabilityService mechanismService;
  private final SignatureProbeService probeService;
  private final VersionProvider versionProvider;
  private final TraceRecorder traceRecorder;
  private final ObjectMapper mapper;

  public SupportBundleService(
      SmartCardReaderService readerService,
      MechanismCapabilityService mechanismService,
      SignatureProbeService probeService,
      VersionProvider versionProvider,
      TraceRecorder traceRecorder) {
    this.readerService = readerService;
    this.mechanismService = mechanismService;
    this.probeService = probeService;
    this.versionProvider = versionProvider;
    this.traceRecorder = traceRecorder;
    this.mapper = new ObjectMapper();
    this.mapper.findAndRegisterModules(); // OffsetDateTime için JSR-310 modülü
    this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  /** Bundle'ı bellek üzerinde üretir; küçük bir ZIP (KB seviyesi). */
  public byte[] build(String traceIdHint) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      writeOverview(zip, traceIdHint);
      writePcscDiagnostics(zip);

      // Her takılı kart için ayrı mekanizma ve sign-probe dosyaları üret.
      List<SmartCardInfo> infos;
      try {
        infos = readerService.listCardsWithMeta();
      } catch (RuntimeException e) {
        log.debug("listCardsWithMeta destek paketi sırasında başarısız: {}", e.getMessage());
        infos = java.util.Collections.<SmartCardInfo>emptyList();
      }
      int idx = 0;
      for (SmartCardInfo info : infos) {
        idx++;
        writeMechanisms(zip, info, idx);
        writeSignProbe(zip, info, idx);
      }

      writeRecentTraces(zip);
    } catch (IOException e) {
      throw new RuntimeException("Support bundle ZIP üretilemedi: " + e.getMessage(), e);
    }
    return out.toByteArray();
  }

  private void writeOverview(ZipOutputStream zip, String traceId) throws IOException {
    Map<String, Object> overview = new LinkedHashMap<String, Object>();
    overview.put("generatedAt", OffsetDateTime.now().toString());
    overview.put("appVersion", versionProvider != null ? versionProvider.currentVersion() : null);
    overview.put("traceId", traceId);
    Map<String, String> jvm = new LinkedHashMap<String, String>();
    jvm.put("os.name", System.getProperty("os.name"));
    jvm.put("os.version", System.getProperty("os.version"));
    jvm.put("os.arch", System.getProperty("os.arch"));
    jvm.put("java.version", System.getProperty("java.version"));
    jvm.put("java.vendor", System.getProperty("java.vendor"));
    jvm.put("java.vm.name", System.getProperty("java.vm.name"));
    overview.put("environment", jvm);
    putEntry(zip, "overview.json", mapper.writeValueAsString(overview));
  }

  private void writePcscDiagnostics(ZipOutputStream zip) throws IOException {
    try {
      PcscDiagnostics d = readerService.diagnose();
      putEntry(zip, "pcsc-diagnostics.json", mapper.writeValueAsString(d));
    } catch (RuntimeException e) {
      Map<String, String> err = new LinkedHashMap<String, String>();
      err.put("error", e.getClass().getSimpleName());
      err.put("message", e.getMessage());
      putEntry(zip, "pcsc-diagnostics.json", mapper.writeValueAsString(err));
    }
  }

  private void writeMechanisms(ZipOutputStream zip, SmartCardInfo info, int idx)
      throws IOException {
    String safeName = sanitizeName(info.getTerminalName(), idx);
    try {
      MechanismCapabilityResponse caps =
          mechanismService.describe(info.getTerminalName(), null, null);
      putEntry(zip, "mechanisms-" + safeName + ".json", mapper.writeValueAsString(caps));
    } catch (RuntimeException e) {
      Map<String, String> err = new LinkedHashMap<String, String>();
      err.put("terminal", info.getTerminalName());
      err.put("error", e.getClass().getSimpleName());
      err.put("message", e.getMessage());
      putEntry(zip, "mechanisms-" + safeName + ".json", mapper.writeValueAsString(err));
    }
  }

  private void writeSignProbe(ZipOutputStream zip, SmartCardInfo info, int idx) throws IOException {
    String safeName = sanitizeName(info.getTerminalName(), idx);
    try {
      SignatureProbeService.Result probe = probeService.probe(info.getTerminalName(), null, null);
      putEntry(zip, "sign-probe-" + safeName + ".json", mapper.writeValueAsString(probe));
    } catch (RuntimeException e) {
      Map<String, String> err = new LinkedHashMap<String, String>();
      err.put("terminal", info.getTerminalName());
      err.put("error", e.getClass().getSimpleName());
      err.put("message", e.getMessage());
      putEntry(zip, "sign-probe-" + safeName + ".json", mapper.writeValueAsString(err));
    }
  }

  /**
   * Bellek içi trace recorder doluysa son N kaydı pakete ekler. Recorder hiç enjekte edilmemişse
   * (eski test fixture'ları) sessizce atlar; "no traces" notu yine de yazılır ki destek tarafında
   * kafa karışıklığı olmasın.
   */
  private void writeRecentTraces(ZipOutputStream zip) throws IOException {
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    if (traceRecorder == null) {
      body.put("note", "TraceRecorder bağlanmamış (eski yapı / test fixture).");
      body.put("records", java.util.Collections.<TraceRecord>emptyList());
    } else {
      Map<String, Object> stats = new LinkedHashMap<String, Object>();
      stats.put("enabled", traceRecorder.isEnabled());
      stats.put("capacity", traceRecorder.getCapacity());
      stats.put("currentSize", traceRecorder.currentSize());
      stats.put("totalRecorded", traceRecorder.getTotalRecorded());
      stats.put("totalDropped", traceRecorder.getTotalDropped());
      body.put("stats", stats);
      // Üst sınır 100; daha fazlası destek bundle'ını şişirir, zaten en yenisi en kıymetlisidir.
      body.put("records", traceRecorder.snapshot(100, false));
    }
    putEntry(zip, "recent-traces.json", mapper.writeValueAsString(body));
  }

  private static String sanitizeName(String s, int idx) {
    if (s == null) {
      return "card" + idx;
    }
    String trimmed = s.trim();
    StringBuilder sb = new StringBuilder(trimmed.length());
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
      sb.append(ok ? c : '_');
    }
    String out = sb.toString();
    if (out.isEmpty()) {
      out = "card" + idx;
    }
    return out;
  }

  private static void putEntry(ZipOutputStream zip, String name, String contents)
      throws IOException {
    ZipEntry entry = new ZipEntry(name);
    zip.putNextEntry(entry);
    zip.write(contents.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
