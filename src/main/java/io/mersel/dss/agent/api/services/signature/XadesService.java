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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;
import io.mersel.dss.agent.api.services.smartcard.SmartCardManager;

import xades4j.algorithms.EnvelopedSignatureTransform;
import xades4j.production.DataObjectReference;
import xades4j.production.SignedDataObjects;
import xades4j.production.XadesBesSigningProfile;
import xades4j.production.XadesSigner;
import xades4j.properties.DataObjectDesc;
import xades4j.providers.impl.KeyStoreKeyingDataProvider.KeyEntryPasswordProvider;
import xades4j.providers.impl.KeyStoreKeyingDataProvider.KeyStorePasswordProvider;
import xades4j.providers.impl.KeyStoreKeyingDataProvider.SigningCertSelector;
import xades4j.providers.impl.PKCS11KeyStoreKeyingDataProvider;

/**
 * XAdES-BES imzalama servisi.
 *
 * <p>İki ayrı akış desteklenir:
 *
 * <ul>
 *   <li>{@link #signXmlDocument} — düz XML belgesini XAdES-BES enveloped imzayla imzalar (xades4j
 *       1.7 + PKCS#11 keying provider).
 *   <li>{@link #signHrXmlCounterSignature} — <em>var olan</em> bir {@code &lt;ds:Signature&gt;}
 *       elementinin {@code SignatureValue}'su üzerine ETSI uyumlu {@code
 *       &lt;xades:CounterSignature&gt;} ekler. javax.xml.crypto.dsig ile PKCS#11 provider üzerinden
 *       imzalanır.
 * </ul>
 *
 * <p>İki akış da test edilebilirlik için {@link Pkcs11Session} parametresi alabilen yardımcılarla
 * beraber gelir; testler {@link Pkcs11Session#wrapForTest} ile software keystore üzerinden bu
 * yardımcıları çağırır.
 */
@Service
public class XadesService {

  private static final Logger log = LoggerFactory.getLogger(XadesService.class);

  static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";
  static final String XADES_NS = "http://uri.etsi.org/01903/v1.3.2#";

  /** SHA-384 digest URL (xmldsig-more). */
  static final String DIGEST_SHA384 = "http://www.w3.org/2001/04/xmldsig-more#sha384";
  /** ECDSA-SHA384 signature URL. */
  static final String SIG_ECDSA_SHA384 = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384";
  /**
   * RSA-SHA256 signature URL. JDK 1.8 javax.xml.crypto.dsig.SignatureMethod'da constant olarak yok.
   */
  static final String SIG_RSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";

  /** XAdES 1.3.2 — counter-signature Reference Type URI (parent {@code SignatureValue} hedefi). */
  static final String XADES_TYPE_COUNTERSIGNED_SIGNATURE =
      "http://uri.etsi.org/01903#CountersignedSignature";

  /** XAdES 1.3.2 — SignedProperties Reference Type URI. */
  static final String XADES_TYPE_SIGNED_PROPERTIES = "http://uri.etsi.org/01903#SignedProperties";

  /**
   * XAdES {@code SigningTime} formatı: {@code 2026-05-20T16:43:15.486+03:00}. Millisaniye 3 hane
   * sabit; saniyenin altı yuvarlanmıyor sadece kırpılıyor.
   */
  private static final DateTimeFormatter XADES_SIGNING_TIME_FORMAT =
      new DateTimeFormatterBuilder()
          .append(DateTimeFormatter.ISO_LOCAL_DATE)
          .appendLiteral('T')
          .appendPattern("HH:mm:ss")
          .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
          .appendOffset("+HH:MM", "Z")
          .toFormatter(Locale.ROOT);

  private final SmartCardManager cardManager;
  private final CertificateChainBuilder chainBuilder;

  public XadesService(SmartCardManager cardManager, CertificateChainBuilder chainBuilder) {
    this.cardManager = cardManager;
    this.chainBuilder = chainBuilder;
  }

  /* ================================================================== */
  /* Public API                                                          */
  /* ================================================================== */

