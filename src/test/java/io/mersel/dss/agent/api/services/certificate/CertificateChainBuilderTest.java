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
package io.mersel.dss.agent.api.services.certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.config.SignerProperties;

/**
 * {@link CertificateChainBuilder} davranışını mocked HTTP fetcher ve dinamik olarak üretilmiş 3
 * katmanlı sertifika hiyerarşisi (Root CA → Intermediate CA → End-entity) ile doğrular.
 */
class CertificateChainBuilderTest {

  @BeforeAll
  static void registerBouncyCastle() {
    if (java.security.Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      java.security.Security.addProvider(new BouncyCastleProvider());
    }
  }

  @Test
  void passthroughReturnsInputUnchangedWhenAiaDisabled() throws Exception {
    Hierarchy h = newHierarchy();
    Certificate[] input = new Certificate[] {h.endEntity};

    Certificate[] out = CertificateChainBuilder.passthrough().build(input);

    assertSame(input, out, "AIA kapalıyken giriş aynen dönmeli (alokasyon yok).");
  }

  @Test
  void buildFollowsAiaAndAddsIntermediateAndRoot() throws Exception {
    Hierarchy h = newHierarchy();

    Map<String, byte[]> downloads = new HashMap<String, byte[]>();
    downloads.put(h.endEntityAiaUrl, h.intermediate.getEncoded());
    downloads.put(h.intermediateAiaUrl, h.root.getEncoded());
    AtomicInteger calls = new AtomicInteger();
    Function<String, byte[]> fetcher =
        url -> {
          calls.incrementAndGet();
          return downloads.get(url);
        };

    CertificateChainBuilder builder = newBuilder(true, 8, fetcher);

    Certificate[] out = builder.build(new Certificate[] {h.endEntity});

    assertEquals(3, out.length, "End-entity + intermediate + root beklenir");
    assertEquals(h.endEntity, out[0]);
    assertEquals(h.intermediate, out[1]);
    assertEquals(h.root, out[2]);
    assertEquals(2, calls.get(), "Tam olarak iki AIA hop yapılmalı");
  }

  @Test
  void buildStopsAtSelfSignedRootWithoutHttpCalls() throws Exception {
    Hierarchy h = newHierarchy();
    AtomicInteger calls = new AtomicInteger();

    CertificateChainBuilder builder =
        newBuilder(
            true,
            8,
            url -> {
              calls.incrementAndGet();
              return null;
            });

    Certificate[] out = builder.build(new Certificate[] {h.root});

    assertEquals(1, out.length);
    assertSame(h.root, out[0]);
    assertEquals(0, calls.get(), "Self-signed root için HTTP çağrısı yapılmamalı");
  }

  @Test
  void buildStopsWhenDownloadFails() throws Exception {
    Hierarchy h = newHierarchy();

    CertificateChainBuilder builder = newBuilder(true, 8, url -> null);

    Certificate[] out = builder.build(new Certificate[] {h.endEntity});

    assertEquals(1, out.length, "İndirme başarısız → mevcut zincir kullanılır, exception YOK");
    assertSame(h.endEntity, out[0]);
  }

  @Test
  void buildRespectsMaxDepth() throws Exception {
    Hierarchy h = newHierarchy();
    Map<String, byte[]> downloads = new HashMap<String, byte[]>();
    downloads.put(h.endEntityAiaUrl, h.intermediate.getEncoded());
    downloads.put(h.intermediateAiaUrl, h.root.getEncoded());

    CertificateChainBuilder builder = newBuilder(true, 2, downloads::get);

    Certificate[] out = builder.build(new Certificate[] {h.endEntity});

    assertEquals(2, out.length, "maxDepth=2 → en fazla 2 sertifika");
    assertSame(h.endEntity, out[0]);
    assertEquals(h.intermediate, out[1]);
  }

  @Test
  void extractCaIssuersUrlsReadsAiaExtension() throws Exception {
    Hierarchy h = newHierarchy();

    java.util.List<String> urls = CertificateChainBuilder.extractCaIssuersUrls(h.endEntity);

    assertEquals(1, urls.size());
    assertEquals(h.endEntityAiaUrl, urls.get(0));
  }

