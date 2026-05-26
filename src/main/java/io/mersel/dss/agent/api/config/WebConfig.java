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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.mersel.dss.agent.api.services.update.VersionProvider;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Web katmanı yapılandırması: CORS politikası + OpenAPI metadata.
 *
 * <p>Daemon yerel makinede çalıştığı için varsayılan CORS politikası yalnızca <em>loopback</em>
 * origin'lerine açıktır. Ek origin'ler {@code mersel.signer.cors-allowed-origins} ile (virgülle
 * ayrılmış pattern listesi) tanımlanır; her pattern Spring'in {@code addAllowedOriginPatterns}
 * sözdizimini ({@code *} wildcard'lı) kullanır.
 *
 * <p>OpenAPI {@code info} bloğu sürüm bilgisini {@link VersionProvider}'dan okur (MANIFEST.MF →
 * pom.properties → fallback); lisans bilgisi proje kök dizinindeki {@code LICENSE} dosyasındaki
 * "Apache License 2.0 + Mersel Brand Attribution Addendum" sözleşmesinin SPDX referansını taşır.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private static final List<String> DEFAULT_LOOPBACK_PATTERNS =
      Collections.unmodifiableList(
          Arrays.asList(
              "http://localhost:*",
              "https://localhost:*",
              "http://127.0.0.1:*",
              "https://127.0.0.1:*"));

  private static final String LICENSE_NAME = "Apache-2.0 WITH LicenseRef-Mersel-Brand-Attribution";
  private static final String LICENSE_URL =
      "https://github.com/mersel-dss/mersel-dss-agent-signer-java/blob/main/LICENSE";

  private final List<String> allowedOriginPatterns;
  private final VersionProvider versionProvider;

  public WebConfig(
      @Value("${mersel.signer.cors-allowed-origins:}") String allowedOriginsCsv,
      VersionProvider versionProvider) {
    this.allowedOriginPatterns = parsePatterns(allowedOriginsCsv);
    this.versionProvider = versionProvider;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns(allowedOriginPatterns.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        .allowedHeaders("*")
        .exposedHeaders("Content-Disposition")
        .allowCredentials(true)
        .maxAge(3600);
  }

  @Bean
  public OpenAPI merselSignerOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Mersel DSS Agent Signer")
                .version(versionProvider.currentVersion())
                .description(
                    "Yerel PKCS#11 akıllı kart imzalama servisi: "
                        + "PAdES-B, XAdES-BES, XAdES CounterSignature ve "
                        + "GİB e-Fatura başvurusu. Spring Boot 2.7 + JDK 1.8.")
                .contact(new Contact().name("Mersel DSS Team").url("https://github.com/mersel-dss"))
                .license(new License().name(LICENSE_NAME).url(LICENSE_URL)));
  }

  private static List<String> parsePatterns(String csv) {
    if (StringUtils.isBlank(csv)) {
      return DEFAULT_LOOPBACK_PATTERNS;
    }
    List<String> parsed =
        Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    return parsed.isEmpty() ? DEFAULT_LOOPBACK_PATTERNS : parsed;
  }
}