  public byte[] signXmlDocument(SignDocumentDto dto) {
    Path libraryPath =
        cardManager.resolveLibrary(dto.getTerminalName(), dto.getPkcs11LibraryPath());
    log.info(
        "XAdES-BES imzalama: lib={}, terminal={}, certId={}",
        libraryPath,
        dto.getTerminalName(),
        dto.getCertificateId());

    byte[] xmlBytes = readBytes(dto);
    try {
      return doXadesBesSign(xmlBytes, libraryPath, dto.getCertificateId(), dto.getPin());
    } catch (Exception e) {
      throw new SignatureOperationException("XAdES-BES imzalama başarısız: " + e.getMessage(), e);
    }
  }

  public byte[] signHrXmlCounterSignature(SignDocumentDto dto) {
    Path libraryPath =
        cardManager.resolveLibrary(dto.getTerminalName(), dto.getPkcs11LibraryPath());
    log.info(
        "XAdES CounterSignature: lib={}, terminal={}, certId={}",
        libraryPath,
        dto.getTerminalName(),
        dto.getCertificateId());

    try (Pkcs11Session session = Pkcs11Session.open(libraryPath, dto.getPin())) {
      return signHrWithSession(session, dto);
    }
  }

  /* ================================================================== */
  /* Test-friendly mid-level entries                                     */
  /* ================================================================== */

  /**
   * Counter-signature için test/runtime ortak implementasyonu. Çağıran kişi {@link Pkcs11Session}'ı
   * kendisi yönetir ({@link Pkcs11Session#wrapForTest} ile software keystore de olabilir).
   */
  public byte[] signHrWithSession(Pkcs11Session session, SignDocumentDto dto) {
    byte[] xmlBytes = readBytes(dto);
    String alias;
    try {
      alias = session.resolveAlias(dto.getCertificateId());
    } catch (Exception e) {
      throw new SignatureOperationException(
          "Counter-signature için sertifika çözülemedi: " + e.getMessage(), e);
    }
    PrivateKey privateKey = session.getPrivateKey(alias);
    Certificate[] chain = chainBuilder.build(session.getCertificateChain(alias));

    try {
      return doCounterSignature(xmlBytes, privateKey, chain);
    } catch (Exception e) {
      throw new SignatureOperationException(
          "XAdES CounterSignature başarısız: " + e.getMessage(), e);
    }
  }

  /* ================================================================== */
  /* XAdES-BES (xades4j) implementation                                  */
  /* ================================================================== */

  private byte[] doXadesBesSign(
      byte[] xmlBytes, Path libraryPath, String certIdentifier, String pin) throws Exception {
    final String pinLocal = pin;
    final String providerName = "merselXadesSigner-" + UUID.randomUUID().toString().substring(0, 8);

    PKCS11KeyStoreKeyingDataProvider keyingProvider =
        new PKCS11KeyStoreKeyingDataProvider(
            libraryPath.toString(),
            providerName,
            new SigningCertSelectorByIdentifier(certIdentifier),
            new KeyStorePasswordProvider() {
              @Override
              public char[] getPassword() {
                return pinLocal == null ? new char[0] : pinLocal.toCharArray();
              }
            },
            new KeyEntryPasswordProvider() {
              @Override
              public char[] getPassword(String alias, X509Certificate cert) {
                return pinLocal == null ? new char[0] : pinLocal.toCharArray();
              }
            },
            true);

    XadesBesSigningProfile profile = new XadesBesSigningProfile(keyingProvider);
    XadesSigner signer = profile.newSigner();

    Document document = parseXml(xmlBytes);
    Element root = document.getDocumentElement();

    DataObjectDesc dataObj =
        new DataObjectReference("").withTransform(new EnvelopedSignatureTransform());
    SignedDataObjects dataObjs = new SignedDataObjects().withSignedDataObject(dataObj);

    signer.sign(dataObjs, root);
    return serialise(document);
  }

  /**
   * X.509 serial number, alias adı veya CN üzerinden xades4j'in sertifika listesinden seçim yapar.
   */
  static final class SigningCertSelectorByIdentifier implements SigningCertSelector {
    private final String identifier;

