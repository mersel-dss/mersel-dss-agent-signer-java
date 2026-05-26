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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xml.security.Init;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.models.enums.XmlContentType;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;
import io.mersel.dss.agent.api.testsupport.PfxLoader;
import io.mersel.dss.agent.api.testsupport.PfxTestKey;
import io.mersel.dss.agent.api.util.InMemoryMultipartFile;

/**
 * {@link XadesService#signHrWithSession} davranışını Kamu SM RSA-2048 test sertifikasıyla doğrular:
 * önceden imzalanmış bir XAdES belgesinin {@code SignatureValue}'su üzerine sertifika sahibinin
 * counter-signature attığını ve sonucun XML-DSig olarak doğrulanabildiğini kontrol eder.
 */
class XadesCounterSignatureTest {

  @BeforeAll
  static void initApacheSantuario() {
    Init.init();
  }

  @Test
  void addsAndVerifiesCounterSignature() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    byte[] originalSignedXml = sampleSignedXadesDocument();

    SignDocumentDto dto = new SignDocumentDto();
    dto.setTerminalName("test");
    dto.setPin(new String(key.getPassword()));
    dto.setContentType(XmlContentType.HrXmlCounterSignature);
    String serialHex = loaded.certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
    dto.setCertificateId(serialHex);
    dto.setContent(
        new InMemoryMultipartFile("file", "hr.xml", "application/xml", originalSignedXml));

    XadesService service =
        new XadesService(
            null,
            io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder.passthrough());
    byte[] counterSignedXml;
    try (Pkcs11Session session = loaded.openSession()) {
      counterSignedXml = service.signHrWithSession(session, dto);
    }
    assertNotNull(counterSignedXml);

    Document doc = parse(counterSignedXml);

    // CounterSignature elementi var mı?
    NodeList counterSigs = doc.getElementsByTagNameNS(XadesService.XADES_NS, "CounterSignature");
    assertTrue(counterSigs.getLength() >= 1, "xades:CounterSignature elementi eklenmiş olmalı.");

    Element counterContainer = (Element) counterSigs.item(0);
    NodeList innerSigs = counterContainer.getElementsByTagNameNS(XadesService.DS_NS, "Signature");
    assertTrue(innerSigs.getLength() >= 1, "CounterSignature içinde ds:Signature olmalı.");

    // Karşı imzalanan ve counter-sig'in kendi SignatureValue'larındaki Id attribute'larını
    // XML ID olarak işaretle — schema yokken getElementById bulabilsin.
    NodeList allSigValues = doc.getElementsByTagNameNS(XadesService.DS_NS, "SignatureValue");
    boolean targetHasId = false;
    for (int i = 0; i < allSigValues.getLength(); i++) {
      Element el = (Element) allSigValues.item(i);
      String id = el.getAttribute("Id");
      if (id != null && !id.isEmpty()) {
        if (i == 0) targetHasId = true;
        el.setIdAttribute("Id", true);
      }
    }
    assertTrue(targetHasId, "Hedef SignatureValue'a Id atanmış olmalı.");

    // XAdES-BES counter-signature, kendi SignedProperties'ini referans aldığı için onun da
    // Id'sini XML ID olarak işaretle.
    NodeList signedPropsList =
        doc.getElementsByTagNameNS(XadesService.XADES_NS, "SignedProperties");
    boolean counterHasSignedProps = false;
    for (int i = 0; i < signedPropsList.getLength(); i++) {
      Element el = (Element) signedPropsList.item(i);
      String id = el.getAttribute("Id");
      if (id != null && !id.isEmpty()) {
        el.setIdAttribute("Id", true);
        counterHasSignedProps = true;
      }
    }
    assertTrue(
        counterHasSignedProps,
        "Counter-signature için xades:SignedProperties (Id'li) üretilmiş olmalı.");

    // Counter-sig XAdES-BES olarak: iki Reference'ı da içeren XML-DSig validate'i geçmeli.
    Element counterSigElement = (Element) innerSigs.item(0);
    DOMValidateContext valCtx = new DOMValidateContext(new X509KeySelector(), counterSigElement);
    XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
    XMLSignature signature = fac.unmarshalXMLSignature(valCtx);
    assertTrue(
        signature.validate(valCtx),
        "Counter-signature XAdES-BES (SignedProperties + CountersignedSignature) olarak"
            + " geçerli olmalı.");

