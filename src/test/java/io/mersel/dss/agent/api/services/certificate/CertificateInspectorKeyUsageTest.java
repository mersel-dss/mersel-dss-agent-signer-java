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
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.qualified.QCStatement;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.models.enums.CertificatePurpose;
import io.mersel.dss.agent.api.models.enums.ExtendedKeyUsage;
import io.mersel.dss.agent.api.models.enums.KeyUsage;
import io.mersel.dss.agent.api.models.enums.TurkishCertificatePolicy;

/**
 * KeyUsage / EKU / CertificatePolicies / QCStatements parse'ı + iş amacı türetimi unit testleri.
 * BouncyCastle ile self-signed RSA sertifikalar üretip {@link CertificateInspector}'a verir.
 */
class CertificateInspectorKeyUsageTest {

  private static final String OID_MALI_MUHUR_SIGNING = "2.16.792.1.2.1.1.5.7.50.1";
  private static final String OID_MALI_MUHUR_ENCRYPTION = "2.16.792.1.2.1.1.5.7.50.2";
  private static final String OID_KAMU_SM_QES_INDIVIDUAL = "2.16.792.1.61.0.1.5070.1.1";
  private static final String OID_QC_COMPLIANCE = "0.4.0.1862.1.1";
  private static final String OID_QC_SSCD = "0.4.0.1862.1.4";

  private final CertificateInspector inspector = new CertificateInspector(null);

  /* ---------------- KeyUsage / EKU karar ağacı ---------------- */

  @Test
  void signingOnlyClassifiedAsSigning() throws Exception {
    int ku =
        org.bouncycastle.asn1.x509.KeyUsage.digitalSignature
            | org.bouncycastle.asn1.x509.KeyUsage.nonRepudiation;
    X509Certificate cert = buildCert(ku, null, null, null);

    Set<KeyUsage> set = inspector.keyUsage(cert);
    assertThat(set).contains(KeyUsage.DIGITAL_SIGNATURE, KeyUsage.NON_REPUDIATION);
    assertThat(set).doesNotContain(KeyUsage.KEY_ENCIPHERMENT, KeyUsage.KEY_CERT_SIGN);
    assertThat(inspector.purpose(cert)).isEqualTo(CertificatePurpose.SIGNING);
  }

  @Test
  void encryptionOnlyClassifiedAsEncryption() throws Exception {
    int ku = org.bouncycastle.asn1.x509.KeyUsage.keyEncipherment;
    X509Certificate cert = buildCert(ku, null, null, null);

    Set<KeyUsage> set = inspector.keyUsage(cert);
    assertThat(set).containsExactly(KeyUsage.KEY_ENCIPHERMENT);
    assertThat(inspector.purpose(cert)).isEqualTo(CertificatePurpose.ENCRYPTION);
  }

  @Test
  void mixedDigitalSigPlusKeyEnciphermentClassifiedAsMixed() throws Exception {
    int ku =
        org.bouncycastle.asn1.x509.KeyUsage.digitalSignature
            | org.bouncycastle.asn1.x509.KeyUsage.keyEncipherment;
    X509Certificate cert = buildCert(ku, null, null, null);
    assertThat(inspector.purpose(cert)).isEqualTo(CertificatePurpose.MIXED);
  }

  @Test
  void caBitsClassifiedAsOther() throws Exception {
    int ku =
        org.bouncycastle.asn1.x509.KeyUsage.keyCertSign
            | org.bouncycastle.asn1.x509.KeyUsage.cRLSign;
    X509Certificate cert = buildCert(ku, null, null, null);
    assertThat(inspector.purpose(cert)).isEqualTo(CertificatePurpose.OTHER);
  }