    SigningCertSelectorByIdentifier(String identifier) {
      this.identifier = identifier == null ? "" : identifier.trim();
    }

    @Override
    public X509Certificate selectCertificate(List<X509Certificate> certs) {
      if (certs == null || certs.isEmpty()) return null;
      if (identifier.isEmpty()) return certs.get(0);
      String norm =
          identifier.replaceAll("\\s+", "").replaceFirst("^0x", "").toUpperCase(Locale.ROOT);
      for (X509Certificate cert : certs) {
        String certSerial = cert.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
        if (norm.equalsIgnoreCase(certSerial)) return cert;

        String subject = cert.getSubjectX500Principal().getName();
        if (subject != null
            && subject.toUpperCase(Locale.ROOT).contains(identifier.toUpperCase(Locale.ROOT))) {
          return cert;
        }
      }
      return certs.get(0);
    }
  }

  /* ================================================================== */
  /* XAdES Counter-signature implementation                              */
  /* ================================================================== */

  /**
   * Var olan {@code <ds:Signature>}'ın {@code <ds:SignatureValue>}'su üzerine ETSI XAdES-BES uyumlu
   * bir {@code <xades:CounterSignature>} ekler.
   *
   * <p>Üretilen counter-signature, kendi başına eksiksiz bir XAdES-BES imzasıdır:
   *
   * <ul>
   *   <li>{@code SignedInfo} iki {@code Reference} içerir:
   *       <ul>
   *         <li>Kendi {@code SignedProperties}'i hedefler — Type = {@code
   *             http://uri.etsi.org/01903#SignedProperties}.
   *         <li>Karşı imzalanan {@code SignatureValue}'u hedefler — Type = {@code
   *             http://uri.etsi.org/01903#CountersignedSignature}, c14n WithComments transform.
   *       </ul>
   *   <li>{@code KeyInfo} sadece imzacı sertifikasını içerir.
   *   <li>{@code Object/QualifyingProperties} altında {@code SignedProperties} ( {@code
   *       SigningTime} + {@code SigningCertificate}: CertDigest + IssuerSerial) bulunur.
   * </ul>
   *
   * <p>İmzacı sertifikası RSA ise {@code RSA-SHA256 / SHA-256}, ECDSA ise {@code ECDSA-SHA384 /
   * SHA-384} kullanılır. {@code DOMSignContext}'e {@code ds} ve {@code xades} prefix'leri
   * sabitlenir; böylece üretilen XML İmzager Kurumsal gibi araçlarda XAdES profilinde
   * doğrulanabilir hale gelir.
   */
  byte[] doCounterSignature(byte[] xmlBytes, PrivateKey privateKey, Certificate[] chain)
      throws Exception {
    Document doc = parseXml(xmlBytes);

    NodeList sigs = doc.getElementsByTagNameNS(DS_NS, "Signature");
    if (sigs.getLength() == 0) {
      throw new IllegalArgumentException(
          "XML'de imzalanacak <ds:Signature> bulunamadı (counter-sig için zorunlu).");
    }
    Element existingSig = (Element) sigs.item(0);

    Element parentSigValueEl = findChildSignatureValue(existingSig);
    if (parentSigValueEl == null) {
      throw new IllegalArgumentException("Mevcut <ds:Signature> içinde <ds:SignatureValue> yok.");
    }

    // Karşı imzalanacak SignatureValue'a XML ID garantisi (URI fragment dereferencing için)
    String parentSigValueId = parentSigValueEl.getAttribute("Id");
    if (parentSigValueId == null || parentSigValueId.isEmpty()) {
      parentSigValueId = "Signature-Value-Id-" + UUID.randomUUID();
      parentSigValueEl.setAttribute("Id", parentSigValueId);
    }
    parentSigValueEl.setIdAttribute("Id", true);

    Element ussp = findOrCreateUnsignedSignatureProperties(existingSig, doc);

    Element counterSig = doc.createElementNS(XADES_NS, "xades:CounterSignature");
    ussp.appendChild(counterSig);

    // Counter-signature'a ait Id'ler — örnek dokümanla aynı isim şablonu, hot-path kısa-vadede.
    String counterSigId = "Signature-Id-" + UUID.randomUUID();
    String counterSigValueId = "Signature-Value-Id-" + UUID.randomUUID();
    String signedPropsId = "Signed-Properties-Id-" + UUID.randomUUID();
    String objectId = "Object-Id-" + UUID.randomUUID();
    String signedPropsRefId = "Reference-Id-" + UUID.randomUUID();
    String counterRefId = "Reference-Id-" + UUID.randomUUID();

    // Algoritma seçimi: RSA-2048 NES = RSA-SHA256 / SHA-256, ECDSA = ECDSA-SHA384 / SHA-384
    X509Certificate signingCert = (X509Certificate) chain[0];
    boolean ecdsa = isEcdsa(signingCert);
    String digestUrl = ecdsa ? DIGEST_SHA384 : DigestMethod.SHA256;
    String sigUrl = ecdsa ? SIG_ECDSA_SHA384 : SIG_RSA_SHA256;

    // 1) QualifyingProperties / SignedProperties DOM ağacını kur — JSR 105 XMLObject içine
    //    DOMStructure olarak konacak. SignedProperties'in Id attribute'unu XML ID olarak işaretle
    // ki
    //    Reference URI="#signedPropsId" digest hesaplanırken dereferencing düşmesin.
    Element qualifyingProps = buildQualifyingProperties(doc, counterSigId, signedPropsId);
    Element signedPropsEl = (Element) qualifyingProps.getFirstChild();
    populateSignedProperties(doc, signedPropsEl, signingCert, digestUrl);

    XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

    DOMStructure qpStruct = new DOMStructure(qualifyingProps);
    XMLObject xmlObject =
        fac.newXMLObject(Collections.singletonList(qpStruct), objectId, null, null);

    // 2) İki Reference (XAdES-BES: kendi SignedProperties + karşı imzalanan SignatureValue)
    Reference signedPropsRef =
        fac.newReference(
            "#" + signedPropsId,
            fac.newDigestMethod(digestUrl, null),
            null,
            XADES_TYPE_SIGNED_PROPERTIES,
            signedPropsRefId);

    Reference counterRef =
        fac.newReference(
            "#" + parentSigValueId,
            fac.newDigestMethod(digestUrl, null),
            Collections.singletonList(
                fac.newTransform(
                    CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS, (TransformParameterSpec) null)),
            XADES_TYPE_COUNTERSIGNED_SIGNATURE,
            counterRefId);

    SignedInfo si =
        fac.newSignedInfo(
            fac.newCanonicalizationMethod(
                CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS, (C14NMethodParameterSpec) null),
            fac.newSignatureMethod(sigUrl, null),
            Arrays.asList(signedPropsRef, counterRef));

    // 3) KeyInfo — yalnız imzacı sertifikası (xades:Cert ile zaten chain'e bağlanıyor)
    KeyInfoFactory kif = fac.getKeyInfoFactory();
    X509Data x509Data = kif.newX509Data(Collections.<Object>singletonList(signingCert));
    KeyInfo ki = kif.newKeyInfo(Collections.singletonList(x509Data));

    // 4) XMLSignature — Id ve SignatureValue Id explicit, Object listesi içeride QP taşıyor
    javax.xml.crypto.dsig.XMLSignature xmlSig =
        fac.newXMLSignature(
            si, ki, Collections.singletonList(xmlObject), counterSigId, counterSigValueId);

    DOMSignContext sc = new DOMSignContext(privateKey, counterSig);
    sc.putNamespacePrefix(DS_NS, "ds");
    sc.putNamespacePrefix(XADES_NS, "xades");

    xmlSig.sign(sc);

    return serialise(doc);
  }

