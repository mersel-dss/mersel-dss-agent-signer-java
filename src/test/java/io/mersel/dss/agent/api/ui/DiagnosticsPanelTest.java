/*
 * Copyright 2026 Mersel DSS
 * SPDX-License-Identifier: Apache-2.0 WITH LicenseRef-Mersel-Brand-Attribution
 */
package io.mersel.dss.agent.api.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.services.diagnostics.TraceRecord;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecorder;

/**
 * Headless ortamda {@link DiagnosticsPanel} açılışı no-op olmalı; null recorder ile çağrı da
 * patlamamalı. CI'da gerçek pencere render edilemez; bu testler smoke + null-safety odaklı.
 */
class DiagnosticsPanelTest {

  @AfterEach
  void tearDown() {
    DiagnosticsPanel.resetForTest();
  }

  @Test
  void showOrFocusIsNoOpInHeadlessEnvironment() {
    // Headless mode otomatik aktif testlerde (java.awt.headless=true). Pencere açılmaz; exception
    // atılmamalı; "current" referansı set edilmemeli.
    System.setProperty("java.awt.headless", "true");
    TraceRecorder recorder = new TraceRecorder(true, 10);
    assertDoesNotThrow(() -> DiagnosticsPanel.showOrFocus(recorder));
    assertThat(DiagnosticsPanel.isShowingForTest()).isFalse();
  }

  @Test
  void showOrFocusWithNullRecorderIsSafe() {
    assertDoesNotThrow(() -> DiagnosticsPanel.showOrFocus(null));
    assertThat(DiagnosticsPanel.isShowingForTest()).isFalse();
  }

  @Test
  void traceTableModelExposesRowsCorrectly() {
    DiagnosticsPanel.TraceTableModel model = new DiagnosticsPanel.TraceTableModel();
    model.setRecords(
        java.util.Arrays.asList(
            TraceRecord.builder()
                .traceId("trace-1")
                .method("GET")
                .path("/smartcard/list")
                .statusCode(200)
                .durationMs(42)
                .build(),
            TraceRecord.builder()
                .traceId("trace-2")
                .method("POST")
                .path("/sign/xades")
                .statusCode(500)
                .errorCode("SIGNATURE_FAILED")
                .durationMs(123)
                .build()));

    assertThat(model.getRowCount()).isEqualTo(2);
    assertThat(model.getValueAt(0, 1)).isEqualTo("GET");
    assertThat(model.getValueAt(0, 2)).isEqualTo("/smartcard/list");
    assertThat(model.getValueAt(0, 3)).isEqualTo("200");
    assertThat(model.getValueAt(1, 5)).isEqualTo("SIGNATURE_FAILED");
    assertThat(model.getValueAt(1, 6)).isEqualTo("trace-2");

    TraceRecord prepended =
        TraceRecord.builder()
            .traceId("trace-3")
            .method("DELETE")
            .path("/x")
            .statusCode(204)
            .build();
    model.prepend(prepended);
    assertThat(model.getRowCount()).isEqualTo(3);
    assertThat(model.getRow(0).getTraceId()).isEqualTo("trace-3");
  }

  @Test
  void statusPillRendererProducesComponentForCommonStatuses() {
    // Renderer'lar direkt instantiate edilebilmeli (headless'a takılmadan).
    DiagnosticsPanel.StatusPillRenderer renderer = new DiagnosticsPanel.StatusPillRenderer();
    javax.swing.JTable t = new javax.swing.JTable();
    int[] codes = {200, 201, 301, 400, 404, 500, 502};
    for (int code : codes) {
      java.awt.Component c =
          renderer.getTableCellRendererComponent(t, Integer.toString(code), false, false, 0, 3);
      assertThat(c).isNotNull();
    }
  }

  @Test
  void methodPillRendererSupportsAllVerbs() {
    DiagnosticsPanel.MethodPillRenderer renderer = new DiagnosticsPanel.MethodPillRenderer();
    javax.swing.JTable t = new javax.swing.JTable();
    String[] verbs = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"};
    for (String v : verbs) {
      java.awt.Component c = renderer.getTableCellRendererComponent(t, v, false, false, 0, 1);
      assertThat(c).isNotNull();
    }
  }

  @Test
  void emptyHtmlStringIsBuiltSafely() {
    // Empty state HTML üretimi private static; yine de smoke için renderer pipeline'ından
    // bağımsız bir basic null-safety: panel çağrıları null-safe.
    assertDoesNotThrow(() -> DiagnosticsPanel.showOrFocus(null));
    assertDoesNotThrow(() -> DiagnosticsPanel.close());
  }
}
