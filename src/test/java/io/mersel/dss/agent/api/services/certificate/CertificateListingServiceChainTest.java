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

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

/**
 * {@link CertificateListingService#buildChainFromBundle} subject-issuer matching mantığı testleri.
 * Gerçek 3-katmanlı (Root → Intermediate → Leaf) RSA chain'i BouncyCastle ile üretilir; bundle'a
 * hangi cert'lerin konduğuna göre döndürülen zincirin doğru kesildiği doğrulanır.
 */
class CertificateListingServiceChainTest {

  @Test
  void fullChainReturnedWhenBundleContainsAllCerts() throws Exception {
    Chain c = newChain();
    X509Certificate[] result =
        CertificateListingService.buildChainFromBundle(
            c.leaf, Arrays.asList(c.leaf, c.intermediate, c.root));

    assertThat(result).hasSize(3);
    assertThat(result[0]).isEqualTo(c.leaf);
    assertThat(result[1]).isEqualTo(c.intermediate);
    assertThat(result[2]).isEqualTo(c.root);
  }

  @Test
  void chainStopsWhenIntermediateMissing() throws Exception {
    Chain c = newChain();
    X509Certificate[] result =
        CertificateListingService.buildChainFromBundle(c.leaf, Arrays.asList(c.leaf, c.root));

    assertThat(result).hasSize(1);
    assertThat(result[0]).isEqualTo(c.leaf);
  }

  @Test
  void leafOnlyChainWhenBundleEmpty() throws Exception {
    Chain c = newChain();
    X509Certificate[] result =
        CertificateListingService.buildChainFromBundle(c.leaf, Collections.singletonList(c.leaf));

    assertThat(result).hasSize(1);
    assertThat(result[0]).isEqualTo(c.leaf);
  }

  @Test
  void selfSignedRootReturnedAsSingleElement() throws Exception {
    Chain c = newChain();
    X509Certificate[] result =
        CertificateListingService.buildChainFromBundle(
            c.root, Arrays.asList(c.leaf, c.intermediate, c.root));

    assertThat(result).hasSize(1);
    assertThat(result[0]).isEqualTo(c.root);
  }

  @Test
  void nullLeafReturnsEmptyArray() {
    X509Certificate[] result =
        CertificateListingService.buildChainFromBundle(
            null, Collections.<X509Certificate>emptyList());
    assertThat(result).isEmpty();
  }

  @Test
  void noInfiniteLoopWhenBundleIsCyclic() throws Exception {
    // Patolojik durum: Root sertifikasını bundle'a iki kez koyalım (aynı identity); identity-based
    // visited guard'ı sayesinde sonsuz döngü olmamalı, döngüden çıkmalı.
    Chain c = newChain();
    X509Certificate[] result =
        CertificateListingService.buildChainFromBundle(
            c.leaf, Arrays.asList(c.leaf, c.intermediate, c.root, c.root));

    assertThat(result).hasSize(3);
    assertThat(result[2]).isEqualTo(c.root);
  }

  /* ---------------- helpers ---------------- */

  private static Chain newChain() throws Exception {
    KeyPair rootKp = newRsaKeyPair();
    KeyPair intKp = newRsaKeyPair();
    KeyPair leafKp = newRsaKeyPair();

    X500Name rootDn = new X500Name("CN=Test Root CA");
    X500Name intDn = new X500Name("CN=Test Intermediate CA");
    X500Name leafDn = new X500Name("CN=Test Leaf, O=Mersel");

    X509Certificate root = buildCert(rootDn, rootDn, rootKp.getPublic(), rootKp.getPrivate());
    X509Certificate intermediate = buildCert(intDn, rootDn, intKp.getPublic(), rootKp.getPrivate());
    X509Certificate leaf = buildCert(leafDn, intDn, leafKp.getPublic(), intKp.getPrivate());

    Chain c = new Chain();
    c.root = root;
    c.intermediate = intermediate;
    c.leaf = leaf;
    return c;
  }

  private static KeyPair newRsaKeyPair() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    return gen.generateKeyPair();
  }

  private static X509Certificate buildCert(
      X500Name subject, X500Name issuer, PublicKey subjectPub, PrivateKey issuerPriv)
      throws Exception {
    Instant now = Instant.now();
    X509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(System.nanoTime()),
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(365, ChronoUnit.DAYS)),
            subject,
            subjectPub);
    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuerPriv);
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }

  private static final class Chain {
    X509Certificate root;
    X509Certificate intermediate;
    X509Certificate leaf;
  }
}
