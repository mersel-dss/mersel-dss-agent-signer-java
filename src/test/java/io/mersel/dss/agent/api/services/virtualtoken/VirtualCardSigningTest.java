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
package io.mersel.dss.agent.api.services.virtualtoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.security.Key;
import java.security.PublicKey;
import java.util.List;
import java.util.Locale;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xml.security.Init;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.signatures.SignatureUtil;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.models.CertificateResponse;
import io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder;
import io.mersel.dss.agent.api.services.certificate.CertificateInspector;
import io.mersel.dss.agent.api.services.certificate.CertificateListingService;
import io.mersel.dss.agent.api.services.certificate.RevocationChecker;
import io.mersel.dss.agent.api.services.signature.PadesService;
import io.mersel.dss.agent.api.services.signature.XadesService;
import io.mersel.dss.agent.api.testsupport.PfxLoader;
import io.mersel.dss.agent.api.testsupport.PfxTestKey;
import io.mersel.dss.agent.api.util.InMemoryMultipartFile;

/**
 * Sanal PKCS#12 (PFX) "Dummy Card" uçtan uca: sertifika listeleme + PAdES + XAdES-BES (RSA & EC)
 * imzalarının üretilebildiğini ve doğrulanabildiğini Kamu SM test PFX'leriyle gösterir.
 */
class VirtualCardSigningTest {

  private static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";
  private static final String XADES_NS = "http://uri.etsi.org/01903/v1.3.2#";

  @BeforeAll
  static void initSantuario() {
    Init.init();
  }

  @ParameterizedTest(name = "[{0}] PFX sanal karttan sertifika listelenir")
  @EnumSource(PfxTestKey.class)
  void listsCertificatesFromVirtualPkcs12(PfxTestKey key) throws Exception {
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());

    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    VirtualToken token =
        registry.registerPkcs12(
            "PFX-" + key.name(),
            Files.readAllBytes(key.getFile().toPath()),
            key.getPassword(),
            key.getFileName());

    RevocationChecker checker = mock(RevocationChecker.class);
    when(checker.check(any(), any()))
        .thenReturn(
            new io.mersel.dss.agent.api.models.CertificateStatusResponse(
                io.mersel.dss.agent.api.models.CertificateStatusResponse.Status.UNKNOWN,
                "test — no network"));
    CertificateListingService svc =
        new CertificateListingService(
            null, new CertificateInspector(checker), CertificateChainBuilder.passthrough());

    List<CertificateResponse> certs = svc.listFromVirtual(token);

    assertThat(certs).isNotEmpty();
    assertThat(certs.get(0).getSubject()).isNotBlank();
    assertThat(certs.get(0).getX509SerialNumber()).isNotBlank();
  }

  @ParameterizedTest(name = "[{0}] PFX sanal kartla PAdES imza")
  @EnumSource(PfxTestKey.class)
  void signsPadesViaVirtualPkcs12(PfxTestKey key) throws Exception {
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    String serialHex = loaded.certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);

    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    registry.registerPkcs12(
        "PFX", Files.readAllBytes(key.getFile().toPath()), key.getPassword(), key.getFileName());

    SignDocumentDto dto = new SignDocumentDto();
    dto.setTerminalName("PFX");
    dto.setPin("ignored"); // PFX kartta yok sayılır
    dto.setCertificateId(serialHex);
    dto.setContent(
        new InMemoryMultipartFile("file", "test.pdf", "application/pdf", generatePdf("Mersel")));

    PadesService pades = new PadesService(null, CertificateChainBuilder.passthrough(), registry);
    ByteArrayOutputStream signedOut = new ByteArrayOutputStream();
    pades.sign(dto, signedOut);

    byte[] signedPdf = signedOut.toByteArray();
    assertThat(signedPdf).isNotEmpty();
    try (PdfReader reader = new PdfReader(new ByteArrayInputStream(signedPdf));
        PdfDocument doc = new PdfDocument(reader)) {
      assertThat(new SignatureUtil(doc).getSignatureNames()).isNotEmpty();
    }
  }

  @ParameterizedTest(name = "[{0}] PFX sanal kartla XAdES-BES imza ve doğrulama")
  @EnumSource(PfxTestKey.class)
  void signsXadesBesViaVirtualPkcs12(PfxTestKey key) throws Exception {
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    String serialHex = loaded.certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
    PublicKey publicKey = loaded.certificate.getPublicKey();

    VirtualTokenRegistry registry = new VirtualTokenRegistry();
    registry.registerPkcs12(
        "PFX", Files.readAllBytes(key.getFile().toPath()), key.getPassword(), key.getFileName());

    SignDocumentDto dto = new SignDocumentDto();
    dto.setTerminalName("PFX");
    dto.setPin("ignored");
    dto.setCertificateId(serialHex);
    dto.setContent(new InMemoryMultipartFile("file", "doc.xml", "application/xml", sampleXml()));

    XadesService xades =
        new XadesService(null, CertificateChainBuilder.passthrough(), null, registry);
    byte[] signedXml = xades.signXmlDocument(dto);
    assertThat(signedXml).isNotEmpty();

    Document doc = parse(signedXml);
    NodeList sigs = doc.getElementsByTagNameNS(DS_NS, "Signature");
    assertThat(sigs.getLength()).isEqualTo(1);
    Element sigEl = (Element) sigs.item(0);

    // SignedProperties Id'sini XML ID olarak işaretle ki #ref çözümlensin.
    NodeList signedProps = doc.getElementsByTagNameNS(XADES_NS, "SignedProperties");
    assertThat(signedProps.getLength()).isEqualTo(1);
    ((Element) signedProps.item(0)).setIdAttribute("Id", true);

    DOMValidateContext valCtx = new DOMValidateContext(new FixedKeySelector(publicKey), sigEl);
    XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
    XMLSignature signature = fac.unmarshalXMLSignature(valCtx);

    assertThat(signature.validate(valCtx))
        .as("XAdES-BES (PFX) software imza matematiksel olarak doğrulanmalı")
        .isTrue();
    assertThat(signature.getSignedInfo().getReferences()).hasSize(2);
  }

  /* ---------------- helpers ---------------- */

  private static byte[] sampleXml() throws Exception {
    return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<root><data>Mersel DSS Agent virtual card XAdES-BES</data></root>")
        .getBytes("UTF-8");
  }

  private static Document parse(byte[] xml) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
  }

  private static byte[] generatePdf(String text) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
        com.itextpdf.layout.Document layout = new com.itextpdf.layout.Document(pdfDoc)) {
      layout.add(new Paragraph(text));
    }
    return out.toByteArray();
  }

  /** Sertifikadan bağımsız, sabit public key dönen KeySelector. */
  private static final class FixedKeySelector extends KeySelector {
    private final PublicKey publicKey;

    FixedKeySelector(PublicKey publicKey) {
      this.publicKey = publicKey;
    }

    @Override
    public KeySelectorResult select(
        KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context) {
      return new KeySelectorResult() {
        @Override
        public Key getKey() {
          return publicKey;
        }
      };
    }
  }
}
