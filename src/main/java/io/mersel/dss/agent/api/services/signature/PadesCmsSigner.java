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
package io.mersel.dss.agent.api.services.signature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuerSerial;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.signatures.IExternalSignatureContainer;

/**
 * PAdES-B-B (CADES) seviyesi PDF imzası için <b>manuel CMS</b> kurucu — iText 7 {@link
 * IExternalSignatureContainer} kontratını implemente eder.
 *
 * <h2>Neden manuel?</h2>
 *
 * iText 7'nin built-in {@code PdfSigner.signDetached(... CryptoStandard.CADES)} yolu {@code
 * id-aa-signingCertificateV2} (RFC 5035) attribute'ını kurarken {@link
 * ESSCertIDv2#getIssuerSerial() issuerSerial} alanını <b>OPTIONAL kabul edip yazmıyor</b>. RFC 5035
 * §3 bunu {@code OPTIONAL} olarak tanımlar; ancak Türkiye e-imza ekosisteminde TÜBİTAK / Kamu SM
 * doğrulayıcıları (İmzAGER, MA3, Cybersoft Verifier vb.) bu alanı <b>ZORUNLU</b> sayar ve eksik
 * olduğunda "İmzacı Sertifikası Özelliği V2 Kontrolcüsü → Özellik issuer serial alanı içermiyor"
 * şeklinde fail döner.
 *
 * <p>Bu sınıf {@code SigningCertificateV2} attribute'ını eksiksiz kurar:
 *
 * <pre>
 *   ESSCertIDv2 ::= SEQUENCE {
 *       hashAlgorithm   AlgorithmIdentifier DEFAULT id-sha256,
 *       certHash        OCTET STRING,
 *       issuerSerial    IssuerSerial   ← ZORUNLU yazılır
 *   }
 *   IssuerSerial ::= SEQUENCE {
 *       issuer       GeneralNames,
 *       serialNumber CertificateSerialNumber
 *   }
 * </pre>
 *
 * <h2>İmza algoritması seçimi</h2>
 *
 * Server projesindeki {@code DigestAlgorithmResolverService} ile aynı kural: <b>sertifikanın public
 * key parametresi</b> taban alınır, CA'nın sertifikayı imzalarken kullandığı algoritma ({@code
 * cert.getSigAlgName()}) <em>kasıtlı olarak yok sayılır</em>. EC için curve büyüklüğü (NIST SP
 * 800-57), RSA için SHA-256 default.
 *
 * <ul>
 *   <li>RSA → SHA-256 + RSASSA-PKCS1-v1_5 ({@code 1.2.840.113549.1.1.11})
 *   <li>EC P-256 → SHA-256 + ECDSA ({@code 1.2.840.10045.4.3.2})
 *   <li>EC P-384 → SHA-384 + ECDSA ({@code 1.2.840.10045.4.3.3})
 *   <li>EC P-521 → SHA-512 + ECDSA ({@code 1.2.840.10045.4.3.4})
 * </ul>
 *
 * <h2>Provider akışı</h2>
 *
 * Akıllı kart {@link PrivateKey} SunPKCS11 provider'ında yaşar; {@link
 * JcaContentSignerBuilder#setProvider(String) provider name} olarak {@code
 * Pkcs11Session.getProvider().getName()} verilir. Digest calculator (signed attributes ve cert hash
 * hesabı) {@code BouncyCastle} provider'ında çalışır — SunPKCS11 bazı kartlarda {@code
 * MessageDigest.getInstance("SHA-256")}'i driver'a delege eder ve gereksiz session açar; BC her
 * ortamda saf yazılım.
 *
 * <h2>Çıktı</h2>
 *
 * iText 7 {@code PdfSigner.signExternalContainer(container, estimatedSize)} çağrısı bu sınıfın
 * {@link #sign(InputStream)}'inden gelen DER-encoded CMS SignedData byte'larını PDF imza
 * dictionary'sinin {@code /Contents} alanına gömüp byte range'i ona göre günceller. {@link
 * #modifySigningDictionary(PdfDictionary)} ETSI {@code SubFilter}'ı ({@code ETSI.CAdES.detached})
 * set eder.
 */
