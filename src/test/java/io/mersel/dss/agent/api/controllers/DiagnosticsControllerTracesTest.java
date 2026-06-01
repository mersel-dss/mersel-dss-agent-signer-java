/*
 * Copyright 2026 Mersel DSS
 * SPDX-License-Identifier: Apache-2.0 WITH LicenseRef-Mersel-Brand-Attribution
 */
package io.mersel.dss.agent.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import io.mersel.dss.agent.api.services.diagnostics.TraceRecord;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecorder;
import io.mersel.dss.agent.api.services.signature.SignatureProbeService;
import io.mersel.dss.agent.api.services.signature.SupportBundleService;

/**
 * {@link DiagnosticsController} üzerindeki yeni trace recorder endpoint'lerini doğrular. Probe ve
 * bundle servisleri mock; testler yalnızca recorder akışına odaklanır.
 */
class DiagnosticsControllerTracesTest {

  @Test
  void listTracesReturnsRecordsAndStats() {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    recorder.record(record("t1", 200, null));
    recorder.record(record("t2", 500, "ERR"));

    DiagnosticsController controller =
        new DiagnosticsController(
            Mockito.mock(SignatureProbeService.class),
            Mockito.mock(SupportBundleService.class),
            recorder);

    ResponseEntity<DiagnosticsController.TracesResponse> resp = controller.listTraces(0, false);
    assertThat(resp.getStatusCodeValue()).isEqualTo(200);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().getRecords()).hasSize(2);
    Map<String, Object> stats = resp.getBody().getStats();
    assertThat(stats.get("enabled")).isEqualTo(Boolean.TRUE);
    assertThat(stats.get("currentSize")).isEqualTo(2);
    assertThat(stats.get("totalRecorded")).isEqualTo(2L);
  }

  @Test
  void listTracesWithErrorOnlyFiltersSuccess() {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    recorder.record(record("t1", 200, null));
    recorder.record(record("t2", 500, "X"));
    recorder.record(record("t3", 401, "AUTH"));

    DiagnosticsController controller =
        new DiagnosticsController(
            Mockito.mock(SignatureProbeService.class),
            Mockito.mock(SupportBundleService.class),
            recorder);

    ResponseEntity<DiagnosticsController.TracesResponse> resp = controller.listTraces(50, true);
    assertThat(resp.getBody().getRecords())
        .extracting(TraceRecord::getTraceId)
        .containsExactly("t3", "t2");
  }

  @Test
  void clearTracesEmptiesBuffer() {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    recorder.record(record("t1", 200, null));
    recorder.record(record("t2", 200, null));

    DiagnosticsController controller =
        new DiagnosticsController(
            Mockito.mock(SignatureProbeService.class),
            Mockito.mock(SupportBundleService.class),
            recorder);

    ResponseEntity<Map<String, Object>> resp = controller.clearTraces();
    assertThat(resp.getBody().get("cleared")).isEqualTo(Boolean.TRUE);
    assertThat(resp.getBody().get("currentSize")).isEqualTo(0);
    assertThat(recorder.snapshot()).isEmpty();
  }

  @Test
  void setEnabledTogglesRecorder() {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    DiagnosticsController controller =
        new DiagnosticsController(
            Mockito.mock(SignatureProbeService.class),
            Mockito.mock(SupportBundleService.class),
            recorder);

    ResponseEntity<Map<String, Object>> off = controller.setEnabled(false);
    assertThat(off.getBody().get("enabled")).isEqualTo(Boolean.FALSE);
    assertThat(off.getBody().get("previous")).isEqualTo(Boolean.TRUE);
    assertThat(recorder.isEnabled()).isFalse();

    ResponseEntity<Map<String, Object>> on = controller.setEnabled(true);
    assertThat(on.getBody().get("enabled")).isEqualTo(Boolean.TRUE);
    assertThat(on.getBody().get("previous")).isEqualTo(Boolean.FALSE);
  }

  private static TraceRecord record(String id, int status, String code) {
    return TraceRecord.builder()
        .traceId(id)
        .method("GET")
        .path("/x")
        .statusCode(status)
        .errorCode(code)
        .build();
  }
}
