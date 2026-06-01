/*
 * Copyright 2026 Mersel DSS
 * SPDX-License-Identifier: Apache-2.0 WITH LicenseRef-Mersel-Brand-Attribution
 */
package io.mersel.dss.agent.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.mersel.dss.agent.api.exceptions.CauseChainExtractor;
import io.mersel.dss.agent.api.services.diagnostics.TraceErrorContext;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecord;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecorder;

class TraceRecordingFilterTest {

  @Test
  void recordsSuccessfulRequest() throws Exception {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    TraceRecordingFilter filter = new TraceRecordingFilter(recorder);

    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/sign/xades");
    req.setQueryString("terminalName=ATR&pin=1234");
    req.setRemoteAddr("192.168.1.50");
    MockHttpServletResponse res = new MockHttpServletResponse();
    res.setStatus(200);
    res.setHeader(TraceIdFilter.HEADER_NAME, "trace-abc-123");

    FilterChain chain = (request, response) -> {};
    filter.doFilter(req, res, chain);

    List<TraceRecord> snap = recorder.snapshot();
    assertThat(snap).hasSize(1);
    TraceRecord r = snap.get(0);
    assertThat(r.getTraceId()).isEqualTo("trace-abc-123");
    assertThat(r.getMethod()).isEqualTo("POST");
    assertThat(r.getPath()).isEqualTo("/sign/xades");
    assertThat(r.getStatusCode()).isEqualTo(200);
    assertThat(r.getQuerySanitised()).contains("pin=***");
    assertThat(r.getRemoteAddr()).isEqualTo("192.168.1.***");
    assertThat(r.isError()).isFalse();
  }

  @Test
  void recordsErrorContextWhenAttached() throws Exception {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    TraceRecordingFilter filter = new TraceRecordingFilter(recorder);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/smartcard/list");
    MockHttpServletResponse res = new MockHttpServletResponse();
    res.setHeader(TraceIdFilter.HEADER_NAME, "abc");

    FilterChain chain =
        (request, response) -> {
          TraceErrorContext ctx =
              new TraceErrorContext(
                  "SIGNATURE_ALGORITHM_UNSUPPORTED",
                  "Unsupported parameters",
                  "io.mersel.SomeException",
                  java.util.Collections.singletonList(
                      new CauseChainExtractor.Frame("Boom", "boom")),
                  null);
          TraceErrorContext.attach(request, ctx);
          ((MockHttpServletResponse) response).setStatus(500);
        };

    filter.doFilter(req, res, chain);

    List<TraceRecord> snap = recorder.snapshot();
    assertThat(snap).hasSize(1);
    TraceRecord r = snap.get(0);
    assertThat(r.isError()).isTrue();
    assertThat(r.getStatusCode()).isEqualTo(500);
    assertThat(r.getErrorCode()).isEqualTo("SIGNATURE_ALGORITHM_UNSUPPORTED");
    assertThat(r.getErrorMessage()).isEqualTo("Unsupported parameters");
    assertThat(r.getExceptionType()).isEqualTo("io.mersel.SomeException");
    assertThat(r.getCauseChain()).hasSize(1);
  }

  @Test
  void disabledRecorderProducesNoRecord() throws Exception {
    TraceRecorder recorder = new TraceRecorder(false, 10);
    TraceRecordingFilter filter = new TraceRecordingFilter(recorder);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/sign/xades");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = (request, response) -> {};
    filter.doFilter(req, res, chain);

    assertThat(recorder.snapshot()).isEmpty();
  }

  @Test
  void healthAndPingPathsAreSkippedFromRecording() throws Exception {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    TraceRecordingFilter filter = new TraceRecordingFilter(recorder);

    String[] skippedPaths = {
      "/actuator/health",
      "/actuator",
      "/actuator/info",
      "/health",
      "/health/liveness",
      "/ping",
      "/favicon.ico",
      "/error"
    };
    for (String p : skippedPaths) {
      MockHttpServletRequest req = new MockHttpServletRequest("GET", p);
      MockHttpServletResponse res = new MockHttpServletResponse();
      res.setStatus(200);
      filter.doFilter(req, res, (q, r) -> {});
    }

    assertThat(recorder.snapshot()).isEmpty();
    assertThat(recorder.getTotalRecorded()).isZero();
  }

  @Test
  void normalApiPathsArePreservedDespiteSkipList() throws Exception {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    TraceRecordingFilter filter = new TraceRecordingFilter(recorder);

    String[] kept = {"/sign/xades", "/smartcard/list", "/diagnostics/sign-probe"};
    for (String p : kept) {
      MockHttpServletRequest req = new MockHttpServletRequest("POST", p);
      MockHttpServletResponse res = new MockHttpServletResponse();
      res.setStatus(200);
      filter.doFilter(req, res, (q, r) -> {});
    }

    assertThat(recorder.snapshot()).hasSize(3);
  }

  @Test
  void emptySkipListDisablesFiltering() throws Exception {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    TraceRecordingFilter filter =
        new TraceRecordingFilter(recorder, java.util.Collections.<String>emptyList());

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
    MockHttpServletResponse res = new MockHttpServletResponse();
    res.setStatus(200);
    filter.doFilter(req, res, (q, r) -> {});

    assertThat(recorder.snapshot()).hasSize(1);
  }

  @Test
  void shouldSkipMatchesOnlyOnPathBoundaries() {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    TraceRecordingFilter filter = new TraceRecordingFilter(recorder);

    // Boundary safety: /healthcare /healthy gibi yollar /health prefix'iyle YANLIŞLIKLA
    // skip edilmesin. Sadece path boundary'sinde (/, sonu, alfasayısal olmayan kar.) match olur.
    assertThat(filter.shouldSkip("/health")).isTrue();
    assertThat(filter.shouldSkip("/health/")).isTrue();
    assertThat(filter.shouldSkip("/health/liveness")).isTrue();
    assertThat(filter.shouldSkip("/healthcare")).isFalse();
    assertThat(filter.shouldSkip("/healthy-records")).isFalse();
    assertThat(filter.shouldSkip("/sign/xades")).isFalse();
    assertThat(filter.shouldSkip(null)).isFalse();
  }

  @Test
  void noneTokenInSkipListDisablesFiltering() throws Exception {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    TraceRecordingFilter filter =
        new TraceRecordingFilter(recorder, java.util.Arrays.asList("none", "/actuator"));
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse res = new MockHttpServletResponse();
    res.setStatus(200);
    filter.doFilter(req, res, (q, r) -> {});
    // "none" görüldüğünde tüm liste boşaltılır → /actuator dahil hiçbir path skip edilmez.
    assertThat(recorder.snapshot()).hasSize(1);
  }
}
