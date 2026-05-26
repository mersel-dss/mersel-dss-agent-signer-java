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
package io.mersel.dss.agent.api.testsupport;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;

/**
 * Test yardımcısı: {@link PfxTestKey}'i okunabilir bir PKCS12 KeyStore'a + {@link Pkcs11Session}
 * sarmalayıcısına çevirir.
 *
 * <p>JDK 1.8'de PKCS12 default sağlayıcı SUN/SunJSSE'dir; bizim use case'imiz için yeterli. EC PFX
 * için BouncyCastle'a düşülmesi gerekiyorsa {@link #load(PfxTestKey)} ikinci girişimde BC'yi dener.
 */
public final class PfxLoader {

  static {
    // BC: hem RSA-SHA256 hem ECDSA-SHA384'ü tek provider'da destekler;
    // SunJSSE'nin PKCS12 yüklü key'ini BC üzerinden imzalayabiliriz.
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private PfxLoader() {}

  /** PFX'i yükler ve hem KeyStore hem signing material'i döner. */
  public static Loaded load(PfxTestKey key) throws Exception {
    if (!key.isAvailable()) {
      throw new IllegalStateException(
          "PFX dosyası mevcut değil: "
              + key.getAbsolutePath()
              + " — resources/test-certs/README.md'i kontrol edin.");
    }

    KeyStore ks;
    try (InputStream in = new FileInputStream(key.getFile())) {
      ks = KeyStore.getInstance("PKCS12");
      ks.load(in, key.getPassword());
    } catch (Exception primaryFail) {
      // BC fallback (özellikle bazı EC eğri PFX'leri için)
      Provider bc = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
      if (bc == null) throw primaryFail;
      try (InputStream in = new FileInputStream(key.getFile())) {
        ks = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
        ks.load(in, key.getPassword());
      }
    }

    // İmzalama için BC: hem RSA hem ECDSA için tek provider; SunJSSE'nin
    // yüklediği PKCS12 anahtarını BC ile imzalayabiliriz çünkü PrivateKey
    // serializable (RSAPrivateCrtKey / ECPrivateKey, transferred by encoding).
    Provider signingProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);

    // Alias kontrolü: convention "1"; bazen "ne" gibi alternatifler olabilir
    String alias = key.getAlias();
    if (!ks.containsAlias(alias)) {
      java.util.Enumeration<String> e = ks.aliases();
      if (!e.hasMoreElements()) {
        throw new IllegalStateException("PFX boş, alias yok: " + key.getFileName());
      }
      alias = e.nextElement();
    }

    X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
    return new Loaded(key, ks, signingProvider, alias, cert);
  }

  public static final class Loaded implements AutoCloseable {
    public final PfxTestKey key;
    public final KeyStore keyStore;
    public final Provider provider;
    public final String alias;
    public final X509Certificate certificate;

    Loaded(
        PfxTestKey key,
        KeyStore keyStore,
        Provider provider,
        String alias,
        X509Certificate certificate) {
      this.key = key;
      this.keyStore = keyStore;
      this.provider = provider;
      this.alias = alias;
      this.certificate = certificate;
    }

    public Pkcs11Session openSession() {
      return Pkcs11Session.wrapForTest(keyStore, provider, new String(key.getPassword()));
    }

    @Override
    public void close() {
      /* PKCS12 in-memory; cleanup yok */
    }
  }
}
