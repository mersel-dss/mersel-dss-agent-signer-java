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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
 * Cert public key tipine (RSA / EC) göre {@code ds:KeyInfo} altında doğru {@code KeyValue}
 * elementinin üretildiğini kilitler. xades4j PKCS#11 ana akışı (`doXadesBesSign` →
 * `BasicSignatureOptions.includePublicKey(true)`) ile JSR 105 counter-signature akışı
 * (`doCounterSignature` → `KeyInfoFactory.newKeyValue`) farklı kod yollarını kullanır ama aynı
 * çıktıyı vermeli:
 *
 * <ul>
 *   <li>RSA imzacı sertifikası → {@code <ds:KeyValue><ds:RSAKeyValue><ds:Modulus/>
 *       <ds:Exponent/></ds:RSAKeyValue></ds:KeyValue>}
 *   <li>EC imzacı sertifikası → {@code <ds:KeyValue><dsig11:ECKeyValue
 *       xmlns:dsig11="http://www.w3.org/2009/xmldsig11#"><dsig11:NamedCurve URI="urn:oid:..."/>
 *       <dsig11:PublicKey>...</dsig11:PublicKey></dsig11:ECKeyValue></ds:KeyValue>}
 * </ul>
 *
 * <p>Sahada e-imza ekosisteminde RSA-2048 ve EC P-384 (NIST secp384r1) en yaygın imzacı sertifika
 * türleri; TÜBİTAK XAdES uygulama kılavuzu KeyInfo'da KeyValue blogu da bekler. Server-side kardeş
 * proje
 * (`mersel-dss-server-signer-java/src/main/java/eu/europa/esig/dss/xades/signature/XAdESSignatureBuilder.java`)
 * tamamen DSS'e geçtikten sonra aynı zenginleştirmeyi manuel olarak override etti (`addRSAKeyValue`
 * / `addECKeyValue` metotları); agent xades4j path'inde kalacağı için bu davranışı
 * `BasicSignatureOptions.includePublicKey(true)` switch'i ile xmlsec'in zaten hazır olan key-type
 * dispatch'inden ücretsiz alır.
 */
class XadesKeyInfoEnrichmentTest {

  private static final String DSIG11_NS = "http://www.w3.org/2009/xmldsig11#";

  @BeforeAll
  static void initApacheSantuario() {
    Init.init();
  }

