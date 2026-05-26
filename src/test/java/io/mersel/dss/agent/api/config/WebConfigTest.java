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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.services.update.VersionProvider;
import io.swagger.v3.oas.models.OpenAPI;

/**
 * {@link WebConfig#merselSignerOpenApi()} bean'inin OpenAPI {@code info} bloğunu doğru kurduğunu
 * doğrular.
 *
 * <p>Regresyon yakalama hedefi:
 *
 * <ul>
 *   <li>Lisans bloğu Apache-2.0 + Brand Attribution Addendum SPDX referansını taşır (eskiden
 *       yanlışlıkla "MIT" diyordu — LICENSE/NOTICE/pom.xml ile çelişkiliydi).
 *   <li>Sürüm hard-coded değil, {@link VersionProvider} üzerinden okunur (build artefakt'tan akar;
 *       her release'de WebConfig kaynağına dokunmaya gerek kalmaz).
 * </ul>
 */
class WebConfigTest {

  @Test
  void openApiLicenseReferencesApacheWithBrandAttributionAddendum() {
    VersionProvider versionProvider = mock(VersionProvider.class);
    when(versionProvider.currentVersion()).thenReturn("9.9.9-test");

    WebConfig webConfig =
        new WebConfig("", versionProvider, mock(MandatoryUpdateInterceptor.class));
    OpenAPI openApi = webConfig.merselSignerOpenApi();

    assertThat(openApi.getInfo()).isNotNull();
    assertThat(openApi.getInfo().getLicense()).isNotNull();
    assertThat(openApi.getInfo().getLicense().getName())
        .isEqualTo("Apache-2.0 WITH LicenseRef-Mersel-Brand-Attribution");
    assertThat(openApi.getInfo().getLicense().getUrl())
        .isEqualTo("https://github.com/mersel-dss/mersel-dss-agent-signer-java/blob/main/LICENSE");
  }

  @Test
  void openApiVersionComesFromVersionProvider() {
    VersionProvider versionProvider = mock(VersionProvider.class);
    when(versionProvider.currentVersion()).thenReturn("3.1.4-rc.7");

    WebConfig webConfig =
        new WebConfig("", versionProvider, mock(MandatoryUpdateInterceptor.class));
    OpenAPI openApi = webConfig.merselSignerOpenApi();

    assertThat(openApi.getInfo().getVersion()).isEqualTo("3.1.4-rc.7");
    assertThat(openApi.getInfo().getTitle()).isEqualTo("Mersel DSS Agent Signer");
    assertThat(openApi.getInfo().getContact()).isNotNull();
    assertThat(openApi.getInfo().getContact().getName()).isEqualTo("Mersel DSS Team");
  }
}