  @Test
  void tlsClientCertClassifiedAsAuthentication() throws Exception {
    int ku = org.bouncycastle.asn1.x509.KeyUsage.digitalSignature;
    X509Certificate cert =
        buildCert(ku, new KeyPurposeId[] {KeyPurposeId.id_kp_clientAuth}, null, null);

    Set<ExtendedKeyUsage> eku = inspector.extendedKeyUsage(cert).getTyped();
    assertThat(eku).contains(ExtendedKeyUsage.CLIENT_AUTH);
    assertThat(inspector.purpose(cert)).isEqualTo(CertificatePurpose.AUTHENTICATION);
  }

  @Test
  void emptyExtensionsClassifiedAsOther() throws Exception {
    X509Certificate cert = buildCert(0, null, null, null);
    assertThat(inspector.keyUsage(cert)).isEmpty();
    assertThat(inspector.extendedKeyUsage(cert).getTyped()).isEmpty();
    assertThat(inspector.certificatePolicies(cert).getAllOids()).isEmpty();
    assertThat(inspector.qcStatementOids(cert)).isEmpty();
    assertThat(inspector.purpose(cert)).isEqualTo(CertificatePurpose.OTHER);
  }

  /* ---------------- EKU OID görünürlüğü ---------------- */

  @Test
  void ekuAllOidsExposesBothMappedAndUnknownOids() throws Exception {
    int ku = org.bouncycastle.asn1.x509.KeyUsage.digitalSignature;
    KeyPurposeId customOid = KeyPurposeId.getInstance(new ASN1ObjectIdentifier("1.2.3.4.5"));
    X509Certificate cert =
        buildCert(ku, new KeyPurposeId[] {KeyPurposeId.id_kp_clientAuth, customOid}, null, null);

    CertificateInspector.ExtendedKeyUsageResult ekuRes = inspector.extendedKeyUsage(cert);

    // allOids hem mapped (CLIENT_AUTH OID'i) hem unknown'u içerir.
    assertThat(ekuRes.getAllOids())
        .containsExactlyInAnyOrder(ExtendedKeyUsage.CLIENT_AUTH.oid(), "1.2.3.4.5");
    // typed sadece enum'a düşeni.
    assertThat(ekuRes.getTyped()).containsExactly(ExtendedKeyUsage.CLIENT_AUTH);
    // unknown sadece enum dışı kalanı.
    assertThat(ekuRes.getUnknownOids()).containsExactly("1.2.3.4.5");
  }

  /* ---------------- CertificatePolicies parse'ı ---------------- */

  @Test
  void certificatePoliciesParsesAllOidsAndDetectsTurkishMaliMuhurSigning() throws Exception {
    X509Certificate cert =
        buildCert(
            org.bouncycastle.asn1.x509.KeyUsage.digitalSignature,
            null,
            new String[] {OID_MALI_MUHUR_SIGNING, "1.2.3.4.99"},
            null);

    CertificateInspector.CertificatePoliciesResult res = inspector.certificatePolicies(cert);
    assertThat(res.getAllOids()).containsExactlyInAnyOrder(OID_MALI_MUHUR_SIGNING, "1.2.3.4.99");
    assertThat(res.getTurkishPolicies())
        .containsExactly(TurkishCertificatePolicy.MALI_MUHUR_SIGNING);
  }

  @Test
  void certificatePoliciesDetectsKamuSmQesIndividual() throws Exception {
    X509Certificate cert =
        buildCert(
            org.bouncycastle.asn1.x509.KeyUsage.digitalSignature
                | org.bouncycastle.asn1.x509.KeyUsage.nonRepudiation,
            null,
            new String[] {OID_KAMU_SM_QES_INDIVIDUAL},
            null);

    assertThat(inspector.certificatePolicies(cert).getTurkishPolicies())
        .containsExactly(TurkishCertificatePolicy.KAMU_SM_QES_INDIVIDUAL);
  }

  /* ---------------- Türkçe politika kısa-yolu purpose() üzerinde ---------------- */