final class PadesCmsSigner implements IExternalSignatureContainer {

  private static final Logger log = LoggerFactory.getLogger(PadesCmsSigner.class);

  /**
   * BC digest calculator provider için sabit isim. {@link
   * io.mersel.dss.agent.api.services.keystore.BouncyCastleSetup#ensureRegistered()} BC'yi 1.
   * pozisyona kayıt etmiş olur, bu sınıf çağrıldığında provider mevcut.
   */
  private static final String BC_PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

  private final PrivateKey privateKey;
  private final X509Certificate signingCertificate;
  private final List<X509Certificate> certificateChain;
  private final String signatureProviderName;

  /** Digest seçimi: cert public key'e göre çözümlenmiş. */
  private final SigAlg sigAlg;

  PadesCmsSigner(
      PrivateKey privateKey, X509Certificate[] chain, String signatureProviderName, SigAlg sigAlg) {
    if (privateKey == null) throw new IllegalArgumentException("privateKey null olamaz");
    if (chain == null || chain.length == 0) {
      throw new IllegalArgumentException("certificate chain boş olamaz");
    }
    if (signatureProviderName == null || signatureProviderName.trim().isEmpty()) {
      throw new IllegalArgumentException("signatureProviderName null/boş olamaz");
    }
    this.privateKey = privateKey;
    this.signingCertificate = chain[0];
    this.certificateChain = new ArrayList<>(Arrays.asList(chain));
    this.signatureProviderName = signatureProviderName;
    this.sigAlg = sigAlg;
  }

  /**
   * iText'in PDF byte range stream'i üzerinden CMS SignedData (detached) üret.
   *
   * <p>Akış:
   *
   * <ol>
   *   <li>Stream'i tamamen oku — byte range, signature placeholder dışındaki tüm PDF byte'larıdır.
   *   <li>{@link SigningCertificateV2} attribute'ını <b>IssuerSerial dahil</b> kur.
   *   <li>BC {@link CMSSignedDataGenerator} ile signed-attributes (content-type, message-digest,
   *       signing-certificate-v2) üret + private key ile imzala.
   *   <li>Cert chain'i CMS {@code certificates} field'ına göm.
   *   <li>DER-encoded CMS bytes döndür ({@code encapsulate=false}: detached, içerik PDF'in
   *       byteRange'inde tutulur).
   * </ol>
   */
  @Override
  public byte[] sign(InputStream data) throws GeneralSecurityException {
    try {
      byte[] dataBytes = readAll(data);

      Attribute signingCertificateV2 = buildSigningCertificateV2Attribute();
      ASN1EncodableVector signedAttrs = new ASN1EncodableVector();
      signedAttrs.add(signingCertificateV2);
      AttributeTable signedAttrTable = new AttributeTable(signedAttrs);

      JcaSignerInfoGeneratorBuilder signerInfoBuilder =
          new JcaSignerInfoGeneratorBuilder(
                  new JcaDigestCalculatorProviderBuilder().setProvider(BC_PROVIDER).build())
              .setSignedAttributeGenerator(
                  new DefaultSignedAttributeTableGenerator(signedAttrTable));

      ContentSigner contentSigner =
          new JcaContentSignerBuilder(sigAlg.jcaSignatureAlgorithm)
              .setProvider(signatureProviderName)
              .build(privateKey);

      CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
      generator.addSignerInfoGenerator(signerInfoBuilder.build(contentSigner, signingCertificate));
      generator.addCertificates(new JcaCertStore(certificateChain));

      CMSSignedData cms = generator.generate(new CMSProcessableByteArray(dataBytes), false);
      byte[] encoded = cms.getEncoded();
      log.debug(
          "PAdES CMS üretildi: {} byte, signedAttrs=signingCertV2(IssuerSerial dahil), sigAlg={}",
          encoded.length,
          sigAlg.jcaSignatureAlgorithm);
      return encoded;
    } catch (GeneralSecurityException gse) {
      throw gse;
    } catch (Exception e) {
      throw new GeneralSecurityException("PAdES CMS üretimi başarısız: " + e.getMessage(), e);
    }
  }

