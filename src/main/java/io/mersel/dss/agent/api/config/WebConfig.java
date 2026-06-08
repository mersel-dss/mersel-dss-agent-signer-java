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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.mersel.dss.agent.api.services.update.VersionProvider;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Web katmanı yapılandırması: CORS politikası + OpenAPI metadata.
 *
 * <h2>CORS politikası</h2>
 *
 * <p>Akıllı kart imzalayıcı bir <em>desktop daemon</em>'dur ve farklı domain'lerdeki müşteri
 * uygulamalarından (B2B web portalleri, e-fatura paneli, ön muhasebe uygulamaları, vs.) çağrılır —
 * bu sektörün yerleşik beklentisidir. Bu nedenle <strong>varsayılan politika tüm origin'lere
 * açıktır</strong> ({@code allowedOriginPatterns="*"}).
 *
 * <p>Sıkılaştırmak isteyen kurumsal kurulumlar {@code mersel.signer.cors-allowed-origins}
 * property'siyle (virgülle ayrılmış pattern listesi, Spring {@code addAllowedOriginPatterns}
 * sözdizimi — {@code https://*.example.com} gibi wildcard'lı) sınırlama getirebilir; bu durumda
 * yalnız listedeki origin'ler kabul edilir.
 *
 * <h2>Güvenlik notu</h2>
 *
 * <p>Açık CORS, daemon'ın kendisini "savunmasız" yapmaz çünkü hassas işlemler (sign / pin validate)
 * zaten kullanıcının PIN'ini gerektirir; PIN client tarafından her istekte gönderilir. Ancak iki
 * noktayı bilmek gerekir:
 *
 * <ol>
 *   <li>{@code GET /smartcard/certificate} sertifika listesini (TC kimlik / VKN, ad soyad) PIN'siz
 *       döner. Açık CORS'la birlikte herhangi bir kötücül site bunu okuyabilir. Aşağıdaki Host
 *       header savunması bu yüzden öneriyor.
 *   <li>DNS rebinding saldırısı (evil.com → 127.0.0.1) CORS'u atlar; klasik mitigation <em>Host
 *       header allowlist</em> uygulamaktır ({@code localhost}, {@code 127.0.0.1}, {@code [::1]}).
 *       Şu an aktif değil; ileride filter eklenebilir.
 * </ol>
 *
 * <p>OpenAPI {@code info} bloğu sürüm bilgisini {@link VersionProvider}'dan okur (MANIFEST.MF →
 * pom.properties → fallback); lisans bilgisi proje kök dizinindeki {@code LICENSE} dosyasındaki
 * "Apache License 2.0 + Mersel Brand Attribution Addendum" sözleşmesinin SPDX referansını taşır.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  /**
   * Varsayılan: her origin kabul edilir. Spring 5.3+ {@code allowedOriginPatterns} pattern'ı {@code
   * *} ile kullanıldığında {@code allowCredentials=true} ile birlikte de çalışır — gelen {@code
   * Origin} header'ı response'a yansıtılır (literal {@code *} değil).
   */
  static final List<String> DEFAULT_OPEN_PATTERNS =
      Collections.unmodifiableList(Arrays.asList("*"));

  private static final String LICENSE_NAME = "Apache-2.0 WITH LicenseRef-Mersel-Brand-Attribution";
  private static final String LICENSE_URL =
      "https://github.com/mersel-dss/mersel-dss-agent-signer-java/blob/main/LICENSE";

  private final List<String> allowedOriginPatterns;
  private final VersionProvider versionProvider;
  private final MandatoryUpdateInterceptor mandatoryUpdateInterceptor;

  public WebConfig(
      @Value("${mersel.signer.cors-allowed-origins:}") String allowedOriginsCsv,
      VersionProvider versionProvider,
      MandatoryUpdateInterceptor mandatoryUpdateInterceptor) {
    this.allowedOriginPatterns = parsePatterns(allowedOriginsCsv);
    this.versionProvider = versionProvider;
    this.mandatoryUpdateInterceptor = mandatoryUpdateInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // Interceptor kendi içinde allowlist match yapıyor; ek olarak burada `addPathPatterns("/**")`
    // ile tüm path'leri kapsıyoruz. Spotless veya readability açısından allowlist'i hem
    // interceptor'ın preHandle içinde hem burada {@code excludePathPatterns} olarak duplicate
    // ETMİYORUZ — tek truth-source interceptor sınıfı. Aksi halde iki yer arasında drift olur.
    registry.addInterceptor(mandatoryUpdateInterceptor).addPathPatterns("/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    // {@code allowedOriginPatterns("*")} + {@code allowCredentials(true)} kombinasyonu Spring
    // 5.3+'ta legaldir; framework gelen Origin'i response'ta birebir yansıtır (literal {@code *}
    // değil). Bu sayede browser cookie/credential gönderebileceği halde response kabul eder.
    registry
        .addMapping("/**")
        .allowedOriginPatterns(allowedOriginPatterns.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        .allowedHeaders("*")
        .exposedHeaders(
            "Content-Disposition",
            "X-Timestamp-Time",
            "X-Timestamp-TSA",
            "X-Timestamp-Serial",
            "X-Timestamp-Hash-Algorithm",
            "X-Timestamp-Nonce")
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

  /**
   * CSV {@code mersel.signer.cors-allowed-origins} değerini pattern listesine çevirir.
   *
   * <p>Boş / null / yalnız whitespace giriş → {@link #DEFAULT_OPEN_PATTERNS} (her origin). Geçerli
   * entry'ler (boş olmayan, trim'lenmiş) varsa onlar kullanılır; tüm entry'ler boşsa yine default'a
   * düşülür. Bu davranış sayesinde admin {@code
   * mersel.signer.cors-allowed-origins=https://app.example.com} gibi tek pattern verirse
   * <em>sadece</em> o origin geçer; geri kalanı reddeder.
   *
   * <p>Test edilebilirlik için package-private static; test sınıfı default fallback ve CSV parsing
   * davranışını doğrulayabilir.
   */
  static List<String> parsePatterns(String csv) {
    if (StringUtils.isBlank(csv)) {
      return DEFAULT_OPEN_PATTERNS;
    }
    List<String> parsed =
        Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    return parsed.isEmpty() ? DEFAULT_OPEN_PATTERNS : parsed;
  }
}
