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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.IssuerSerial;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.signatures.SignatureUtil;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;
import io.mersel.dss.agent.api.testsupport.PfxLoader;
import io.mersel.dss.agent.api.testsupport.PfxTestKey;
import io.mersel.dss.agent.api.util.InMemoryMultipartFile;

/**
 * {@link PadesService} davranışını Kamu SM'in publicly published test PFX'leriyle doğrular.
 *
 * <h2>Kapsanan failure modu</h2>
 *
 * Türkiye doğrulayıcılarda (İmzAGER, MA3, Cybersoft Verifier) iki spesifik fail görülüyordu:
 *
 * <ol>
 *   <li><b>İmzacı Sertifikası Özelliği V2 → "Özellik issuer serial alanı içermiyor"</b> — iText 7
 *       built-in CADES yolu {@code ESSCertIDv2.issuerSerial}'i RFC 5035 OPTIONAL kabul edip
 *       atlıyordu. Bu test {@link #signedCmsContainsSigningCertificateV2WithIssuerSerial} ile
 *       attribute içinde IssuerSerial'in yazıldığını doğrular.
 *   <li><b>İmza Matematiksel Doğrulama → "İmza matematiksel doğrulanamadı"</b> — signed-attributes
 *       digest'i veya signature value formatı verifier'ın hesabıyla eşleşmiyordu. Bu test {@link
 *       #signedCmsVerifiesCryptographically} ile BC {@link SignerInformation#verify} üzerinden
 *       gerçek kriptografik doğrulama yapar — pass etmesi imzanın matematiksel olarak verifier
 *       tarafından kabul edileceği garantisi verir.
 * </ol>
 *
 * <p>PFX dosyası repo'da yoksa {@link org.junit.jupiter.api.Assumptions#assumeTrue} ile graceful
 * skip yapılır.
 */
class PadesServiceTest {

  @ParameterizedTest(name = "[{0}] PAdES signs and produces valid CMS")
  @EnumSource(PfxTestKey.class)
  void signsPdfAndProducesValidSignature(PfxTestKey key) throws Exception {
    assumeTrue(key.isAvailable(), "Skip — PFX bulunamadı: " + key.getAbsolutePath());

    byte[] signedPdf = signTestPdf(key);
    assertNotNull(signedPdf);

    try (PdfReader reader = new PdfReader(new ByteArrayInputStream(signedPdf));
        PdfDocument signedDoc = new PdfDocument(reader)) {
      SignatureUtil util = new SignatureUtil(signedDoc);
      List<String> names = util.getSignatureNames();
      assertFalse(names.isEmpty(), "En az bir imza alanı olmalı.");
    }
  }

  /**
   * Regression koruması: imzalı PDF'in CMS'inde {@code id-aa-signingCertificateV2} attribute'ı
   * IssuerSerial alanı dahil ESSCertIDv2 ile yazılmış olmalı.
   *
   * <p>iText 7 built-in CADES yolu IssuerSerial'i atlardı; bu testin geçmesi {@link PadesCmsSigner}
   * üzerinden manuel CMS yazımının yürürlükte olduğunun göstergesidir.
   */
  @ParameterizedTest(name = "[{0}] CMS signingCertificateV2 attribute IssuerSerial içerir")
  @EnumSource(PfxTestKey.class)
  void signedCmsContainsSigningCertificateV2WithIssuerSerial(PfxTestKey key) throws Exception {
    assumeTrue(key.isAvailable(), "Skip — PFX bulunamadı: " + key.getAbsolutePath());

    byte[] signedPdf = signTestPdf(key);
    PfxLoader.Loaded loaded = PfxLoader.load(key);

    SignerInformation signer = extractSignerInformation(signedPdf);
    AttributeTable signedAttrs = signer.getSignedAttributes();
    assertNotNull(signedAttrs, "Signed attributes null olmamalı.");

    org.bouncycastle.asn1.cms.Attribute scV2Attr =
        signedAttrs.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2);
    assertNotNull(
        scV2Attr, "id-aa-signingCertificateV2 attribute'ı eksik — Kamu SM doğrulaması fail eder.");

