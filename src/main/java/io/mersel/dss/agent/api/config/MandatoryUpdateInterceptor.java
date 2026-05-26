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
 */
package io.mersel.dss.agent.api.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mersel.dss.agent.api.models.ErrorModel;
import io.mersel.dss.agent.api.services.update.UpdateGate;
import io.mersel.dss.agent.api.services.update.UpdateInfo;

/**
 * Zorunlu güncelleme bekleyen daemon'da imzalama endpoint'lerini durdurur.
 *
 * <p>{@link UpdateGate#isMandatoryBlocked()} {@code true} olduğunda allowlist DIŞINDAKİ tüm
 * istekler {@code HTTP 426 Upgrade Required} + yapılandırılmış {@link ErrorModel} JSON döner. Body
 * içinde {@code downloadHint} olarak hem release HTML sayfası hem doğrudan jar URL'i taşınır;
 * uzantı / istemci kullanıcıyı yönlendirebilsin.
 *
 * <p>Allowlist (her durumda 200/normal akış):
 *
 * <ul>
 *   <li>{@code /update/**} — kullanıcı yine yeni sürüm durumunu sorgulayabilsin / manuel
 *       tetikleyebilsin.
 *   <li>{@code /actuator/**} — monitoring (sağlık endpoint'leri).
 *   <li>{@code /v3/api-docs/**}, {@code /swagger}, {@code /webjars/**} — OpenAPI metadata.
 *   <li>{@code /}, {@code /index.html}, {@code /static/**}, {@code /assets/**}, {@code /favicon.*}
 *       — Scalar API reference statik içeriği.
 *   <li>{@code /error} — Spring boot default error path; sonsuz döngü olmasın.
 * </ul>
 *
 * <p>Diğer her şey — özellikle {@code /pades/**}, {@code /xades/**}, {@code /smartcard/**}, {@code
 * /gib/**} — blok atar. Yan etki olarak preflight OPTIONS istekleri de bloklanır görünür ama Spring
 * MVC CORS preflight'ı handler chain'e girmeden önce ele alır; bu interceptor controller mapping'i
 * olan istekler için çalışır.
 */
@Component
public class MandatoryUpdateInterceptor implements HandlerInterceptor {

  private static final Logger LOG = LoggerFactory.getLogger(MandatoryUpdateInterceptor.class);
  private static final String ERROR_CODE = "UPDATE_REQUIRED";
  // HTTP 426 — RFC 7231 §6.5.15. "The server refuses to perform the request using the current
  // protocol but might be willing to do so after the client upgrades to a different protocol."
  // Burada "protocol" yerine "client version" — semantik tam oturmasa da uygun stable kod.
  private static final int HTTP_UPGRADE_REQUIRED = 426;
  private static final List<String> ALLOWLIST_PATTERNS =
      Arrays.asList(
          "/update/**",
          "/actuator/**",
          "/v3/api-docs",
          "/v3/api-docs/**",
          "/swagger",
          "/swagger-ui/**",
          "/webjars/**",
          "/",
          "/index.html",
          "/static/**",
          "/assets/**",
          "/favicon.ico",
          "/favicon.png",
          "/error");

  private final UpdateGate updateGate;
  private final ObjectMapper objectMapper;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public MandatoryUpdateInterceptor(UpdateGate updateGate, ObjectMapper objectMapper) {
    this.updateGate = updateGate;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if (!updateGate.isMandatoryBlocked()) {
      return true;
    }
    // CORS preflight'ı bloklamayız — preflight bloklanırsa tarayıcı asıl POST'u atmaz ve
    // kullanıcı muğlak "CORS hatası" görür. Preflight geçer, asıl request geldiğinde 426 +
    // ErrorModel JSON döner; web istemci downloadHint'i okuyup kullanıcıyı yönlendirebilir.
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    if (path.isEmpty()) {
      path = "/";
    }
    if (isAllowed(path)) {
      return true;
    }

    UpdateInfo info = updateGate.getPending();
    String latest = info == null ? "?" : info.getLatestVersion();
    String downloadUrl = info == null ? null : info.getDownloadUrl();
    String releaseUrl = info == null ? null : info.getReleaseUrl();

    LOG.warn("Update required: {} {} bloklandı (latest={}).", request.getMethod(), path, latest);

    ErrorModel body =
        new ErrorModel(
            ERROR_CODE,
            "Yeni sürüm v"
                + latest
                + " yayınlandı. Güvenlik ve uyumluluk gereği imzalama servisi yeni "
                + "sürüm kurulana kadar yeni istek kabul etmez. Uygulama içindeki 'İndir' "
                + "butonu ile güncellemeyi tamamlayın.");
    if (downloadUrl != null && !downloadUrl.isEmpty()) {
      body.setDownloadHint(downloadUrl);
    } else if (releaseUrl != null && !releaseUrl.isEmpty()) {
      body.setDownloadHint(releaseUrl);
    }

    response.setStatus(HTTP_UPGRADE_REQUIRED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    byte[] json = objectMapper.writeValueAsBytes(body);
    response.setContentLength(json.length);
    response.getOutputStream().write(json);
    response.getOutputStream().flush();
    return false;
  }

  private boolean isAllowed(String path) {
    for (String pattern : ALLOWLIST_PATTERNS) {
      if (pathMatcher.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }
}
