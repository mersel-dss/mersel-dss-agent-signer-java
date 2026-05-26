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
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

/**
 * {@link RevocationChecker#crlReasonText(int)} ve {@link
 * RevocationChecker#crlEntryReasonText(X509CRLEntry)} davranışı.
 *
 * <p>CRL/OCSP yanıtlarındaki revoke nedeni kodlarının frontend için anlamlı text'e çevrildiğini
 * garanti eder. Sabit tablo mapping'ine ek olarak, BouncyCastle {@code X509v2CRLBuilder} ile
 * in-memory CRL üretilip RFC 5280 §5.3.1 ReasonCode extension'ının (OID 2.5.29.21) doğru parse
 * edildiği doğrulanır — eski projedeki hardcoded mesaj davranışına kıyasla artık reason kodu
 * structured biçimde döner.
 */
class RevocationCheckerReasonTest {

  @Test
  void mapsRfc5280StandardCodes() {
    assertThat(RevocationChecker.crlReasonText(0)).isEqualTo("unspecified");
    assertThat(RevocationChecker.crlReasonText(1)).isEqualTo("keyCompromise");
    assertThat(RevocationChecker.crlReasonText(2)).isEqualTo("cACompromise");
    assertThat(RevocationChecker.crlReasonText(3)).isEqualTo("affiliationChanged");
    assertThat(RevocationChecker.crlReasonText(4)).isEqualTo("superseded");
    assertThat(RevocationChecker.crlReasonText(5)).isEqualTo("cessationOfOperation");
    assertThat(RevocationChecker.crlReasonText(6)).isEqualTo("certificateHold");
    assertThat(RevocationChecker.crlReasonText(8)).isEqualTo("removeFromCRL");
    assertThat(RevocationChecker.crlReasonText(9)).isEqualTo("privilegeWithdrawn");
    assertThat(RevocationChecker.crlReasonText(10)).isEqualTo("aACompromise");
  }

  @Test
  void unknownCodeReturnsLabelWithRawValue() {
    // 7 RFC'de yok (reserved); 99 ileride eklenebilecek bir değer simülasyonu.
    assertThat(RevocationChecker.crlReasonText(7)).isEqualTo("unknown(7)");
    assertThat(RevocationChecker.crlReasonText(99)).isEqualTo("unknown(99)");
    assertThat(RevocationChecker.crlReasonText(-1)).isEqualTo("unknown(-1)");
  }

  @Test
  void crlEntryReasonTextReturnsNullForNullInput() {
    assertThat(RevocationChecker.crlEntryReasonText(null)).isNull();
  }

  /* ---------------- Gerçek CRL entry'leri (BC X509v2CRLBuilder) ---------------- */

  @Test
  void crlEntryReasonTextExtractsKeyCompromiseFromReasonCodeExtension() throws Exception {
    BigInteger serial = BigInteger.valueOf(42L);
    X509CRL crl = buildCrlWithReason(serial, CRLReason.keyCompromise);

    X509CRLEntry entry = crl.getRevokedCertificate(serial);
    assertThat(entry).as("revoke kaydı CRL'de bulunamadı").isNotNull();
    assertThat(RevocationChecker.crlEntryReasonText(entry)).isEqualTo("keyCompromise");
  }

  @Test
  void crlEntryReasonTextExtractsCessationOfOperationFromReasonCodeExtension() throws Exception {
    BigInteger serial = BigInteger.valueOf(7L);
    X509CRL crl = buildCrlWithReason(serial, CRLReason.cessationOfOperation);

    X509CRLEntry entry = crl.getRevokedCertificate(serial);
    assertThat(entry).isNotNull();
    assertThat(RevocationChecker.crlEntryReasonText(entry)).isEqualTo("cessationOfOperation");
  }

  @Test
  void crlEntryReasonTextExtractsSupersededFromReasonCodeExtension() throws Exception {
    BigInteger serial = BigInteger.valueOf(99L);
    X509CRL crl = buildCrlWithReason(serial, CRLReason.superseded);

    X509CRLEntry entry = crl.getRevokedCertificate(serial);
    assertThat(entry).isNotNull();
    assertThat(RevocationChecker.crlEntryReasonText(entry)).isEqualTo("superseded");
  }

  @Test
  void crlEntryReasonTextReturnsNullWhenReasonCodeExtensionAbsent() throws Exception {
    BigInteger serial = BigInteger.valueOf(5L);
    // ReasonCode opsiyoneldir (RFC 5280 §5.3.1); eski/minimal CRL üreticileri (KamuSM Class 3
    // CRL'in bazı eski sürümleri, açık kaynak self-signed test CA'ları) bu extension'ı koymaz —
    // bu senaryoda graceful null dönmeli, NPE atmamalı.
    X509CRL crl = buildCrlWithoutReasonExtension(serial);

    X509CRLEntry entry = crl.getRevokedCertificate(serial);
    assertThat(entry).isNotNull();
    assertThat(RevocationChecker.crlEntryReasonText(entry)).isNull();
  }

  /* ---------------- helpers ---------------- */

  /**
   * BC {@link X509v2CRLBuilder} ile, ReasonCode extension'lı tek revoke kaydı içeren minimal CRL
   * üretir.
   */
  private static X509CRL buildCrlWithReason(BigInteger serial, int reason) throws Exception {
    KeyPair issuerKey = newRsaKeyPair();
    Instant now = Instant.now();

    X509v2CRLBuilder crlBuilder = newCrlBuilder(now);
    crlBuilder.addCRLEntry(serial, Date.from(now.minus(1, ChronoUnit.HOURS)), reason);

    return sign(crlBuilder, issuerKey);
  }

  /**
   * BC {@link X509v2CRLBuilder} ile, ReasonCode extension içermeyen revoke kaydı üretir — {@code
   * addCRLEntry(BigInteger, Date, Extensions)} overload'una boş {@code Extensions} geçilir. RFC
   * 5280 §5.3.1: ReasonCode opsiyoneldir, yokken default "unspecified" sayılır ancak biz extension
   * yokluğunu null mesajla expose ediyoruz (mesaj kirliliğine yol açmasın diye).
   */
  private static X509CRL buildCrlWithoutReasonExtension(BigInteger serial) throws Exception {
    KeyPair issuerKey = newRsaKeyPair();
    Instant now = Instant.now();

    X509v2CRLBuilder crlBuilder = newCrlBuilder(now);
    crlBuilder.addCRLEntry(
        serial, Date.from(now.minus(1, ChronoUnit.HOURS)), new Extensions(new Extension[0]));

    return sign(crlBuilder, issuerKey);
  }

  private static X509v2CRLBuilder newCrlBuilder(Instant now) {
    X509v2CRLBuilder crlBuilder =
        new X509v2CRLBuilder(new X500Name("CN=Test CRL Issuer"), Date.from(now));
    crlBuilder.setNextUpdate(Date.from(now.plus(7, ChronoUnit.DAYS)));
    return crlBuilder;
  }

  private static X509CRL sign(X509v2CRLBuilder crlBuilder, KeyPair issuerKey) throws Exception {
    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey.getPrivate());
    X509CRLHolder holder = crlBuilder.build(signer);
    return new JcaX509CRLConverter().getCRL(holder);
  }

  private static KeyPair newRsaKeyPair() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    return gen.generateKeyPair();
  }
}