  /**
   * {@code <xades:QualifyingProperties Target="#sigId"><xades:SignedProperties Id="...">}
   * iskeletini üretir. {@code SignedProperties}'in {@code Id} attribute'u XML ID olarak
   * işaretlenir.
   */
  private static Element buildQualifyingProperties(
      Document doc, String counterSigId, String signedPropsId) {
    Element qp = doc.createElementNS(XADES_NS, "xades:QualifyingProperties");
    qp.setAttribute("Target", "#" + counterSigId);
    Element sp = doc.createElementNS(XADES_NS, "xades:SignedProperties");
    sp.setAttribute("Id", signedPropsId);
    sp.setIdAttribute("Id", true);
    qp.appendChild(sp);
    return qp;
  }

  /**
   * {@code SignedProperties} altına {@code SignedSignatureProperties} kurarak {@code SigningTime} +
   * {@code SigningCertificate} (CertDigest + IssuerSerial) yerleştirir.
   */
  private static void populateSignedProperties(
      Document doc, Element signedProps, X509Certificate signingCert, String digestUrl)
      throws Exception {
    Element ssp = doc.createElementNS(XADES_NS, "xades:SignedSignatureProperties");
    signedProps.appendChild(ssp);

    Element signingTime = doc.createElementNS(XADES_NS, "xades:SigningTime");
    signingTime.setTextContent(currentXadesSigningTime());
    ssp.appendChild(signingTime);

    Element signingCertificate = doc.createElementNS(XADES_NS, "xades:SigningCertificate");
    ssp.appendChild(signingCertificate);

    Element certEl = doc.createElementNS(XADES_NS, "xades:Cert");
    signingCertificate.appendChild(certEl);

    Element certDigest = doc.createElementNS(XADES_NS, "xades:CertDigest");
    certEl.appendChild(certDigest);
    Element certDigestMethod = doc.createElementNS(DS_NS, "ds:DigestMethod");
    certDigestMethod.setAttribute("Algorithm", digestUrl);
    certDigest.appendChild(certDigestMethod);
    Element certDigestValue = doc.createElementNS(DS_NS, "ds:DigestValue");
    certDigestValue.setTextContent(certificateDigestBase64(signingCert, digestUrl));
    certDigest.appendChild(certDigestValue);

    Element issuerSerial = doc.createElementNS(XADES_NS, "xades:IssuerSerial");
    certEl.appendChild(issuerSerial);
    Element x509IssuerName = doc.createElementNS(DS_NS, "ds:X509IssuerName");
    x509IssuerName.setTextContent(
        signingCert.getIssuerX500Principal().getName(X500Principal.RFC2253));
    issuerSerial.appendChild(x509IssuerName);
    Element x509SerialNumber = doc.createElementNS(DS_NS, "ds:X509SerialNumber");
    x509SerialNumber.setTextContent(signingCert.getSerialNumber().toString());
    issuerSerial.appendChild(x509SerialNumber);
  }

