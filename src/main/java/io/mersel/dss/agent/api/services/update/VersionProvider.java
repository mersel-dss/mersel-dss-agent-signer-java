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
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.SignerApplication;

/**
 * Çalışan daemon'un sürümünü Maven build artefakt'larından okur.
 *
 * <p>Sıralama:
 *
 * <ol>
 *   <li>{@code MANIFEST.MF} → {@code Implementation-Version} (executable jar)
 *   <li>{@code META-INF/maven/io.mersel.dss/mersel-dss-agent-signer-api/pom.properties} → {@code
 *       version} (Maven build her zaman bunu üretir, IDE/{@code mvn spring-boot:run} için fallback)
 *   <li>{@link #FALLBACK_VERSION} — geliştirme ortamında (resource'lar olmadan IDE çalıştırması)
 * </ol>
 *
 * <p>Test'te {@code subclass + override resolve()} ile stub'lanabilir.
 */
@Component
public class VersionProvider {

  private static final Logger LOG = LoggerFactory.getLogger(VersionProvider.class);

  static final String FALLBACK_VERSION = "dev";
  private static final String POM_PROPS_RESOURCE =
      "/META-INF/maven/io.mersel.dss/mersel-dss-agent-signer-api/pom.properties";

  private final String cachedVersion;

  public VersionProvider() {
    this.cachedVersion = resolve();
  }

  /** Resolved sürüm. Boş string asla dönmez; en azından {@link #FALLBACK_VERSION}. */
  public String currentVersion() {
    return cachedVersion;
  }

  /** Cache'siz tekrar resolve eder (test'te override için protected). */
  protected String resolve() {
    String fromManifest = readFromManifest();
    if (isMeaningful(fromManifest)) {
      LOG.debug("Sürüm MANIFEST.MF'ten okundu: {}", fromManifest);
      return fromManifest;
    }
    String fromPomProps = readFromPomProperties();
    if (isMeaningful(fromPomProps)) {
      LOG.debug("Sürüm pom.properties'ten okundu: {}", fromPomProps);
      return fromPomProps;
    }
    LOG.debug("Sürüm okunamadı (manifest + pom.properties boş). Fallback: {}", FALLBACK_VERSION);
    return FALLBACK_VERSION;
  }

  private static String readFromManifest() {
    try {
      Package pkg = SignerApplication.class.getPackage();
      if (pkg == null) {
        return null;
      }
      return pkg.getImplementationVersion();
    } catch (RuntimeException re) {
      return null;
    }
  }

  private static String readFromPomProperties() {
    try (InputStream stream = VersionProvider.class.getResourceAsStream(POM_PROPS_RESOURCE)) {
      if (stream == null) {
        return null;
      }
      Properties props = new Properties();
      props.load(stream);
      return props.getProperty("version");
    } catch (IOException ioe) {
      return null;
    }
  }

  private static boolean isMeaningful(String v) {
    return v != null && !v.trim().isEmpty();
  }
}
