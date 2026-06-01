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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * {@link XadesService#wrapBase64(String)} ve {@link
 * XadesService#rewrapBase64InSignatureSubtree(Element)} regresyon testleri — XAdES çıktısında
 * {@code <ds:X509Certificate>} ve {@code <ds:SignatureValue>} text node'larının standart 76 char LF
 * wrap'ine normalize edildiğini ve DOM Transformer'ın çıktısında {@code &#13;} (CR entity)
 * üretmediğini doğrular.
 *
 * <h2>Neden bu test?</h2>
 *
 * <p>Apache Santuario {@code KeyInfo.add(X509Certificate)} ve xades4j üst-katmanı, sertifika DER
 * bytes'ını Base64'e çevirirken Apache Commons Codec MIME modunu kullanır → çıktı 76 karakterde
 * {@code \r\n} ile bölünür. Sonra DOM Transformer XML 1.0 spec gereği literal CR'yi {@code &#13;}
 * olarak entity-encode eder (round-trip korunsun diye). Sahada agent'ın ürettiği XAdES dosyası
 * standart araçlardan üretilenle görsel olarak farklı çıkıyordu (her satır sonu {@code &#13;}
 * taşıyordu), GİB e-fatura entegratörlerinin görsel imza önizleme akışında sorun yaratıyordu.
 *
 * <p>Çözüm: imza üretildikten sonra subtree içindeki {@code <ds:X509Certificate>} ve {@code
 * <ds:SignatureValue>} text node'larını standart 76-char LF wrap'ine normalize ediyoruz. Bu iki
 * node canonicalization / digest girişi <em>değil</em> — yalnız Base64 decode edilip ham byte'lara
 * dönüşüyor; whitespace zaten yutulduğu için imza geçerliliği etkilenmez.
 *
 * <p>Aynı çözümün server projesindeki kardeşi: {@code
 * TestUserCounterSignatureService#rewrapBase64InSubtree} (regresyon testi: {@code
 * TestUserCounterSignatureCleanOutputTest}).
 */
class XadesServiceBase64WrapTest {

  private static final int EXPECTED_LINE_WIDTH = 76;
  private static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";

  @Test
  @DisplayName("wrapBase64: 76 karakterden kısa string olduğu gibi döner")
  void shortStringPassesThrough() {
    String input = "QUJDREVGR0g="; // 12 char
    assertThat(XadesService.wrapBase64(input)).isEqualTo(input);
  }

  @Test
  @DisplayName("wrapBase64: tam 76 karakter wrap edilmez")
  void exactly76CharsNotWrapped() {
    String input = repeat('A', 76);
    String wrapped = XadesService.wrapBase64(input);
    assertThat(wrapped).isEqualTo(input).doesNotContain("\n");
  }

  @Test
  @DisplayName("wrapBase64: 77 karakterde bir kez LF ile böler (\\r üretmez)")
  void wrapsAt76CharsWithLfOnly() {
    String input = repeat('A', 152); // tam 2 satır
    String wrapped = XadesService.wrapBase64(input);

    assertThat(wrapped).doesNotContain("\r");
    String[] lines = wrapped.split("\n", -1);
    assertThat(lines).hasSize(2);
    assertThat(lines[0]).hasSize(EXPECTED_LINE_WIDTH);
    assertThat(lines[1]).hasSize(EXPECTED_LINE_WIDTH);
  }

  @Test
  @DisplayName(
      "wrapBase64: gerçekçi sertifika büyüklüğü (1500 char) → her satır ≤76, son satır kısa")
  void realisticCertificateSizeWraps() {
    String input = repeat('B', 1500);
    String wrapped = XadesService.wrapBase64(input);

    assertThat(wrapped).doesNotContain("\r");
    String[] lines = wrapped.split("\n", -1);
    for (int i = 0; i < lines.length - 1; i++) {
      assertThat(lines[i]).hasSize(EXPECTED_LINE_WIDTH);
    }
    assertThat(lines[lines.length - 1].length()).isLessThanOrEqualTo(EXPECTED_LINE_WIDTH);
    // Roundtrip: tüm whitespace çıkarınca aynen geri gelir
    assertThat(wrapped.replaceAll("\\s+", "")).isEqualTo(input);
  }

  @Test
  @DisplayName(
      "rewrapBase64InSignatureSubtree: Santuario CRLF wrapping LF wrap'ine normalize edilir")
  void rewrapsExistingCrlfToLf() throws Exception {
    Document doc = parseDom(buildSampleXadesDocument(/* withCrlf= */ true));
    Element signature = (Element) doc.getElementsByTagNameNS(DS_NS, "Signature").item(0);

    XadesService.rewrapBase64InSignatureSubtree(signature);

    Element x509 = (Element) signature.getElementsByTagNameNS(DS_NS, "X509Certificate").item(0);
    Element sv = (Element) signature.getElementsByTagNameNS(DS_NS, "SignatureValue").item(0);

    assertThat(x509.getTextContent()).doesNotContain("\r");
    assertThat(sv.getTextContent()).doesNotContain("\r");
    // Hiçbir satır 76 karakteri aşmamalı
    for (String line : x509.getTextContent().split("\n", -1)) {
      assertThat(line.length()).isLessThanOrEqualTo(EXPECTED_LINE_WIDTH);
    }
  }

  @Test
  @DisplayName(
      "Serialised XML çıktısında '&#13;' (CR entity) bulunmaz — Transformer round-trip kontrolü")
  void serialisedOutputContainsNoCarriageReturnEntity() throws Exception {
    Document doc = parseDom(buildSampleXadesDocument(/* withCrlf= */ true));
    Element signature = (Element) doc.getElementsByTagNameNS(DS_NS, "Signature").item(0);

    XadesService.rewrapBase64InSignatureSubtree(signature);

    String serialised = serialise(doc);
    assertThat(serialised)
        .as("Çıktıda XML CR entity'si bulunmamalı (Santuario CRLF normalize edilmeli)")
        .doesNotContain("&#13;")
        .doesNotContain("&#xD;");
  }

  @Test
  @DisplayName("rewrapBase64InSignatureSubtree: null root parametresi NPE yerine no-op döner")
  void nullRootIsNoOp() {
    XadesService.rewrapBase64InSignatureSubtree(null);
  }

  @Test
  @DisplayName(
      "rewrapBase64InSignatureSubtree: tek-satır Base64 (whitespace içermeyen) zaten temizse dokunmaz")
  void singleLineIsLeftAlone() throws Exception {
    String singleLineCert = "MIIBpzCCAU0=";
    Document doc =
        parseDom(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ds:Signature xmlns:ds=\""
                + DS_NS
                + "\">"
                + "<ds:KeyInfo><ds:X509Data><ds:X509Certificate>"
                + singleLineCert
                + "</ds:X509Certificate></ds:X509Data></ds:KeyInfo>"
                + "<ds:SignatureValue>QQ==</ds:SignatureValue>"
                + "</ds:Signature>");

    XadesService.rewrapBase64InSignatureSubtree(doc.getDocumentElement());

    Element x509 =
        (Element) doc.getDocumentElement().getElementsByTagNameNS(DS_NS, "X509Certificate").item(0);
    assertThat(x509.getTextContent()).isEqualTo(singleLineCert);
  }

  /* ------------------------------------------------------------------ */

  private static String repeat(char c, int n) {
    char[] arr = new char[n];
    java.util.Arrays.fill(arr, c);
    return new String(arr);
  }

  private static String buildSampleXadesDocument(boolean withCrlf) {
    // 1500 karakterlik fake Base64; CRLF'li versiyonda her 76 karakterde \r\n araya gir
    String raw = repeat('Z', 1500);
    String certText = withCrlf ? insertCrlfEvery(raw, EXPECTED_LINE_WIDTH) : raw;
    String svRaw = repeat('Y', 256);
    String svText = withCrlf ? insertCrlfEvery(svRaw, EXPECTED_LINE_WIDTH) : svRaw;

    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<root xmlns=\"urn:test\">"
        + "  <ds:Signature xmlns:ds=\""
        + DS_NS
        + "\" Id=\"sig1\">"
        + "    <ds:SignedInfo></ds:SignedInfo>"
        + "    <ds:SignatureValue Id=\"sv1\">"
        + svText
        + "</ds:SignatureValue>"
        + "    <ds:KeyInfo><ds:X509Data><ds:X509Certificate>"
        + certText
        + "</ds:X509Certificate></ds:X509Data></ds:KeyInfo>"
        + "  </ds:Signature>"
        + "</root>";
  }

  private static String insertCrlfEvery(String input, int width) {
    StringBuilder sb = new StringBuilder(input.length() + (input.length() / width) * 2);
    int offset = 0;
    while (offset < input.length()) {
      int end = Math.min(offset + width, input.length());
      sb.append(input, offset, end);
      if (end < input.length()) {
        sb.append("\r\n");
      }
      offset = end;
    }
    return sb.toString();
  }

  private static Document parseDom(String xml) throws Exception {
    return parseDom(xml.getBytes(StandardCharsets.UTF_8));
  }

  private static Document parseDom(byte[] xml) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    DocumentBuilder builder = dbf.newDocumentBuilder();
    return builder.parse(new ByteArrayInputStream(xml));
  }

  private static String serialise(Document doc) throws Exception {
    Transformer t = TransformerFactory.newInstance().newTransformer();
    t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    t.transform(new DOMSource(doc), new StreamResult(out));
    return out.toString(StandardCharsets.UTF_8.name());
  }
}
