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

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.exceptions.UpdateCheckException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * GitHub Releases REST API istemcisi.
 *
 * <p>Sorgu davranışı {@link SignerProperties.Update} ayarları ile sürülür:
 *
 * <ul>
 *   <li>{@code prereleaseAllowed=false} (default) → {@code /repos/{owner}/{repo}/releases/latest}
 *       (GitHub prerelease'leri dışlar).
 *   <li>{@code prereleaseAllowed=true} → {@code /repos/{owner}/{repo}/releases} (ilk eleman
 *       alınır).
 * </ul>
 *
 * <p>HTTP/JSON başarısızlıkları {@link UpdateCheckException}'a sarılır. {@code If-None-Match} ETag
 * desteklenir: önceki yanıtın ETag'i client içinde tutulur; sonraki çağrıda gönderilir, sunucu 304
 * dönerse cached {@link LatestRelease} aynen iade edilir. {@link #fetchLatest(boolean
 * forceRefresh)} ile cache bypass mümkündür.
 *
 * <p>Test edilebilirlik: ctor {@link OkHttpClient}'ı dışarıdan kabul eder. Üretimde Spring
 * auto-wire config'te oluşturulan instance'ı geçer; testte {@code MockWebServer.url("/")}
 * hedeflenebilen standart istemci geçilir.
 */
@Component
public class GitHubReleaseClient {

  private static final Logger LOG = LoggerFactory.getLogger(GitHubReleaseClient.class);

  private final SignerProperties properties;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;

  /** ETag round-trip cache. {@code null} olabilir (henüz hiç sorgu yapılmadıysa). */
  private final AtomicReference<LatestRelease> cached = new AtomicReference<LatestRelease>();

  @Autowired
  public GitHubReleaseClient(SignerProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, defaultClient(properties));
  }

  /** Test friendly ctor. */
  public GitHubReleaseClient(
      SignerProperties properties, ObjectMapper objectMapper, OkHttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  private static OkHttpClient defaultClient(SignerProperties properties) {
    int timeoutMs = properties.getUpdate().getHttpTimeoutMs();
    return new OkHttpClient.Builder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build();
  }

  /**
   * En son release'i çeker. {@code forceRefresh=true} ise ETag/conditional GET bypass edilir.
   *
   * @throws UpdateCheckException ağ veya parse hatasında.
   */
  public LatestRelease fetchLatest(boolean forceRefresh) {
    SignerProperties.Update cfg = properties.getUpdate();
    String url = buildUrl(cfg);
    String currentEtag = forceRefresh ? null : etagOf(cached.get());

    Request.Builder builder =
        new Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "mersel-dss-agent-signer/0.0.0");
    if (currentEtag != null) {
      builder.header("If-None-Match", currentEtag);
    }

    Request request = builder.build();
    try (Response response = httpClient.newCall(request).execute()) {
      int code = response.code();
      if (code == 304 && cached.get() != null) {
        LOG.debug("GitHub release 304 Not Modified — cached release döndürülüyor.");
        return cached.get();
      }
      if (!response.isSuccessful()) {
        throw new UpdateCheckException(
            "GitHub release sorgusu başarısız: HTTP " + code + " — " + url);
      }
      ResponseBody body = response.body();
      if (body == null) {
        throw new UpdateCheckException("GitHub release sorgusu boş body döndü.");
      }
      String etag = response.header("ETag");
      String json = body.string();
      LatestRelease parsed = parse(json, etag, cfg);
      cached.set(parsed);
      return parsed;
    } catch (IOException ioe) {
      throw new UpdateCheckException(
          "GitHub release sorgusu sırasında I/O hatası: " + ioe.getMessage(), ioe);
    }
  }

  private static String etagOf(LatestRelease r) {
    return r == null ? null : r.getEtag();
  }

  private static String buildUrl(SignerProperties.Update cfg) {
    String base = cfg.getApiBaseUrl();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    String repo = cfg.getRepository();
    if (cfg.isPrereleaseAllowed()) {
      return base + "/repos/" + repo + "/releases?per_page=10";
    }
    return base + "/repos/" + repo + "/releases/latest";
  }

  private LatestRelease parse(String json, String etag, SignerProperties.Update cfg) {
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode releaseNode = root;
      if (cfg.isPrereleaseAllowed() && root.isArray()) {
        if (root.size() == 0) {
          throw new UpdateCheckException("GitHub release listesi boş.");
        }
        releaseNode = root.get(0);
      }
      String tagName = textOrNull(releaseNode, "tag_name");
      String name = textOrNull(releaseNode, "name");
      String htmlUrl = textOrNull(releaseNode, "html_url");
      String body = textOrNull(releaseNode, "body");
      String publishedAt = textOrNull(releaseNode, "published_at");
      boolean prerelease =
          releaseNode.has("prerelease") && releaseNode.get("prerelease").asBoolean(false);

      String assetUrl = null;
      String assetName = null;
      JsonNode assets = releaseNode.get("assets");
      if (assets != null && assets.isArray() && assets.size() > 0) {
        Pattern pattern;
        try {
          pattern = Pattern.compile(cfg.getAssetNamePattern());
        } catch (RuntimeException re) {
          LOG.warn(
              "asset-name-pattern derlenemedi ({}): {}; pattern devre dışı.",
              cfg.getAssetNamePattern(),
              re.getMessage());
          pattern = null;
        }
        // 1) pattern'e uyan ilk asset.
        if (pattern != null) {
          for (Iterator<JsonNode> it = assets.elements(); it.hasNext(); ) {
            JsonNode a = it.next();
            String aName = textOrNull(a, "name");
            if (aName != null && pattern.matcher(aName).matches()) {
              assetUrl = textOrNull(a, "browser_download_url");
              assetName = aName;
              break;
            }
          }
        }
        // 2) fallback: ilk .jar
        if (assetUrl == null) {
          for (Iterator<JsonNode> it = assets.elements(); it.hasNext(); ) {
            JsonNode a = it.next();
            String aName = textOrNull(a, "name");
            if (aName != null && aName.toLowerCase().endsWith(".jar")) {
              assetUrl = textOrNull(a, "browser_download_url");
              assetName = aName;
              break;
            }
          }
        }
      }
      return new LatestRelease(
          tagName, name, htmlUrl, body, publishedAt, prerelease, assetUrl, assetName, etag);
    } catch (IOException ioe) {
      throw new UpdateCheckException("GitHub release JSON parse hatası: " + ioe.getMessage(), ioe);
    }
  }

  private static String textOrNull(JsonNode parent, String field) {
    JsonNode n = parent.get(field);
    return (n == null || n.isNull()) ? null : n.asText(null);
  }

  /** Test ve teşhis için ETag cache'ini temizler. */
  public Optional<LatestRelease> peekCached() {
    return Optional.ofNullable(cached.get());
  }

  /** Test ve teşhis için cache'i tamamen temizler. */
  public void invalidateCache() {
    cached.set(null);
  }
}