  /**
   * iText {@code /Sig} dictionary'sine PAdES-CADES için ETSI SubFilter set eder.
   *
   * <ul>
   *   <li>{@code /Filter}: {@code Adobe.PPKLite} (Adobe-conformant; aynı zamanda eIDAS gereği)
   *   <li>{@code /SubFilter}: {@code ETSI.CAdES.detached} (PAdES-B-B; ETSI EN 319 142-1 §5)
   * </ul>
   */
  @Override
  public void modifySigningDictionary(PdfDictionary signDic) {
    signDic.put(PdfName.Filter, PdfName.Adobe_PPKLite);
    signDic.put(PdfName.SubFilter, PdfName.ETSI_CAdES_DETACHED);
  }

  /* ============================================================== */
  /* SigningCertificateV2 (RFC 5035 §3) — IssuerSerial DAHİL          */
  /* ============================================================== */

  /**
   * {@code id-aa-signingCertificateV2} attribute'ını eksiksiz kurar.
   *
   * <pre>
   *   SigningCertificateV2 ::= SEQUENCE {
   *       certs SEQUENCE OF ESSCertIDv2,
   *       policies SEQUENCE OF PolicyInformation OPTIONAL
   *   }
   *   ESSCertIDv2 ::= SEQUENCE {
   *       hashAlgorithm AlgorithmIdentifier DEFAULT id-sha256,
   *       certHash      OCTET STRING,
   *       issuerSerial  IssuerSerial OPTIONAL  ← bizim için ZORUNLU yazılır
   *   }
   * </pre>
   *
   * <p>Cert hash, agent'ın seçtiği imza digest'iyle (SHA-256 / SHA-384 / SHA-512) hesaplanır — RFC
   * 5035 §3 hashAlgorithm == imza digest'i kuralına uygun.
   */
  private Attribute buildSigningCertificateV2Attribute()
      throws GeneralSecurityException, CertificateEncodingException {
    AlgorithmIdentifier digestAlgId = new AlgorithmIdentifier(sigAlg.digestOid);
    byte[] certHash =
        MessageDigest.getInstance(sigAlg.jcaDigestName, BC_PROVIDER)
            .digest(signingCertificate.getEncoded());

    GeneralName issuerName =
        new GeneralName(
            X500Name.getInstance(signingCertificate.getIssuerX500Principal().getEncoded()));
    GeneralNames issuerNames = new GeneralNames(issuerName);
    IssuerSerial issuerSerial = new IssuerSerial(issuerNames, signingCertificate.getSerialNumber());

    ESSCertIDv2 essCertIdV2 = new ESSCertIDv2(digestAlgId, certHash, issuerSerial);
    SigningCertificateV2 signingCertV2 = new SigningCertificateV2(new ESSCertIDv2[] {essCertIdV2});

    return new Attribute(
        PKCSObjectIdentifiers.id_aa_signingCertificateV2, new DERSet(signingCertV2));
  }