  /** Sertifikanın DER kodlamasını verilen XAdES digest algoritmasıyla hash'leyip Base64 döner. */
  private static String certificateDigestBase64(X509Certificate cert, String digestUrl)
      throws Exception {
    String javaAlg = digestUrl.endsWith("sha384") ? "SHA-384" : "SHA-256";
    MessageDigest md = MessageDigest.getInstance(javaAlg);
    byte[] digest = md.digest(cert.getEncoded());
    return Base64.getEncoder().encodeToString(digest);
  }

  /** Sistem saatinden XAdES uyumlu (millis hassasiyetinde, offset'li) SigningTime üretir. */
  private static String currentXadesSigningTime() {
    OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS);
    return XADES_SIGNING_TIME_FORMAT.format(now);
  }

  /**
   * {@code <ds:Signature>} ALTINDA (descendant) ilk {@code <ds:SignatureValue>}'u döner. Standart
   * XAdES'te her signature'ın tam olarak bir tane vardır.
   */
  private static Element findChildSignatureValue(Element signatureEl) {
    NodeList all = signatureEl.getElementsByTagNameNS(DS_NS, "SignatureValue");
    if (all.getLength() == 0) return null;
    return (Element) all.item(0);
  }

  /**
   * {@code Signature/Object/QualifyingProperties/UnsignedProperties/UnsignedSignatureProperties}
   * yolunda eksik node'ları sırayla oluşturup en alttakini döner.
   */
  private static Element findOrCreateUnsignedSignatureProperties(
      Element signatureEl, Document doc) {
    Element qualifyingProps =
        (Element) singleDescendantNs(signatureEl, XADES_NS, "QualifyingProperties");
    if (qualifyingProps == null) {
      // Object/QualifyingProperties yoksa, mevcut bir Object'in altına koy
      // veya yeni bir Object oluştur.
      Element obj = (Element) firstChildLocal(signatureEl, DS_NS, "Object");
      if (obj == null) {
        obj = doc.createElementNS(DS_NS, "ds:Object");
        signatureEl.appendChild(obj);
      }
      qualifyingProps = doc.createElementNS(XADES_NS, "xades:QualifyingProperties");
      String sigId = signatureEl.getAttribute("Id");
      if (sigId == null || sigId.isEmpty()) {
        sigId = "MerselSig-" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 12);
        signatureEl.setAttribute("Id", sigId);
        signatureEl.setIdAttribute("Id", true);
      }
      qualifyingProps.setAttribute("Target", "#" + sigId);
      obj.appendChild(qualifyingProps);
    }

    Element unsignedProps =
        (Element) firstChildLocal(qualifyingProps, XADES_NS, "UnsignedProperties");
    if (unsignedProps == null) {
      unsignedProps = doc.createElementNS(XADES_NS, "xades:UnsignedProperties");
      qualifyingProps.appendChild(unsignedProps);
    }

    Element ussp =
        (Element) firstChildLocal(unsignedProps, XADES_NS, "UnsignedSignatureProperties");
    if (ussp == null) {
      ussp = doc.createElementNS(XADES_NS, "xades:UnsignedSignatureProperties");
      unsignedProps.appendChild(ussp);
    }
    return ussp;
  }

  private static Node singleDescendantNs(Element parent, String ns, String localName) {
    NodeList list = parent.getElementsByTagNameNS(ns, localName);
    if (list.getLength() == 0) return null;
    return list.item(0);
  }

  private static Node firstChildLocal(Element parent, String ns, String localName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n.getNodeType() != Node.ELEMENT_NODE) continue;
      String nNs = n.getNamespaceURI();
      String nLocal = n.getLocalName();
      if (nLocal == null) nLocal = n.getNodeName();
      if (ns.equals(nNs) && localName.equals(nLocal)) return n;
    }
    return null;
  }

  static boolean isEcdsa(X509Certificate cert) {
    String algo = cert.getPublicKey().getAlgorithm();
    if (algo == null) return false;
    String upper = algo.toUpperCase(Locale.ROOT);
    return upper.contains("EC");
  }

  /* ================================================================== */
  /* DOM helpers                                                         */
  /* ================================================================== */

  private static Document parseXml(byte[] xmlBytes) {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder builder = dbf.newDocumentBuilder();
      return builder.parse(new ByteArrayInputStream(xmlBytes));
    } catch (Exception e) {
      throw new SignatureOperationException("XML parse edilemedi: " + e.getMessage(), e);
    }
  }

  private static byte[] serialise(Document doc) {
    try {
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      transformer.transform(new DOMSource(doc), new StreamResult(out));
      return out.toByteArray();
    } catch (Exception e) {
      throw new SignatureOperationException("XML serileştirilemedi: " + e.getMessage(), e);
    }
  }

  private static byte[] readBytes(SignDocumentDto dto) {
    if (dto == null || dto.getContent() == null) {
      throw new IllegalArgumentException("İmzalanacak içerik boş.");
    }
    try {
      byte[] bytes = dto.getContent().getBytes();
      if (bytes == null || bytes.length == 0) {
        throw new IllegalArgumentException("İmzalanacak XML boş.");
      }
      return bytes;
    } catch (java.io.IOException e) {
      throw new SignatureOperationException("Yüklenen XML okunamadı: " + e.getMessage(), e);
    }
  }
}
