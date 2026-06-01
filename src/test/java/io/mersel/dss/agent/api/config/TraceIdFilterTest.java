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
package io.mersel.dss.agent.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

  private final TraceIdFilter filter = new TraceIdFilter();

  @Test
  void generatesUuidWhenNoInboundTraceHeader() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
    MockHttpServletResponse resp = new MockHttpServletResponse();

    filter.doFilter(req, resp, new MockFilterChain());

    String header = resp.getHeader(TraceIdFilter.HEADER_NAME);
    assertThat(header).isNotBlank();
    UUID parsed = UUID.fromString(header); // valid UUID
    assertThat(parsed).isNotNull();
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void honoursInboundXMerselTraceIdHeader() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
    req.addHeader(TraceIdFilter.HEADER_NAME, "abc-123_safe.id");
    MockHttpServletResponse resp = new MockHttpServletResponse();

    filter.doFilter(req, resp, new MockFilterChain());

    assertThat(resp.getHeader(TraceIdFilter.HEADER_NAME)).isEqualTo("abc-123_safe.id");
  }

  @Test
  void rejectsInboundTraceWithUnsafeChars_andGeneratesNew() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
    req.addHeader(TraceIdFilter.HEADER_NAME, "<script>alert(1)</script>");
    MockHttpServletResponse resp = new MockHttpServletResponse();

    filter.doFilter(req, resp, new MockFilterChain());

    String header = resp.getHeader(TraceIdFilter.HEADER_NAME);
    assertThat(header).isNotEqualTo("<script>alert(1)</script>");
    UUID.fromString(header); // generated as fresh UUID
  }

  @Test
  void putsTraceIdInMdcDuringChainAndClearsAfter() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
    req.addHeader(TraceIdFilter.HEADER_NAME, "trace-001");
    MockHttpServletResponse resp = new MockHttpServletResponse();

    final String[] capturedMdc = new String[1];
    FilterChain chain = (req2, resp2) -> capturedMdc[0] = MDC.get(TraceIdFilter.MDC_KEY);

    filter.doFilter(req, resp, chain);

    assertThat(capturedMdc[0]).isEqualTo("trace-001");
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void readInboundOrGenerate_acceptsXRequestId() {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
    req.addHeader("X-Request-Id", "abcDEF.123_test");

    String got = TraceIdFilter.readInboundOrGenerate((HttpServletRequest) req);

    assertThat(got).isEqualTo("abcDEF.123_test");
  }
}