  @Test
  void rsaSigningCert_emitsRsaKeyValueInCounterSignatureKeyInfo() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());

    Document signedDoc = produceCounterSignedDocument(key);

    // CounterSignature içindeki ds:Signature'ın KeyInfo'sunu bul (ilk ds:Signature dummy parent,
    // ikincisi counter-sig).
    Element counterSigElement = locateCounterSignatureElement(signedDoc);
    Element keyInfo =
        (Element) counterSigElement.getElementsByTagNameNS(XadesService.DS_NS, "KeyInfo").item(0);

    // X509Data + X509Certificate her zaman var.
    NodeList certs = keyInfo.getElementsByTagNameNS(XadesService.DS_NS, "X509Certificate");
    assertThat(certs.getLength()).as("X509Certificate sayısı").isEqualTo(1);

    // RSA için: <ds:KeyValue><ds:RSAKeyValue><ds:Modulus/><ds:Exponent/></ds:RSAKeyValue>
    NodeList keyValues = keyInfo.getElementsByTagNameNS(XadesService.DS_NS, "KeyValue");
    assertThat(keyValues.getLength()).as("KeyValue eleman sayısı").isEqualTo(1);
    Element keyValueEl = (Element) keyValues.item(0);

    NodeList rsaKeyValues = keyValueEl.getElementsByTagNameNS(XadesService.DS_NS, "RSAKeyValue");
    assertThat(rsaKeyValues.getLength()).as("RSA cert için ds:RSAKeyValue olmalı").isEqualTo(1);

    Element rsaEl = (Element) rsaKeyValues.item(0);
    NodeList modulus = rsaEl.getElementsByTagNameNS(XadesService.DS_NS, "Modulus");
    NodeList exponent = rsaEl.getElementsByTagNameNS(XadesService.DS_NS, "Exponent");
    assertThat(modulus.getLength()).as("ds:Modulus olmalı").isEqualTo(1);
    assertThat(exponent.getLength()).as("ds:Exponent olmalı").isEqualTo(1);
    assertThat(modulus.item(0).getTextContent()).isNotBlank();
    assertThat(exponent.item(0).getTextContent()).isNotBlank();

    // EC keyValue olmamalı (RSA cert için).
    NodeList ecKeyValues = keyValueEl.getElementsByTagNameNS(DSIG11_NS, "ECKeyValue");
    assertThat(ecKeyValues.getLength()).as("RSA cert için dsig11:ECKeyValue olmamalı").isZero();
  }

  @Test
  void ecSigningCert_emitsDsig11EcKeyValueInCounterSignatureKeyInfo() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM02_EC384;
    assumeTrue(key.isAvailable(), "Skip — PFX yok: " + key.getAbsolutePath());

    Document signedDoc = produceCounterSignedDocument(key);
    Element counterSigElement = locateCounterSignatureElement(signedDoc);
    Element keyInfo =
        (Element) counterSigElement.getElementsByTagNameNS(XadesService.DS_NS, "KeyInfo").item(0);

    NodeList certs = keyInfo.getElementsByTagNameNS(XadesService.DS_NS, "X509Certificate");
    assertThat(certs.getLength()).as("X509Certificate sayısı").isEqualTo(1);

    // EC için: <ds:KeyValue><dsig11:ECKeyValue><dsig11:NamedCurve/>
    //          <dsig11:PublicKey/></dsig11:ECKeyValue></ds:KeyValue>
    NodeList keyValues = keyInfo.getElementsByTagNameNS(XadesService.DS_NS, "KeyValue");
    assertThat(keyValues.getLength()).as("KeyValue eleman sayısı").isEqualTo(1);
    Element keyValueEl = (Element) keyValues.item(0);

    NodeList ecKeyValues = keyValueEl.getElementsByTagNameNS(DSIG11_NS, "ECKeyValue");
    assertThat(ecKeyValues.getLength())
        .as("EC cert için dsig11:ECKeyValue olmalı (XML-DSig 1.1 namespace)")
        .isEqualTo(1);

    Element ecKeyValue = (Element) ecKeyValues.item(0);
    NodeList namedCurve = ecKeyValue.getElementsByTagNameNS(DSIG11_NS, "NamedCurve");
    NodeList publicKey = ecKeyValue.getElementsByTagNameNS(DSIG11_NS, "PublicKey");
    assertThat(namedCurve.getLength())
        .as("dsig11:NamedCurve olmalı (örn. URI=urn:oid:1.3.132.0.34 = secp384r1)")
        .isEqualTo(1);
    assertThat(publicKey.getLength())
        .as("dsig11:PublicKey olmalı (uncompressed EC point Base64)")
        .isEqualTo(1);

    // secp384r1 — KURUM02 P-384 sertifikası için kilit invariant.
    String curveUri = ((Element) namedCurve.item(0)).getAttribute("URI");
    assertThat(curveUri)
        .as("KURUM02 EC P-384 için NamedCurve URI urn:oid:1.3.132.0.34 (secp384r1)")
        .isEqualTo("urn:oid:1.3.132.0.34");

    // RSA keyValue olmamalı (EC cert için).
    NodeList rsaKeyValues = keyValueEl.getElementsByTagNameNS(XadesService.DS_NS, "RSAKeyValue");
    assertThat(rsaKeyValues.getLength()).as("EC cert için ds:RSAKeyValue olmamalı").isZero();
  }

  /**
   * PFX test sertifikasını yükleyip counter-signature akışını çalıştırır ve sonuç XML'i parse edip
   * Document döner. Hem RSA hem EC pfx için ortak yardımcı.
   */
  private static Document produceCounterSignedDocument(PfxTestKey key) throws Exception {
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
            io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder.passthrough(),
            null,
            new io.mersel.dss.agent.api.services.virtualtoken.VirtualTokenRegistry());

    byte[] counterSignedXml;
    try (Pkcs11Session session = loaded.openSession()) {
      counterSignedXml = service.signHrWithSession(session, dto, null);
    }
    return parse(counterSignedXml);
  }

  /**
   * Karşı imzalanmış belgede {@code xades:CounterSignature} altındaki {@code ds:Signature}
   * elementini döner.
   */
  private static Element locateCounterSignatureElement(Document doc) {
    NodeList counterSigs = doc.getElementsByTagNameNS(XadesService.XADES_NS, "CounterSignature");
    Element counterContainer = (Element) counterSigs.item(0);
    NodeList innerSigs = counterContainer.getElementsByTagNameNS(XadesService.DS_NS, "Signature");
    return (Element) innerSigs.item(0);
  }

  private static byte[] sampleSignedXadesDocument() {
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<doc xmlns=\"urn:test\">"
            + "  <body>HR Faturası</body>"
            + "  <ds:Signature xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" Id=\"sig1\">"
            + "    <ds:SignedInfo>"
            + "      <ds:CanonicalizationMethod"
            + " Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments\"/>"
            + "      <ds:SignatureMethod"
            + " Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"/>"
            + "      <ds:Reference URI=\"\">"
            + "        <ds:DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"/>"
            + "        <ds:DigestValue>AAAA</ds:DigestValue>"
            + "      </ds:Reference>"
            + "    </ds:SignedInfo>"
            + "    <ds:SignatureValue>ZHVtbXk=</ds:SignatureValue>"
            + "    <ds:KeyInfo><ds:X509Data><ds:X509SubjectName>CN=Original</ds:X509SubjectName>"
            + "</ds:X509Data></ds:KeyInfo>"
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
}
