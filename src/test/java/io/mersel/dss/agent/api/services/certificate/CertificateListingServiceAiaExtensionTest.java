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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

/**
 * {@link CertificateListingService#extendChainViaAia} davranışı.
 *
 * <p>Bu testin asıl amacı: KamuSM kartlarında token üzerinde sadece end-entity yazılı senaryoyu
 * regression test etmek. Eski davranış: {@code buildChainFromBundle} leaf-only chain dönüyordu →
 * {@link RevocationChecker#findIssuer} null buluyor → status=UNKNOWN. Yeni davranış: chain AIA
 * üzerinden tamamlanıyor → issuer bulunuyor → OCSP/CRL çalışıyor.
 *
 * <p>{@link CertificateChainBuilder} mock'lanır; gerçek HTTP çıkışı tetiklenmez.
 */
class CertificateListingServiceAiaExtensionTest {

  @Test
  void leafOnlyChainExtendedToLeafPlusIssuerWhenBuilderProvidesIssuer() throws Exception {
    Chain c = newChain();

    CertificateChainBuilder builder = mock(CertificateChainBuilder.class);
    when(builder.build(any(Certificate[].class)))
        .thenReturn(new Certificate[] {c.leaf, c.intermediate, c.root});

    CertificateListingService svc = new CertificateListingService(null, null, builder);

    X509Certificate[] localOnly = new X509Certificate[] {c.leaf}; // KamuSM kart senaryosu
    X509Certificate[] extended = svc.extendChainViaAia(localOnly);

    assertThat(extended).hasSize(3);
    assertThat(extended[0]).isEqualTo(c.leaf);
    assertThat(extended[1]).isEqualTo(c.intermediate);
    assertThat(extended[2]).isEqualTo(c.root);
    verify(builder).build(any(Certificate[].class));
  }

  @Test
  void chainNotShrunkWhenBuilderReturnsSmallerArray() throws Exception {
    Chain c = newChain();

    // Defensive: builder'ın bir bug yüzünden gelen zincirden daha az cert döndürmesi durumunda,
    // listingService girdiği zinciri kaybetmemeli.
    CertificateChainBuilder builder = mock(CertificateChainBuilder.class);
    when(builder.build(any(Certificate[].class))).thenReturn(new Certificate[0]);

    CertificateListingService svc = new CertificateListingService(null, null, builder);

    X509Certificate[] input = new X509Certificate[] {c.leaf, c.intermediate};
    X509Certificate[] result = svc.extendChainViaAia(input);

    assertThat(result).isSameAs(input); // input'a fall-back
  }

  @Test
  void chainPassedThroughWhenBuilderThrows() throws Exception {
    Chain c = newChain();

    CertificateChainBuilder builder = mock(CertificateChainBuilder.class);
    when(builder.build(any(Certificate[].class)))
        .thenThrow(new RuntimeException("AIA HTTP timeout"));

    CertificateListingService svc = new CertificateListingService(null, null, builder);

    X509Certificate[] input = new X509Certificate[] {c.leaf};
    X509Certificate[] result = svc.extendChainViaAia(input);

    // Network hatası listelemeyi kesmemeli — input'la devam et.
    assertThat(result).isSameAs(input);
  }

  @Test
  void nullBuilderReturnsInputUntouched() throws Exception {
    Chain c = newChain();

    // CertificateChainBuilder DI'ile inject edilmeyen test'lerde null tolere edilmeli.
    CertificateListingService svc = new CertificateListingService(null, null, null);

    X509Certificate[] input = new X509Certificate[] {c.leaf};
    X509Certificate[] result = svc.extendChainViaAia(input);
    assertThat(result).isSameAs(input);
  }

  @Test
  void emptyOrNullChainHandledGracefully() {
    CertificateChainBuilder builder = mock(CertificateChainBuilder.class);
    CertificateListingService svc = new CertificateListingService(null, null, builder);

    assertThat(svc.extendChainViaAia(null)).isNull();
    assertThat(svc.extendChainViaAia(new X509Certificate[0])).isEmpty();
    verify(builder, never()).build(any(Certificate[].class)); // gereksiz HTTP yapılmaz
  }

  /* ---------------- helpers ---------------- */

  private static Chain newChain() throws Exception {
    KeyPair rootKp = newRsa();
    KeyPair intKp = newRsa();
    KeyPair leafKp = newRsa();

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

  private static KeyPair newRsa() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    return gen.generateKeyPair();
  }

  private static X509Certificate buildCert(
      X500Name subject, X500Name issuer, PublicKey subjectPub, PrivateKey issuerPriv)
      throws Exception {
    Instant now = Instant.now();
    JcaX509v3CertificateBuilder builder =
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