    ASN1Encodable[] values = scV2Attr.getAttributeValues();
    assertTrue(values.length > 0, "SigningCertificateV2 attribute boş.");

    SigningCertificateV2 scV2 = SigningCertificateV2.getInstance(values[0]);
    ESSCertIDv2[] certs = scV2.getCerts();
    assertTrue(certs != null && certs.length > 0, "ESSCertIDv2 listesi boş.");

    IssuerSerial issuerSerial = certs[0].getIssuerSerial();
    assertNotNull(
        issuerSerial,
        "ESSCertIDv2.issuerSerial NULL — Türk doğrulayıcı 'Özellik issuer serial alanı içermiyor'"
            + " hatası verir. Bu testin fail etmesi PadesCmsSigner regresyonudur.");
    assertNotNull(issuerSerial.getIssuer(), "IssuerSerial.issuer null olamaz.");
    assertEquals(
        loaded.certificate.getSerialNumber(),
        issuerSerial.getSerial().getValue(),
        "IssuerSerial.serial sertifika serial'iyle eşleşmeli.");
  }

  /**
   * Regression koruması: imzalı PDF'in imza değeri kriptografik olarak doğrulanmalı.
   *
   * <p>iText 7 {@link com.itextpdf.signatures.PdfPKCS7#verifySignatureIntegrityAndAuthenticity()}
   * PDF byteRange'i ham byte'larıyla yeniden hash'ler ve CMS signed-attributes ile karşılaştırır;
   * sonra signed-attributes DER bytes'ını signer cert'in public key'iyle Signature.verify eder. Bu
   * test'in pass etmesi, doğrulayıcının ("İmza Matematiksel Doğrulama Kontrolcüsü") imzayı geçerli
   * sayacağı garantisini verir.
   *
   * <p>Manuel yaklaşım ({@link CMSSignedData} + external content) da mümkün ama iText'in native
   * verifier'ı PAdES detached için byteRange wiring'ini zaten doğru yapıyor — duplicate olmasın.
   */
  @ParameterizedTest(name = "[{0}] PDF imza matematiksel olarak doğrulanır")
  @EnumSource(PfxTestKey.class)
  void signedPdfVerifiesCryptographically(PfxTestKey key) throws Exception {
    assumeTrue(key.isAvailable(), "Skip — PFX bulunamadı: " + key.getAbsolutePath());

    byte[] signedPdf = signTestPdf(key);

    try (PdfReader reader = new PdfReader(new ByteArrayInputStream(signedPdf));
        PdfDocument doc = new PdfDocument(reader)) {
      SignatureUtil util = new SignatureUtil(doc);
      List<String> names = util.getSignatureNames();
      assertFalse(names.isEmpty(), "İmza alanı yok.");

      com.itextpdf.signatures.PdfPKCS7 pkcs7 = util.readSignatureData(names.get(0));
      assertNotNull(pkcs7, "PdfPKCS7 null.");

      boolean ok = pkcs7.verifySignatureIntegrityAndAuthenticity();
      assertTrue(
          ok,
          "PdfPKCS7.verifySignatureIntegrityAndAuthenticity FAIL — imza matematiksel olarak"
              + " doğrulanamıyor. Bu, Türk doğrulayıcılarda 'İmza Matematiksel Doğrulama"
              + " Kontrolcüsü → Başarısız' demektir.");
    }
  }

  @Test
  void aliasResolvableByX509Serial() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    String serialHex = loaded.certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);

    try (Pkcs11Session session = loaded.openSession()) {
      assertEquals(loaded.alias, session.resolveAlias(loaded.alias));
      assertEquals(loaded.alias, session.resolveAlias(serialHex));
      assertEquals(loaded.alias, session.resolveAlias(serialHex.toLowerCase(Locale.ROOT)));
      assertEquals(loaded.alias, session.resolveAlias("0x" + serialHex));
    }
  }

  /**
   * Public-key bazlı imza algoritması seçimi. RSA → SHA-256, EC P-384 → SHA-384. Server projesinin
   * {@code DigestAlgorithmResolverService} ile aynı kuralı izler.
   */
  @Test
  void sigAlgSelectionRsa() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    PadesCmsSigner.SigAlg alg = PadesCmsSigner.SigAlg.forCertificate(loaded.certificate);
    assertEquals("SHA256withRSA", alg.jcaSignatureAlgorithm);
    assertEquals("SHA-256", alg.jcaDigestName);
  }

  @Test
  void sigAlgSelectionEcdsa() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM02_EC384;
    assumeTrue(key.isAvailable());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    PadesCmsSigner.SigAlg alg = PadesCmsSigner.SigAlg.forCertificate(loaded.certificate);
    assertEquals("SHA384withECDSA", alg.jcaSignatureAlgorithm);
    assertEquals("SHA-384", alg.jcaDigestName);
  }

  /* ---------------- helpers ---------------- */

  private static byte[] signTestPdf(PfxTestKey key) throws Exception {
    PfxLoader.Loaded loaded = PfxLoader.load(key);
    String serialHex = loaded.certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);

    byte[] pdf = generatePdf("Mersel DSS Agent PAdES — " + key.algorithm());
    SignDocumentDto dto = new SignDocumentDto();
    dto.setTerminalName("test");
    dto.setPin(new String(key.getPassword()));
    dto.setCertificateId(serialHex);
    dto.setContent(new InMemoryMultipartFile("file", "test.pdf", "application/pdf", pdf));

    ByteArrayOutputStream signedOut = new ByteArrayOutputStream();
    PadesService service =
        new PadesService(
            null,
            io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder.passthrough(),
            new io.mersel.dss.agent.api.services.virtualtoken.VirtualTokenRegistry());

    try (Pkcs11Session session = loaded.openSession()) {
      service.signWithSession(session, dto, signedOut);
    }
    return signedOut.toByteArray();
  }

  /**
   * İmzalı PDF'in {@code /Contents}'ından detached CMS'i çıkar ve ilk SignerInformation'ı döner.
   * SignedData'nın content'i (PDF byteRange bytes) PDF'in kendisinde tuttuğu için BC verifier
   * external content olarak veriyi de yeniden bağlamak ister; biz {@code signed} attribute'ları
   * ayrıştırma + verify aşamasında SignedAttributes üzerinden hash kontrolü yapacağı için detached
   * CMS'i olduğu gibi kullanmak yeterli — BC {@code verify} signed-attrs digest'inin içerikten
   * türediğini, signed-attrs'in private key ile imzalandığını doğrular.
   */
  private static SignerInformation extractSignerInformation(byte[] signedPdf) throws Exception {
    try (PdfReader reader = new PdfReader(new ByteArrayInputStream(signedPdf));
        PdfDocument doc = new PdfDocument(reader)) {
      SignatureUtil util = new SignatureUtil(doc);
      List<String> names = util.getSignatureNames();
      assertFalse(names.isEmpty(), "İmza alanı yok.");
      String firstName = names.get(0);
      com.itextpdf.signatures.PdfPKCS7 pkcs7 = util.readSignatureData(firstName);
      assertNotNull(pkcs7, "SignatureUtil.readSignatureData null döndü.");

      // PdfPKCS7 → BC CMSSignedData reconstruct: imza dictionary'sinin /Contents byte'ları
      // tam DER-encoded CMS SignedData. SignatureUtil ham byte'ları doğrudan açmıyor; biz
      // /Contents'ı low-level alalım.
      byte[] cmsDer = util.getSignature(firstName).getContents().getValueBytes();
      CMSSignedData cms = new CMSSignedData(cmsDer);
      Iterator<SignerInformation> it = cms.getSignerInfos().getSigners().iterator();
      assertTrue(it.hasNext(), "CMS içinde signer yok.");
      return it.next();
    }
  }

  private static byte[] generatePdf(String text) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
        Document layout = new Document(pdfDoc)) {
      layout.add(new Paragraph(text));
    }
    return out.toByteArray();
  }
}
