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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.services.diagnostics.TraceRecord;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecorder;
import io.mersel.dss.agent.api.services.smartcard.PcscDiagnostics;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;
import io.mersel.dss.agent.api.services.update.VersionProvider;

/** Bundle akışındaki yeni recent-traces.json adımı + temel zip içeriğini doğrular. */
class SupportBundleServiceTest {

  @Test
  void bundleIncludesOverviewPcscAndRecentTraces() throws Exception {
    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    when(reader.diagnose())
        .thenReturn(new PcscDiagnostics(Collections.emptyMap(), Collections.emptyList()));
    when(reader.listCardsWithMeta()).thenReturn(Collections.emptyList());

    MechanismCapabilityService caps = mock(MechanismCapabilityService.class);
    SignatureProbeService probe = mock(SignatureProbeService.class);
    VersionProvider version = mock(VersionProvider.class);
    when(version.currentVersion()).thenReturn("9.9.9-test");

    TraceRecorder recorder = new TraceRecorder(true, 10);
    recorder.record(
        TraceRecord.builder()
            .traceId("trace-included")
            .method("GET")
            .path("/smartcard/list")
            .statusCode(200)
            .build());

    SupportBundleService svc = new SupportBundleService(reader, caps, probe, version, recorder);

    byte[] zipBytes = svc.build("trace-hint");
    assertThat(zipBytes).isNotEmpty();

    Set<String> entries = new HashSet<String>();
    StringBuilder traceContents = new StringBuilder();
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry e;
      while ((e = zin.getNextEntry()) != null) {
        entries.add(e.getName());
        if ("recent-traces.json".equals(e.getName())) {
          byte[] buf = new byte[1024];
          int n;
          while ((n = zin.read(buf)) > 0) {
            traceContents.append(new String(buf, 0, n, StandardCharsets.UTF_8));
          }
        }
      }
    }

    assertThat(entries).contains("overview.json", "pcsc-diagnostics.json", "recent-traces.json");
    assertThat(traceContents.toString()).contains("trace-included");
    assertThat(traceContents.toString()).contains("\"enabled\" : true");
  }

  @Test
  void bundleHandlesNullRecorderGracefully() throws Exception {
    SmartCardReaderService reader = mock(SmartCardReaderService.class);
    when(reader.diagnose())
        .thenReturn(new PcscDiagnostics(Collections.emptyMap(), Collections.emptyList()));
    when(reader.listCardsWithMeta()).thenReturn(Collections.emptyList());

    SupportBundleService svc =
        new SupportBundleService(
            reader,
            mock(MechanismCapabilityService.class),
            mock(SignatureProbeService.class),
            mock(VersionProvider.class),
            null);

    byte[] zipBytes = svc.build("hint");
    StringBuilder traceContents = new StringBuilder();
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry e;
      while ((e = zin.getNextEntry()) != null) {
        if ("recent-traces.json".equals(e.getName())) {
          byte[] buf = new byte[1024];
          int n;
          while ((n = zin.read(buf)) > 0) {
            traceContents.append(new String(buf, 0, n, StandardCharsets.UTF_8));
          }
        }
      }
    }
    assertThat(traceContents.toString()).contains("TraceRecorder bağlanmamış");
  }
}