  @Test
  void turkishMaliMuhurSigningPolicyLocksPurposeToSigningRegardlessOfBits() throws Exception {
    // Bit'ler hatalı şekilde sadece keyEncipherment dönse bile, policy OID SIGNING kilitliyor.
    X509Certificate cert =
        buildCert(
            org.bouncycastle.asn1.x509.KeyUsage.keyEncipherment,
            null,
            new String[] {OID_MALI_MUHUR_SIGNING},
            null);

    assertThat(inspector.purpose(cert)).isEqualTo(CertificatePurpose.SIGNING);
  }

  @Test
  void turkishMaliMuhurEncryptionPolicyLocksPurposeToEncryption() throws Exception {
    // SIGNING bit'i set olsa bile Mali Mühür ŞIFRELEME policy OID kazanır.
    X509Certificate cert =
        buildCert(
            org.bouncycastle.asn1.x509.KeyUsage.digitalSignature,
            null,
            new String[] {OID_MALI_MUHUR_ENCRYPTION},
            null);

    assertThat(inspector.purpose(cert)).isEqualTo(CertificatePurpose.ENCRYPTION);
  }

  @Test
  void turkishConflictingPoliciesYieldMixed() throws Exception {
    X509Certificate cert =
        buildCert(
            org.bouncycastle.asn1.x509.KeyUsage.digitalSignature,
            null,
            new String[] {OID_MALI_MUHUR_SIGNING, OID_MALI_MUHUR_ENCRYPTION},
            null);

    assertThat(inspector.purpose(cert)).isEqualTo(CertificatePurpose.MIXED);
  }

  /* ---------------- QCStatements parse'ı ---------------- */

  @Test
  void qcStatementOidsExposeStatementIdentifiers() throws Exception {
    X509Certificate cert =
        buildCert(
            org.bouncycastle.asn1.x509.KeyUsage.digitalSignature
                | org.bouncycastle.asn1.x509.KeyUsage.nonRepudiation,
            null,
            null,
            new String[] {OID_QC_COMPLIANCE, OID_QC_SSCD});

    Set<String> oids = inspector.qcStatementOids(cert);
    assertThat(oids).containsExactlyInAnyOrder(OID_QC_COMPLIANCE, OID_QC_SSCD);
    assertThat(inspector.isQualified(cert)).isTrue();
  }

  /* ---------------- helpers ---------------- */

  private static X509Certificate buildCert(
      int keyUsageBits,
      KeyPurposeId[] ekuPurposes,
      String[] certificatePolicyOids,
      String[] qcStatementOids)
      throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair kp = gen.generateKeyPair();

    X500Name subject = new X500Name("CN=Test Cert");
    Instant now = Instant.now();
    X509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(System.currentTimeMillis()),
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(365, ChronoUnit.DAYS)),
            subject,
            kp.getPublic());
    if (keyUsageBits != 0) {
      builder.addExtension(
          Extension.keyUsage, true, new org.bouncycastle.asn1.x509.KeyUsage(keyUsageBits));
    }
    if (ekuPurposes != null && ekuPurposes.length > 0) {
      builder.addExtension(
          Extension.extendedKeyUsage,
          false,
          new org.bouncycastle.asn1.x509.ExtendedKeyUsage(ekuPurposes));
    }
    if (certificatePolicyOids != null && certificatePolicyOids.length > 0) {
      ASN1EncodableVector vec = new ASN1EncodableVector();
      for (String oid : certificatePolicyOids) {
        vec.add(new PolicyInformation(new ASN1ObjectIdentifier(oid)));
      }
      builder.addExtension(Extension.certificatePolicies, false, new DERSequence(vec));
    }
    if (qcStatementOids != null && qcStatementOids.length > 0) {
      ASN1EncodableVector vec = new ASN1EncodableVector();
      for (String oid : qcStatementOids) {
        vec.add(new QCStatement(new ASN1ObjectIdentifier(oid)));
      }
      builder.addExtension(Extension.qCStatements, false, new DERSequence(vec));
    }
    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }
}