  /* ============================================================== */
  /* Helpers                                                          */
  /* ============================================================== */

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    byte[] tmp = new byte[8192];
    int n;
    while ((n = in.read(tmp)) > 0) {
      buf.write(tmp, 0, n);
    }
    return buf.toByteArray();
  }

  /**
   * Bir sertifikanın <b>public key</b> parametresine göre çözümlenmiş imza algoritması.
   *
   * <p>Server projesinin {@code DigestAlgorithmResolverService} mantığıyla bire bir uyumludur —
   * fark: DSS {@code SignatureAlgorithm} enum'una bağımlı değildir, saf BC OID + JCA isim çifti
   * kullanır.
   *
   * @see PadesCmsSigner sınıf javadoc'undaki "İmza algoritması seçimi" bölümü
   */
  static final class SigAlg {
    final ASN1ObjectIdentifier digestOid;
    final String jcaDigestName;
    final String jcaSignatureAlgorithm;
    final String label;

    private SigAlg(
        ASN1ObjectIdentifier digestOid,
        String jcaDigestName,
        String jcaSignatureAlgorithm,
        String label) {
      this.digestOid = digestOid;
      this.jcaDigestName = jcaDigestName;
      this.jcaSignatureAlgorithm = jcaSignatureAlgorithm;
      this.label = label;
    }

    /**
     * Public key tipine + EC için curve büyüklüğüne göre imza algoritmasını seçer.
     *
     * <p>Forced override (örn. property-driven SHA-256) gerekirse {@link #forced} yardımcısı
     * kullanılır.
     */
    static SigAlg forCertificate(X509Certificate cert) {
      if (cert == null) {
        return rsa(Digest.SHA256);
      }
      PublicKey pub = cert.getPublicKey();
      String alg = pub.getAlgorithm() == null ? "" : pub.getAlgorithm().toUpperCase(Locale.ROOT);
      if (pub instanceof ECKey || alg.contains("EC")) {
        Digest d = digestForEcCurve((ECKey) pub);
        return ec(d);
      }
      return rsa(Digest.SHA256);
    }

    /** Property-driven override (ileride {@code signing.digest.algorithm} desteği için). */
    static SigAlg forced(X509Certificate cert, Digest forcedDigest) {
      if (forcedDigest == null) {
        return forCertificate(cert);
      }
      PublicKey pub = cert == null ? null : cert.getPublicKey();
      String alg =
          (pub == null || pub.getAlgorithm() == null)
              ? "RSA"
              : pub.getAlgorithm().toUpperCase(Locale.ROOT);
      if (pub instanceof ECKey || alg.contains("EC")) {
        return ec(forcedDigest);
      }
      return rsa(forcedDigest);
    }

    private static Digest digestForEcCurve(ECKey ecKey) {
      if (ecKey == null || ecKey.getParams() == null || ecKey.getParams().getOrder() == null) {
        return Digest.SHA256;
      }
      int bits = ecKey.getParams().getOrder().bitLength();
      if (bits > 384) return Digest.SHA512;
      if (bits > 256) return Digest.SHA384;
      if (bits > 224) return Digest.SHA256;
      return Digest.SHA224;
    }

    private static SigAlg rsa(Digest d) {
      return new SigAlg(d.oid, d.jca, d.jca.replace("-", "") + "withRSA", "RSA-" + d.label());
    }

    private static SigAlg ec(Digest d) {
      return new SigAlg(d.oid, d.jca, d.jca.replace("-", "") + "withECDSA", "ECDSA-" + d.label());
    }

    @Override
    public String toString() {
      return label + "(" + jcaSignatureAlgorithm + ")";
    }
  }

  /** Desteklenen digest algoritmaları + OID + JCA isim eşlemesi. */
  enum Digest {
    SHA224("SHA-224", NISTObjectIdentifiers.id_sha224),
    SHA256("SHA-256", NISTObjectIdentifiers.id_sha256),
    SHA384("SHA-384", NISTObjectIdentifiers.id_sha384),
    SHA512("SHA-512", NISTObjectIdentifiers.id_sha512);

    final String jca;
    final ASN1ObjectIdentifier oid;

    Digest(String jca, ASN1ObjectIdentifier oid) {
      this.jca = jca;
      this.oid = oid;
    }

    String label() {
      return jca.replace("-", "");
    }
  }
}