    // XAdES-BES yapısal invariant'ları:
    // 1) SignedInfo iki Reference içerir — biri SignedProperties, biri CountersignedSignature.
    assertEquals(2, signature.getSignedInfo().getReferences().size(), "İki Reference olmalı.");
    boolean hasSignedPropsRef = false;
    boolean hasCounterRef = false;
    for (Object refObj : signature.getSignedInfo().getReferences()) {
      javax.xml.crypto.dsig.Reference r = (javax.xml.crypto.dsig.Reference) refObj;
      if (XadesService.XADES_TYPE_SIGNED_PROPERTIES.equals(r.getType())) hasSignedPropsRef = true;
      if (XadesService.XADES_TYPE_COUNTERSIGNED_SIGNATURE.equals(r.getType())) hasCounterRef = true;
    }
    assertTrue(hasSignedPropsRef, "SignedProperties tipli Reference olmalı.");
    assertTrue(hasCounterRef, "CountersignedSignature tipli Reference olmalı.");

    // 2) QualifyingProperties Target attribute counter-signature'ın kendi Id'sini göstermeli.
    Element counterQp =
        (Element)
            counterSigElement
                .getElementsByTagNameNS(XadesService.XADES_NS, "QualifyingProperties")
                .item(0);
    assertNotNull(counterQp, "Counter-sig içinde xades:QualifyingProperties olmalı.");
    String target = counterQp.getAttribute("Target");
    String counterId = counterSigElement.getAttribute("Id");
    assertTrue(
        target != null && counterId != null && target.equals("#" + counterId),
        "QualifyingProperties Target counter-sig Id'siyle eşleşmeli: target=" + target);

    // 3) SigningTime + SigningCertificate (CertDigest + IssuerSerial) doldurulmuş olmalı.
    assertTrue(
        counterSigElement.getElementsByTagNameNS(XadesService.XADES_NS, "SigningTime").getLength()
            > 0,
        "xades:SigningTime üretilmiş olmalı.");
    assertTrue(
        counterSigElement.getElementsByTagNameNS(XadesService.XADES_NS, "CertDigest").getLength()
            > 0,
        "xades:CertDigest üretilmiş olmalı.");
    assertTrue(
        counterSigElement.getElementsByTagNameNS(XadesService.XADES_NS, "IssuerSerial").getLength()
            > 0,
        "xades:IssuerSerial üretilmiş olmalı.");

    // 4) KeyInfo'da yalnız tek imzacı sertifikası bulunsun (chain leak'i değil).
    Element counterKeyInfo =
        (Element) counterSigElement.getElementsByTagNameNS(XadesService.DS_NS, "KeyInfo").item(0);
    assertNotNull(counterKeyInfo, "ds:KeyInfo olmalı.");
    NodeList certs = counterKeyInfo.getElementsByTagNameNS(XadesService.DS_NS, "X509Certificate");
    assertEquals(1, certs.getLength(), "Counter-sig KeyInfo tek X509Certificate içermeli.");
  }

  private static byte[] sampleSignedXadesDocument() {
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<doc xmlns=\"urn:test\">"
            + "  <body>HR Faturası</body>"
            + "  <ds:Signature xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" Id=\"sig1\">"
            + "    <ds:SignedInfo>"
            + "      <ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments\"/>"
            + "      <ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"/>"
            + "      <ds:Reference URI=\"\">"
            + "        <ds:DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"/>"
            + "        <ds:DigestValue>AAAA</ds:DigestValue>"
            + "      </ds:Reference>"
            + "    </ds:SignedInfo>"
            + "    <ds:SignatureValue>ZHVtbXk=</ds:SignatureValue>"
            + "    <ds:KeyInfo><ds:X509Data><ds:X509SubjectName>CN=Original</ds:X509SubjectName></ds:X509Data></ds:KeyInfo>"
            + "  </ds:Signature>"
            + "</doc>";
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static Document parse(byte[] bytes) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    DocumentBuilder builder = dbf.newDocumentBuilder();
    return builder.parse(new ByteArrayInputStream(bytes));
  }

  /**
   * Counter-signature'ın kendi KeyInfo'sundaki X509 sertifikasından public key'i çıkarıp validate
   * sırasında kullanır.
   */
  static final class X509KeySelector extends KeySelector {
    @Override
    public KeySelectorResult select(
        KeyInfo keyInfo,
        Purpose purpose,
        javax.xml.crypto.AlgorithmMethod method,
        XMLCryptoContext context) {
      for (Object xobj : keyInfo.getContent()) {
        if (!(xobj instanceof X509Data)) continue;
        for (Object o : ((X509Data) xobj).getContent()) {
          if (o instanceof X509Certificate) {
            final PublicKey pk = ((X509Certificate) o).getPublicKey();
            return new KeySelectorResult() {
              @Override
              public Key getKey() {
                return pk;
              }
            };
          }
        }
      }
      throw new RuntimeException("X509 sertifikası KeyInfo'da bulunamadı.");
    }
  }
}
