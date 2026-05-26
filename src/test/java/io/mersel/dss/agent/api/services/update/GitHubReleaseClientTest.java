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
package io.mersel.dss.agent.api.services.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.exceptions.UpdateCheckException;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

class GitHubReleaseClientTest {

  private MockWebServer server;
  private SignerProperties props;
  private GitHubReleaseClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    props = new SignerProperties();
    props.getUpdate().setApiBaseUrl(server.url("/").toString().replaceAll("/$", ""));
    props.getUpdate().setRepository("mersel-dss/mersel-dss-agent-signer-java");
    props.getUpdate().setAssetNamePattern("mersel-dss-agent-signer-api-.*\\.jar");
    OkHttpClient http =
        new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build();
    client = new GitHubReleaseClient(props, new ObjectMapper(), http);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void parsesRealisticReleaseAndPicksPatternMatchingAsset() throws Exception {
    enqueueFixture();
    LatestRelease release = client.fetchLatest(true);

    assertThat(release.getTagName()).isEqualTo("v2.1.0");
    assertThat(release.getName()).contains("Auto-update");
    assertThat(release.getHtmlUrl()).endsWith("/releases/tag/v2.1.0");
    assertThat(release.getPublishedAt()).isEqualTo("2026-06-15T10:00:00Z");
    assertThat(release.isPrerelease()).isFalse();
    // Pattern eşleşeni — `-sources.jar` ASLA seçilmez, ana .jar seçilir.
    assertThat(release.getAssetName()).isEqualTo("mersel-dss-agent-signer-api-2.1.0.jar");
    assertThat(release.getAssetDownloadUrl())
        .endsWith("/v2.1.0/mersel-dss-agent-signer-api-2.1.0.jar");
    assertThat(release.getBody()).contains("Otomatik");

    RecordedRequest req = server.takeRequest();
    assertThat(req.getPath())
        .isEqualTo("/repos/mersel-dss/mersel-dss-agent-signer-java/releases/latest");
    assertThat(req.getHeader("Accept")).contains("application/vnd.github+json");
    assertThat(req.getHeader("User-Agent")).contains("mersel-dss-agent-signer");
  }

  @Test
  void fallsBackToFirstJarWhenPatternDoesNotMatch() throws Exception {
    props.getUpdate().setAssetNamePattern("nonexistent-pattern-.*\\.jar");
    enqueueFixture();
    LatestRelease release = client.fetchLatest(true);
    assertThat(release.getAssetName()).isEqualTo("mersel-dss-agent-signer-api-2.1.0.jar");
  }

  @Test
  void fallsBackToHtmlUrlWhenNoAssets() throws Exception {
    String json =
        "{\"tag_name\":\"v3.0.0\","
            + "\"html_url\":\"https://github.com/mersel-dss/mersel-dss-agent-signer-java/releases/tag/v3.0.0\","
            + "\"prerelease\":false,\"published_at\":\"2026-09-01T00:00:00Z\",\"body\":\"\",\"assets\":[]}";
    server.enqueue(jsonResponse(200, json, null));
    LatestRelease release = client.fetchLatest(true);
    assertThat(release.getAssetDownloadUrl()).isNull();
    assertThat(release.getAssetName()).isNull();
    assertThat(release.getHtmlUrl()).endsWith("/v3.0.0");
  }

  @Test
  void http404ThrowsUpdateCheckException() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));
    assertThatThrownBy(() -> client.fetchLatest(true))
        .isInstanceOf(UpdateCheckException.class)
        .hasMessageContaining("HTTP 404");
  }

  @Test
  void http403ThrowsUpdateCheckException() {
    server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"rate limit\"}"));
    assertThatThrownBy(() -> client.fetchLatest(true))
        .isInstanceOf(UpdateCheckException.class)
        .hasMessageContaining("HTTP 403");
  }

  @Test
  void socketTimeoutThrowsUpdateCheckException() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    assertThatThrownBy(() -> client.fetchLatest(true)).isInstanceOf(UpdateCheckException.class);
  }

  @Test
  void etagRoundTripReturnsCachedOn304() throws Exception {
    // İlk istek 200 + ETag + body.
    server.enqueue(jsonResponse(200, readFixture(), "\"etag-abc\""));
    LatestRelease first = client.fetchLatest(false);
    assertThat(first.getEtag()).isEqualTo("\"etag-abc\"");

    // İkinci istek 304 → cached aynen dönmeli.
    server.enqueue(new MockResponse().setResponseCode(304));
    LatestRelease second = client.fetchLatest(false);
    assertThat(second).isSameAs(first);

    // server'a ikinci istek gitmiş olmalı + If-None-Match header'ı içermeli.
    server.takeRequest();
    RecordedRequest secondReq = server.takeRequest();
    assertThat(secondReq.getHeader("If-None-Match")).isEqualTo("\"etag-abc\"");
  }

  @Test
  void prereleaseModeQueriesReleasesEndpointAndPicksFirst() throws Exception {
    props.getUpdate().setPrereleaseAllowed(true);
    String arr =
        "[{\"tag_name\":\"v2.2.0-rc1\","
            + "\"html_url\":\"https://github.com/mersel-dss/mersel-dss-agent-signer-java/releases/tag/v2.2.0-rc1\","
            + "\"prerelease\":true,\"published_at\":\"2026-07-01T00:00:00Z\",\"body\":\"\",\"assets\":[]}]";
    server.enqueue(jsonResponse(200, arr, null));
    LatestRelease release = client.fetchLatest(true);
    assertThat(release.getTagName()).isEqualTo("v2.2.0-rc1");
    assertThat(release.isPrerelease()).isTrue();
    RecordedRequest req = server.takeRequest();
    assertThat(req.getPath()).startsWith("/repos/mersel-dss/mersel-dss-agent-signer-java/releases");
    assertThat(req.getPath()).contains("per_page=");
  }

  /* ---------------- helpers ---------------- */

  private void enqueueFixture() throws IOException {
    server.enqueue(jsonResponse(200, readFixture(), null));
  }

  private static MockResponse jsonResponse(int code, String body, String etag) {
    MockResponse m = new MockResponse().setResponseCode(code).setBody(body);
    m.addHeader("Content-Type", "application/json");
    if (etag != null) {
      m.addHeader("ETag", etag);
    }
    return m;
  }

  private static String readFixture() throws IOException {
    try (InputStream in =
        GitHubReleaseClientTest.class.getResourceAsStream("/fixtures/github/release-latest.json")) {
      if (in == null) {
        throw new IOException("fixture not found");
      }
      try (Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
        return s.hasNext() ? s.next() : "";
      }
    }
  }
}