  @Test
  void pickIssuerSelectsBySubjectMatchFromBundle() throws Exception {
    Hierarchy h = newHierarchy();
    byte[] body = h.intermediate.getEncoded();
    String expected = h.intermediate.getSubjectX500Principal().getName("CANONICAL");

    X509Certificate match =
        CertificateChainBuilder.pickIssuer(body, expected, new java.util.HashSet<String>());

    assertNotNull(match);
    assertEquals(h.intermediate, match);
  }

  /* ------------------------------------------------------------------ */
  /* Test scaffolding: 3 katmanlı mini PKI hiyerarşisi                  */
  /* ------------------------------------------------------------------ */

  private static CertificateChainBuilder newBuilder(
      boolean aiaEnabled, int maxDepth, Function<String, byte[]> fetcher) {
    SignerProperties.Chain props = new SignerProperties.Chain();
    props.setAiaEnabled(aiaEnabled);
    props.setMaxDepth(maxDepth);
    props.setHttpTimeoutMs(1000);
    return new CertificateChainBuilder(props, fetcher);
  }

  private static Hierarchy newHierarchy() throws Exception {
    KeyPair rootKp = newRsaKey();
    KeyPair interKp = newRsaKey();
    KeyPair eeKp = newRsaKey();

    X500Principal rootDn = new X500Principal("CN=Test Root CA,O=Mersel Test,C=TR");
    X500Principal interDn = new X500Principal("CN=Test Intermediate CA,O=Mersel Test,C=TR");
    X500Principal eeDn = new X500Principal("CN=Test End Entity,O=Mersel Test,C=TR");

    String interAiaUrl = "http://aia.mersel.test/intermediate.cer";
    String eeAiaUrl = "http://aia.mersel.test/endentity.cer";

    X509Certificate root =
        buildCert(rootDn, rootDn, rootKp, rootKp.getPrivate(), true, null, BigInteger.ONE);
    X509Certificate intermediate =
        buildCert(
            interDn,
            rootDn,
            interKp,
            rootKp.getPrivate(),
            true,
            interAiaUrl,
            BigInteger.valueOf(2));
    X509Certificate endEntity =
        buildCert(
            eeDn, interDn, eeKp, interKp.getPrivate(), false, eeAiaUrl, BigInteger.valueOf(3));

    assertTrue(
        endEntity.getIssuerX500Principal().equals(intermediate.getSubjectX500Principal()),
        "Sanity: end-entity issuer == intermediate subject");

    Hierarchy h = new Hierarchy();
    h.root = root;
    h.intermediate = intermediate;
    h.endEntity = endEntity;
    h.endEntityAiaUrl = eeAiaUrl;
    h.intermediateAiaUrl = interAiaUrl;
    return h;
  }

  private static KeyPair newRsaKey() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    return g.generateKeyPair();
  }

  private static X509Certificate buildCert(
      X500Principal subject,
      X500Principal issuer,
      KeyPair subjectKp,
      java.security.PrivateKey issuerKey,
      boolean ca,
      String caIssuersAiaUrl,
      BigInteger serial)
      throws Exception {
    long now = System.currentTimeMillis();
    Date notBefore = new Date(now - 60_000L);
    Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000L);

    X509v3CertificateBuilder bld =
        new JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, subject, subjectKp.getPublic());
    bld.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));

    if (caIssuersAiaUrl != null) {
      AccessDescription ad =
          new AccessDescription(
              X509ObjectIdentifiers.id_ad_caIssuers,
              new GeneralName(GeneralName.uniformResourceIdentifier, caIssuersAiaUrl));
      AuthorityInformationAccess aia =
          AuthorityInformationAccess.getInstance(
              new org.bouncycastle.asn1.DERSequence(ad.toASN1Primitive()));
      bld.addExtension(Extension.authorityInfoAccess, false, aia);
    }

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(issuerKey);

    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(bld.build(signer));
  }

  private static class Hierarchy {
    X509Certificate root;
    X509Certificate intermediate;
    X509Certificate endEntity;
    String endEntityAiaUrl;
    String intermediateAiaUrl;
  }
}
