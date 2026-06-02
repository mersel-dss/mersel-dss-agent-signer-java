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
import java.security.AuthProvider;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
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

import javax.security.auth.login.LoginException;
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

import org.apache.xml.security.Init;
import org.apache.xml.security.algorithms.JCEMapper;
import org.apache.xml.security.signature.ObjectContainer;
import org.apache.xml.security.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.exceptions.CauseChainExtractor;
import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.exceptions.SignerException;
import io.mersel.dss.agent.api.models.SignatureDiagnostics;
import io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder;
import io.mersel.dss.agent.api.services.keystore.BouncyCastleSetup;
import io.mersel.dss.agent.api.services.keystore.IaikPkcs11Signer;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;
import io.mersel.dss.agent.api.services.smartcard.SmartCardInfo;
import io.mersel.dss.agent.api.services.smartcard.SmartCardManager;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;

import xades4j.algorithms.EnvelopedSignatureTransform;
import xades4j.production.BasicSignatureOptions;
import xades4j.production.DataObjectReference;
import xades4j.production.SignedDataObjects;
import xades4j.production.SigningCertificateMode;
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
  private final SmartCardReaderService readerService;

  @Autowired
  public XadesService(
      SmartCardManager cardManager,
      CertificateChainBuilder chainBuilder,
      SmartCardReaderService readerService) {
    this.cardManager = cardManager;
    this.chainBuilder = chainBuilder;
    this.readerService = readerService;
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

    SignatureDiagnostics diag = baseDiagnosticsFor(dto, libraryPath);
    byte[] xmlBytes = readBytes(dto);
    try {
      return doXadesBesSign(xmlBytes, libraryPath, dto.getCertificateId(), dto.getPin(), diag);
    } catch (SignerException known) {
      // Resolver veya alt katman zaten yapısal exception fırlatmış. SunPKCS11'in bilinen
      // patolojilerinden biriyse (CKA_ID collision veya CKR_USER_NOT_LOGGED_IN) IAIK PKCS#11
      // wrapper fallback'ine düş; aksi halde tanılamayı bağlayıp olduğu gibi yeniden fırlat.
      if (IaikPkcs11Signer.requiresIaikFallback(known)) {
        logFallbackReason(known, dto);
        return doXadesBesSignNativeWithDiag(
            xmlBytes, libraryPath, dto.getCertificateId(), dto.getPin(), diag, known);
      }
      if (known.getDiagnostics() == null) {
        known.withDiagnostics(diag);
      }
      throw known;
    } catch (Exception e) {
      if (IaikPkcs11Signer.requiresIaikFallback(e)) {
        logFallbackReason(e, dto);
        return doXadesBesSignNativeWithDiag(
            xmlBytes, libraryPath, dto.getCertificateId(), dto.getPin(), diag, e);
      }
      String code = classifySignatureFailure(e);
      String rootMsg = CauseChainExtractor.rootMessage(e);
      String msg =
          "XAdES-BES imzalama başarısız: "
              + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
              + (rootMsg != null && !rootMsg.equals(e.getMessage()) ? " | root: " + rootMsg : "");
      throw (SignatureOperationException)
          new SignatureOperationException(code, msg, e).withDiagnostics(diag);
    }
  }

  /**
   * {@link #doXadesBesSignNative} sarmalayıcısı: native akışı denerken native akış da patarsa, hem
   * orijinal CKA_ID hatasını hem native başarısızlığı tek bir {@code SignatureOperationException}
   * altında raporlar; bu sayede destek logları kök neden zincirini görür.
   */
  private byte[] doXadesBesSignNativeWithDiag(
      byte[] xmlBytes,
      Path libraryPath,
      String certIdentifier,
      String pin,
      SignatureDiagnostics diag,
      Throwable xades4jFailure) {
    // Spesifik SunPKCS11 patolojisini (CKA_ID collision / CKR_USER_NOT_LOGGED_IN) diag'a yansıt;
    // native yolun nötr uyarısı yalnız "IAIK üzerinden imzalandı" der, neden bağlamı buradan gelir.
    mergeWarning(
        diag,
        "SunPKCS11 P11KeyStore patolojisi tespit edildi ("
            + describePathology(xades4jFailure)
            + "); IAIK PKCS#11 wrapper fallback'ine düşüldü.");
    try {
      return doXadesBesSignNative(xmlBytes, libraryPath, certIdentifier, pin, diag);
    } catch (SignerException nativeKnown) {
      if (nativeKnown.getDiagnostics() == null) {
        nativeKnown.withDiagnostics(diag);
      }
      throw nativeKnown;
    } catch (Exception nativeFail) {
      // Native path da çuvalladı — orijinal xades4j hatasını cause olarak suppress et.
      String code = classifySignatureFailure(nativeFail);
      String rootMsg = CauseChainExtractor.rootMessage(nativeFail);
      String msg =
          "XAdES-BES imzalama başarısız (native fallback de patladı): "
              + (nativeFail.getMessage() != null
                  ? nativeFail.getMessage()
                  : nativeFail.getClass().getSimpleName())
              + (rootMsg != null && !rootMsg.equals(nativeFail.getMessage())
                  ? " | root: " + rootMsg
                  : "");
      SignatureOperationException sigEx = new SignatureOperationException(code, msg, nativeFail);
      if (xades4jFailure != null) {
        sigEx.addSuppressed(xades4jFailure);
      }
      throw (SignatureOperationException) sigEx.withDiagnostics(diag);
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

    SignatureDiagnostics diag = baseDiagnosticsFor(dto, libraryPath);
    byte[] xmlBytes = readBytes(dto);
    try {
      return signHrCounterSignatureViaSunPkcs11(libraryPath, dto, diag);
    } catch (SignerException known) {
      // SunPKCS11 yolu (P11KeyStore engineLoad ya da JSR-105 sign) bilinen bir patolojiyle
      // patladıysa counter-signature'ı IAIK PKCS#11 wrapper üzerinden (SunPKCS11 bypass) yeniden
      // dener: CKA_ID collision / CKR_USER_NOT_LOGGED_IN (requiresIaikFallback) ya da raw-only
      // firmware'in CKR_FUNCTION_NOT_SUPPORTED / "Unsupported parameters" patolojisi
      // (classifySignatureFailure == ALGORITHM_UNSUPPORTED). Aksi halde tanılamayı bağlayıp
      // olduğu gibi yeniden fırlat.
      if (requiresCounterSignatureNativeFallback(known)) {
        logFallbackReason(known, dto);
        return doCounterSignatureNativeWithDiag(
            xmlBytes, libraryPath, dto.getCertificateId(), dto.getPin(), diag, known);
      }
      if (known.getDiagnostics() == null) {
        known.withDiagnostics(diag);
      }
      throw known;
    } catch (RuntimeException e) {
      if (requiresCounterSignatureNativeFallback(e)) {
        logFallbackReason(e, dto);
        return doCounterSignatureNativeWithDiag(
            xmlBytes, libraryPath, dto.getCertificateId(), dto.getPin(), diag, e);
      }
      String code = classifySignatureFailure(e);
      String rootMsg = CauseChainExtractor.rootMessage(e);
      String msg =
          "XAdES CounterSignature başarısız: "
              + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
              + (rootMsg != null && !rootMsg.equals(e.getMessage()) ? " | root: " + rootMsg : "");
      throw (SignatureOperationException)
          new SignatureOperationException(code, msg, e).withDiagnostics(diag);
    }
  }

  /**
   * Counter-signature'ı SunPKCS11 (P11KeyStore + JSR-105) üzerinden üretir: session aç → imzala →
   * session'ı kapat. Session açılışı ya da imza gövdesi başarısız olursa exception olduğu gibi
   * yukarı taşınır; {@link #signHrXmlCounterSignature} hatayı sınıflandırıp gerekirse IAIK native
   * yoluna ({@link #doCounterSignatureNative}) düşer.
   *
   * <p>try-with-resources yerine explicit try/finally: {@code close()} hatası imza body'sini
   * suppress edip yanlış branch'e (fallback değerlendirmesine) düşmesin. close hatası yalnız
   * log'lanır, orijinal exception (varsa) korunur.
   */
  byte[] signHrCounterSignatureViaSunPkcs11(
      Path libraryPath, SignDocumentDto dto, SignatureDiagnostics diag) {
    Pkcs11Session session = Pkcs11Session.open(libraryPath, dto.getPin());
    RuntimeException bodyFailure = null;
    try {
      return signHrWithSession(session, dto, diag);
    } catch (RuntimeException re) {
      bodyFailure = re;
      if (re instanceof SignerException && ((SignerException) re).getDiagnostics() == null) {
        ((SignerException) re).withDiagnostics(diag);
      }
      throw re;
    } finally {
      try {
        session.close();
      } catch (RuntimeException closeFail) {
        if (bodyFailure != null) {
          bodyFailure.addSuppressed(closeFail);
        } else {
          log.warn(
              "Pkcs11Session.close() başarısız oldu (imza body başarılıydı): {}",
              closeFail.toString());
        }
      }
    }
  }

  /**
   * Counter-signature SunPKCS11 yolu başarısız olduğunda IAIK native yoluna düşülmeli mi? BES
   * akışının iki tetikleyicisini ({@link IaikPkcs11Signer#requiresIaikFallback}: CKA_ID collision /
   * CKR_USER_NOT_LOGGED_IN) raw-only firmware'in algoritma patolojisiyle birleştirir ({@link
   * #classifySignatureFailure} == {@code ALGORITHM_UNSUPPORTED} → CKR_FUNCTION_NOT_SUPPORTED,
   * "Unsupported parameters", CKR_MECHANISM_INVALID, ...). Native yol raw {@code CKM_RSA_PKCS} /
   * {@code CKM_ECDSA} + yazılım digest kullandığı için bu patolojilerin tamamını by-pass eder.
   */
  private static boolean requiresCounterSignatureNativeFallback(Throwable t) {
    return IaikPkcs11Signer.requiresIaikFallback(t)
        || SignatureOperationException.CODE_ALGORITHM_UNSUPPORTED.equals(
            classifySignatureFailure(t));
  }

  /**
   * {@link #doCounterSignatureNative} sarmalayıcısı: native akış da patlarsa hem SunPKCS11
   * başarısızlığını hem native başarısızlığını tek bir {@code SignatureOperationException} altında
   * raporlar (destek logları kök neden zincirini görsün). {@link #doXadesBesSignNativeWithDiag} ile
   * aynı kalıp.
   */
  private byte[] doCounterSignatureNativeWithDiag(
      byte[] xmlBytes,
      Path libraryPath,
      String certIdentifier,
      String pin,
      SignatureDiagnostics diag,
      Throwable sunPkcs11Failure) {
    mergeWarning(
        diag,
        "SunPKCS11 counter-signature yolu başarısız ("
            + CauseChainExtractor.rootMessage(sunPkcs11Failure)
            + "); IAIK PKCS#11 wrapper fallback'ine düşüldü.");
    try {
      return doCounterSignatureNative(xmlBytes, libraryPath, certIdentifier, pin, diag);
    } catch (SignerException nativeKnown) {
      if (nativeKnown.getDiagnostics() == null) {
        nativeKnown.withDiagnostics(diag);
      }
      throw nativeKnown;
    } catch (Exception nativeFail) {
      String code = classifySignatureFailure(nativeFail);
      String rootMsg = CauseChainExtractor.rootMessage(nativeFail);
      String msg =
          "XAdES CounterSignature başarısız (native fallback de patladı): "
              + (nativeFail.getMessage() != null
                  ? nativeFail.getMessage()
                  : nativeFail.getClass().getSimpleName())
              + (rootMsg != null && !rootMsg.equals(nativeFail.getMessage())
                  ? " | root: " + rootMsg
                  : "");
      SignatureOperationException sigEx = new SignatureOperationException(code, msg, nativeFail);
      if (sunPkcs11Failure != null) {
        sigEx.addSuppressed(sunPkcs11Failure);
      }
      throw (SignatureOperationException) sigEx.withDiagnostics(diag);
    }
  }

  /* ================================================================== */
  /* Test-friendly mid-level entries                                     */
  /* ================================================================== */

  /**
   * Counter-signature için test/runtime ortak implementasyonu. Çağıran kişi {@link Pkcs11Session}'ı
   * kendisi yönetir ({@link Pkcs11Session#wrapForTest} ile software keystore de olabilir). Tanılama
   * bağlamı (terminal, ATR, cardType, lib) parametre olarak verilir; hata durumunda exception bu
   * bağlamı taşır.
   */
  public byte[] signHrWithSession(
      Pkcs11Session session, SignDocumentDto dto, SignatureDiagnostics baseDiag) {
    SignatureDiagnostics diag = baseDiag != null ? baseDiag : new SignatureDiagnostics();
    if (dto != null && diag.getTerminalName() == null) {
      diag.setTerminalName(dto.getTerminalName());
    }
    byte[] xmlBytes = readBytes(dto);
    String alias;
    try {
      alias = session.resolveAlias(dto.getCertificateId());
    } catch (Exception e) {
      throw (SignatureOperationException)
          new SignatureOperationException(
                  "Counter-signature için sertifika çözülemedi: " + e.getMessage(), e)
              .withDiagnostics(diag);
    }
    PrivateKey privateKey = session.getPrivateKey(alias);
    Certificate[] chain = chainBuilder.build(session.getCertificateChain(alias));

    try {
      X509Certificate signingCert = (X509Certificate) chain[0];
      diag.setKeyAlgorithm(signingCert.getPublicKey().getAlgorithm());
      return doCounterSignature(xmlBytes, privateKey, chain);
    } catch (Exception e) {
      String code = classifySignatureFailure(e);
      String rootMsg = CauseChainExtractor.rootMessage(e);
      String msg =
          "XAdES CounterSignature başarısız: "
              + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
              + (rootMsg != null && !rootMsg.equals(e.getMessage()) ? " | root: " + rootMsg : "");
      throw (SignatureOperationException)
          new SignatureOperationException(code, msg, e).withDiagnostics(diag);
    }
  }

  /* ================================================================== */
  /* XAdES-BES (xades4j) implementation                                  */
  /* ================================================================== */

  byte[] doXadesBesSign(
      byte[] xmlBytes,
      Path libraryPath,
      String certIdentifier,
      String pin,
      SignatureDiagnostics diag)
      throws Exception {
    final String pinLocal = pin;
    final String providerName = "merselXadesSigner-" + UUID.randomUUID().toString().substring(0, 8);
    // SunPKCS11, "name=X" config satırına "SunPKCS11-" prefix'i ekleyerek JCA'ya kaydeder; aynı
    // ismi tutarlı şekilde xmlsec'in JCEMapper.providerId alanına vereceğiz (aşağıda).
    final String jcaProviderName = "SunPKCS11-" + providerName;

    // xades4j'in PKCS11KeyStoreKeyingDataProvider'ı kendi SunPKCS11'ini ayağa kaldırıp keystore
    // load eder. Kart EC objesi içeriyorsa SunEC'nin "Only named ECParameters supported"
    // patolojisine düşer (cause: IOException → KeyStoreException → UnexpectedJCAException).
    // BC'yi pozisyon 1'e kayıt edip SunEC'yi kaldırınca EC parse'ı BC'ye düşer ve sorun çözülür.
    BouncyCastleSetup.ensureRegistered();

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

    // Sertifika public key tipini önceden öğrenmek için chain'i eagerly çekiyoruz. Bu çağrı:
    //   1. xades4j'in lazy SunPKCS11 init'ini tetikler (sign çağrısından önce JCA'ya kayıt olur)
    //   2. Cert public key algoritmasını (RSA / EC) görmemizi sağlar — resolver'a doğru keyHint
    //      verirsek RSA mekanizmaları yerine ECDSA mekanizmalarını seçer. Sahada AKIS / SafeSign
    //      kartlarında hem RSA hem EC sertifika dağılımı mevcut; default "RSA" varsayımı EC
    //      kartlarında SIGNATURE_ALGORITHM_UNSUPPORTED'a yol açıyordu.
    java.util.List<X509Certificate> chain = keyingProvider.getSigningCertificateChain();
    X509Certificate signingCert = chain.get(0);
    String keyHint = resolveKeyHint(signingCert);

    // SunPKCS11 explicit AuthProvider.login — KeyStore.load() implicit login'inin yetmediği
    // patolojinin çaresi.
    //
    // Sorun: xades4j PKCS11KeyStoreKeyingDataProvider yalnız KeyStore.load(null, pin) ile PIN
    // gönderir. SunPKCS11'de bu çağrı KeyStore objesinin açtığı P11Session'da C_Login yapar.
    // Sonradan Signature.initSign() çağrıldığında P11SessionManager yeni bir session açar; bu
    // session app-level login state'i devralmazsa (sürücüye göre değişir — AKIS, SafeSign,
    // bazı Kamu SM kartları bu davranışı gösterir) C_SignInit → CKR_USER_NOT_LOGGED_IN.
    //
    // Çözüm: AuthProvider.login() çağrısı SunPKCS11'in P11SessionManager'ına "tüm gelecek
    // session'lar authenticated kabul edilecek" sinyali verir. login state Provider objesi
    // Security'den çıkana kadar (close/removeProvider) korunur. Bu çağrı KeyStore.load'un
    // yaptığından farklı bir scope'ta çalışır.
    //
    // Idempotent: SunPKCS11 zaten login ise CKR_USER_ALREADY_LOGGED_IN'i P11SessionManager
    // içeride yutar, no-op olur. Provider AuthProvider'ı implement etmiyorsa (test ortamında
    // mock provider gibi) sessizce atla.
    Provider sunPkcs11 = Security.getProvider(jcaProviderName);
    if (sunPkcs11 instanceof AuthProvider) {
      try {
        io.mersel.dss.agent.api.services.keystore.Pkcs11Session.loginExplicit(
            (AuthProvider) sunPkcs11, pinLocal == null ? new char[0] : pinLocal.toCharArray());
        log.debug(
            "SunPKCS11 AuthProvider.login() explicit çağrıldı (provider={}); app-level login"
                + " state Signature.initSign() öncesi kuruldu.",
            jcaProviderName);
      } catch (LoginException le) {
        // KeyStore.load implicit login yetmediği halde explicit login de fail ettiyse PIN
        // bilgisi gerçekten yanlış / kart kilitli / sürücü hata veriyor demektir. Pkcs11Errors
        // sınıflandırması ile uygun yapısal exception'a dönüştür.
        throw io.mersel.dss.agent.api.services.keystore.Pkcs11Errors.mapKeyStoreLoadFailure(le);
      }
    } else if (sunPkcs11 != null) {
      log.warn(
          "Provider '{}' AuthProvider'ı implement etmiyor; explicit login atlandı."
              + " CKR_USER_NOT_LOGGED_IN ihtimaline karşı dikkatli ol.",
          jcaProviderName);
    } else {
      // Provider Security'de bulunamadı — xades4j lazy init henüz tetiklenmemiş olabilir.
      // Bu durumda explicit login atlanır; KeyStore.load implicit login'ine güveniriz.
      log.debug(
          "Provider '{}' Security registry'de bulunamadı; explicit login atlandı.",
          jcaProviderName);
    }

    // Algoritma profili: token'ın CKM_* listesine göre seçilir; default xades4j profili
    // bazı eski AKIS firmware'lerde "Unsupported parameters" patolojisine yol açar. Resolver
    // tanılama bağlamına `keyAlgorithm`'i bizim verdiğimiz keyHint'ten yansıtır.
    SignatureProfileResolver.Resolution resolution =
        SignatureProfileResolver.resolve(libraryPath, keyHint, diag);
    if (!resolution.isSuccess()) {
      // Kart imzalama için uygun mekanizmadan yoksun — direkt kullanıcıya söyle.
      throw resolution.getError();
    }

    // Proactive routing: token yalnız raw imzalama mekanizması (CKM_RSA_PKCS / CKM_ECDSA)
    // destekliyorsa (resolver fallbackStrategy=raw-*-soft-digest), xades4j → SunPKCS11
    // SHA256withRSA çağrısı kartta multi-part C_SignUpdate'e düşer ve AKIS / SafeSign tipi eski
    // firmware'lerde CKR_FUNCTION_NOT_SUPPORTED ile patlar ("update() failed"). Bu kartlarda
    // baştan başarısız olacak xades4j turunu hiç denemeden doğrudan IAIK native yoluna (yazılım
    // digest + DigestInfo prefix + tek C_Sign) geçiyoruz. Resolver'ın eklediği "raw-only" uyarısı
    // tanılamada zaten neden bağlamını taşır, bu yüzden ek uyarı koymuyoruz.
    if (requiresNativeRawSign(diag.getFallbackStrategy())) {
      log.info(
          "Token yalnız raw imzalama mekanizması destekliyor (fallbackStrategy={}); SunPKCS11"
              + " multi-part C_SignUpdate patolojisi (CKR_FUNCTION_NOT_SUPPORTED) atlanıp doğrudan"
              + " IAIK native imza yoluna geçiliyor (lib={}, certId={}).",
          diag.getFallbackStrategy(),
          libraryPath,
          certIdentifier);
      return doXadesBesSignNative(xmlBytes, libraryPath, certIdentifier, pin, diag);
    }

    XadesBesSigningProfile profile = new XadesBesSigningProfile(keyingProvider);
    profile.withAlgorithmsProviderEx(resolution.getProvider());
    // Cert public key tipine göre KeyInfo zenginleştirme:
    //   - RSA cert → xades4j → xmlsec `KeyValue.ctor(Document, PublicKey)` → branch
    //     `RSAPublicKey instanceof` → `<ds:KeyValue><ds:RSAKeyValue><ds:Modulus/><ds:Exponent/>`
    //   - EC  cert → branch `ECPublicKey instanceof`  → `<ds:KeyValue><dsig11:ECKeyValue>
    //     <dsig11:NamedCurve URI="urn:oid:..."/><dsig11:PublicKey>...</dsig11:PublicKey>`
    //     (XML-DSig 1.1 namespace `http://www.w3.org/2009/xmldsig11#`)
    //
    // xades4j default'unda `includePublicKey=false` olduğu için KeyInfo'da yalnız
    // `<ds:X509Data><ds:X509Certificate>` vardı; TÜBİTAK XAdES uygulama kılavuzu KeyInfo'da
    // KeyValue blogu da bekler. Server-side kardeş proje
    // (`mersel-dss-server-signer-java/src/main/java/eu/europa/esig/dss/xades/signature/XAdESSignatureBuilder.java`)
    // tamamen DSS'e geçtikten sonra aynı branch'i manuel olarak override etti
    // (`addRSAKeyValue` / `addECKeyValue` metotları); agent xades4j path'inde kalacağı için bu
    // tek satırlık `includePublicKey(true)` switch'i ile aynı çıktıyı `xmlsec`'in zaten
    // hazır olan key-type dispatch'inden ücretsiz alıyoruz.
    //
    // Sertifika modu `SIGNING_CERTIFICATE`: yalnız imzacı sertifika eklenir (zincir değil);
    // TÜBİTAK kılavuzuyla uyumlu. `checkKeyUsage(true)` default; `signKeyInfo(false)` default —
    // KeyInfo imzalamayan e-Fatura standardına uygun.
    BasicSignatureOptions keyInfoOptions =
        new BasicSignatureOptions()
            .includeSigningCertificate(SigningCertificateMode.SIGNING_CERTIFICATE)
            .includePublicKey(true);
    profile.withBasicSignatureOptions(keyInfoOptions);
    XadesSigner signer = profile.newSigner();

    Document document = parseXml(xmlBytes);
    Element root = document.getDocumentElement();

    DataObjectDesc dataObj =
        new DataObjectReference("").withTransform(new EnvelopedSignatureTransform());
    SignedDataObjects dataObjs = new SignedDataObjects().withSignedDataObject(dataObj);

    // PKCS#11 token'ından gelen private key opaque olabilir (CKA_SENSITIVE=true, CKA_MODULUS
    // / CKA_EC_PARAMS extractable değil). Bu durumda SunPKCS11 P11Key$P11PrivateKey base
    // class'ını döner; ne RSAPrivateKey ne ECPrivateKey arayüzlerini implement eder.
    //
    // xmlsec (xades4j içinden) SignatureBaseRSA / SignatureECDSA ctor'unda
    //   Signature.getInstance(jcaAlg, JCEMapper.getProviderId())
    // çağırır. JCEMapper.providerId null ise provider'sız çağrılır → JCA chain BC'yi (pos 1)
    // seçer → BC'nin RSA/ECDSA Signature SPI'sı `instanceof RSAPrivateKey` /
    // `instanceof ECPrivateKey` kontrol eder → InvalidKeyException("Supplied key (X) is not
    // a RSAPrivateKey instance"). Opaque P11Key bu kontrolü geçemez.
    //
    // Çözüm: JCEMapper.providerId'i SunPKCS11 provider'ımıza çakarak xmlsec'in
    // Signature.getInstance(jcaAlg, "SunPKCS11-X") çağrısına dönmesini sağlıyoruz. SunPKCS11'in
    // kendi RSA/ECDSA Signature implementasyonları P11Key'i doğrudan kabul edip C_Sign'a
    // yönlendirir (sensitive private key material'ı asla kartı terk etmeden).
    //
    // Global static state; multi-thread concurrent imzalamada race olabilir. Agent tek-kullanıcı
    // desktop senaryosunda eş zamanlı imza akışı pratik değil; yine de class-level monitor
    // kilidiyle korunuyor (try/finally ile eski değeri geri yüklüyoruz).
    String previousProviderId;
    synchronized (XadesService.class) {
      previousProviderId = JCEMapper.getProviderId();
      JCEMapper.setProviderId(jcaProviderName);
    }
    try {
      signer.sign(dataObjs, root);
    } finally {
      synchronized (XadesService.class) {
        JCEMapper.setProviderId(previousProviderId);
      }
    }
    // Apache Santuario Base64 wrapping `\r\n` üretir; DOM Transformer `\r`'yi `&#13;` entity'sine
    // çevirir → çıktı XAdES'inde görsel kirlilik. Tüm imza subtree'lerinde standart 76-char LF
    // wrap'ine normalize ediyoruz; canonicalization girişi olmayan iki text node (X509Certificate
    // + SignatureValue) için Base64 decoder zaten whitespace'i yutar, imza geçerliliği etkilenmez.
    rewrapBase64InSignatureSubtree(document.getDocumentElement());
    return serialise(document);
  }

  /* ================================================================== */
  /* XAdES-BES native PKCS#11 sign path (CKA_ID collision fallback)      */
  /* ================================================================== */

  /**
   * SunPKCS11 P11KeyStore'u atlayarak doğrudan PKCS#11 spec çağrılarıyla XAdES-BES enveloped imza
   * üretir. IAIK PKCS#11 Wrapper kod tabanından türetilen {@code org.xipki:ipkcs11wrapper} (server
   * projesinde HSM akışında kullanılan aynı bağımlılık) JNI bridge'i üzerinden token'a iner.
   *
   * <p>Bu yol yalnız <b>fallback</b> olarak çağrılır: xades4j path {@code KeyStoreException:
   * invalid KeyStore state: found N private keys sharing CKA_ID} ile patladığında devreye girer.
   *
   * <p>Sahada NES Bulut / Kamu SM dual-key (SIGN0 imzalama + SIGN1 anahtar uzlaşımı) setup'ında
   * sürücü her iki anahtarı aynı CKA_ID ile yazar; OpenJDK 1.8 {@code P11KeyStore.mapPrivateKeys()}
   * bu duruma izin vermez ve hiçbir cert seçilemeden hata fırlatır. IAIK wrapper SunPKCS11 JCA
   * soyutlama katmanını HİÇ kullanmaz; cert'i {@link IaikPkcs11Signer#findSigningKey} ile CKA_LABEL
   * / X.509 serial / SHA-1 thumbprint match'i üzerinden bulup, aynı CKA_ID'deki birden çok private
   * key'i {@code CKA_SIGN=TRUE} + {@code CKA_KEY_TYPE} ile ayrıştırır.
   *
   * <p>İmza oluşumu:
   *
   * <ol>
   *   <li>Apache Santuario {@code XMLSignature} ile manuel DOM iskeleti kurulur (Reference =
   *       enveloped + signedProps; KeyInfo = X509Data + KeyValue; Object = QualifyingProperties).
   *   <li>{@code SignedInfo.generateDigestValues()} her {@code Reference} için canonical-octet →
   *       digest hesaplar (PrivateKey gerektirmez).
   *   <li>{@code SignedInfo.getCanonicalizedOctetStream()} ile SignedInfo'nun canonical bytes'ı
   *       yazılım tarafında alınır, SHA-256 (RSA) / SHA-384 (EC) hash edilir.
   *   <li>RSA için CKM_RSA_PKCS bekleyen <em>DigestInfo (DER)</em> prefix'i öne eklenir; EC için
   *       ham hash byte'ları aynen verilir.
   *   <li>{@link IaikPkcs11Signer#sign} → ham PKCS#1 v1.5 (RSA) veya R||S concat (ECDSA) byte'ları
   *       Base64'lenip {@code <ds:SignatureValue>} text content'ine yerleştirilir.
   * </ol>
   *
   * <p>Tanılama bağlamı: {@code fallbackStrategy=native-pkcs11-dual-key-bypass}, {@code
   * resolvedPkcs11Mechanism=CKM_RSA_PKCS|CKM_ECDSA}, uyarı satırı eklenir.
   */
  byte[] doXadesBesSignNative(
      byte[] xmlBytes,
      Path libraryPath,
      String certIdentifier,
      String pin,
      SignatureDiagnostics diag)
      throws Exception {
    BouncyCastleSetup.ensureRegistered();
    Init.init();

    try (IaikPkcs11Signer signer = IaikPkcs11Signer.open(libraryPath, pin)) {
      IaikPkcs11Signer.NativeSigningKey nativeKey = signer.findSigningKey(certIdentifier);
      X509Certificate signingCert = nativeKey.getCertificate();
      boolean ec = nativeKey.isEc() || isEcdsa(signingCert);

      String c14nUrl = Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS;
      String digestUrl = ec ? DIGEST_SHA384 : DigestMethod.SHA256;
      String sigUrl = ec ? SIG_ECDSA_SHA384 : SIG_RSA_SHA256;
      String digestJca = ec ? "SHA-384" : "SHA-256";
      long pkcs11Mechanism = ec ? IaikPkcs11Signer.CKM_ECDSA : IaikPkcs11Signer.CKM_RSA_PKCS;
      String mechanismLabel = ec ? "CKM_ECDSA" : "CKM_RSA_PKCS";

      diag.setKeyAlgorithm(ec ? "EC" : "RSA");
      try {
        diag.setKeySize(estimateKeySizeBits(signingCert));
      } catch (RuntimeException ignore) {
        /* tanılama best-effort */
      }
      diag.setAttemptedSignatureAlgorithm(sigUrl);
      diag.setResolvedJcaSignature(ec ? "RAW-ECDSA-NATIVE" : "RAW-RSA-NATIVE");
      diag.setResolvedPkcs11Mechanism(mechanismLabel);
      diag.setFallbackStrategy("iaik-pkcs11-sunpkcs11-bypass");
      // Nötr not: bu yola hem dual-key/login patolojisi fallback'inden hem de raw-only proactive
      // routing'den gelinir. Spesifik neden (CKA_ID collision / CKR_USER_NOT_LOGGED_IN ya da
      // raw-only firmware) çağıran katman tarafından / resolver tarafından diag'a eklenir.
      mergeWarning(
          diag,
          "İmza, SunPKCS11 atlanıp IAIK PKCS#11 wrapper üzerinden (yazılım digest + tek C_Sign)"
              + " atıldı.");

      Document document = parseXml(xmlBytes);
      Element root = document.getDocumentElement();

      String sigId =
          "MerselSig-" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 12);
      String sigValueId = "Signature-Value-Id-" + UUID.randomUUID();
      String objectId = "Object-Id-" + UUID.randomUUID();
      String signedPropsId = "Signed-Properties-Id-" + UUID.randomUUID();
      String signedPropsRefId = "Reference-Id-" + UUID.randomUUID();
      String envelopedRefId = "Reference-Id-" + UUID.randomUUID();

      org.apache.xml.security.signature.XMLSignature santSig =
          new org.apache.xml.security.signature.XMLSignature(document, "", sigUrl, c14nUrl);
      santSig.setId(sigId);
      root.appendChild(santSig.getElement());

      // Reference 1: URI="" with EnvelopedSignatureTransform
      Transforms tfsEnveloped = new Transforms(document);
      tfsEnveloped.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
      santSig.addDocument("", tfsEnveloped, digestUrl, envelopedRefId, null);

      // Reference 2: URI="#signedPropsId" with c14n transform; Type = SignedProperties
      Transforms tfsSp = new Transforms(document);
      tfsSp.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
      santSig.addDocument(
          "#" + signedPropsId, tfsSp, digestUrl, signedPropsRefId, XADES_TYPE_SIGNED_PROPERTIES);

      // KeyInfo: <ds:X509Data><ds:X509Certificate> +
      // <ds:KeyValue><ds:RSAKeyValue|dsig11:ECKeyValue>
      // Santuario `KeyInfo.add(PublicKey)` instanceof dispatch ile RSAKeyValue / ECKeyValue üretir.
      santSig.addKeyInfo(signingCert);
      santSig.addKeyInfo(signingCert.getPublicKey());

      // Object/QualifyingProperties/SignedProperties (counter-sig path'iyle aynı şablon)
      ObjectContainer obj = new ObjectContainer(document);
      obj.setId(objectId);
      Element qualifyingProps = buildQualifyingProperties(document, sigId, signedPropsId);
      Element signedPropsEl = (Element) qualifyingProps.getFirstChild();
      populateSignedProperties(document, signedPropsEl, signingCert, digestUrl);
      obj.getElement().appendChild(qualifyingProps);
      santSig.appendObject(obj);

      // Reference DigestValues hesapla (PrivateKey'siz)
      santSig.getSignedInfo().generateDigestValues();

      // SignedInfo canonical bytes → software digest
      byte[] siCanonical = santSig.getSignedInfo().getCanonicalizedOctetStream();
      MessageDigest md = MessageDigest.getInstance(digestJca);
      byte[] tbsHash = md.digest(siCanonical);

      // Native PKCS#11 imza:
      //  - RSA: CKM_RSA_PKCS, DigestInfo (RFC 8017 §9.2 EMSA-PKCS1-v1_5 EM Step 2)
      //  - EC : CKM_ECDSA, ham hash (RFC 4051 §2.2.4, R||S fixed-width)
      byte[] dataToSign = ec ? tbsHash : rsaDigestInfo(tbsHash, digestJca);
      byte[] rawSignature = signer.sign(nativeKey, pkcs11Mechanism, dataToSign);
      if (rawSignature == null || rawSignature.length == 0) {
        throw new SignatureOperationException(
            "Native PKCS#11 C_Sign boş imza döndürdü (mech=" + mechanismLabel + ").");
      }

      // <ds:SignatureValue Id="..."> içine inject. Santuario varsayılan olarak SignatureValue
      // elementini SignedInfo'dan sonra append eder; Id'ini de güncelleyelim.
      NodeList svList = santSig.getElement().getElementsByTagNameNS(DS_NS, "SignatureValue");
      if (svList.getLength() == 0) {
        throw new SignatureOperationException(
            "Santuario XMLSignature iskeletinde <ds:SignatureValue> bulunamadı.");
      }
      Element svEl = (Element) svList.item(0);
      svEl.setAttribute("Id", sigValueId);
      svEl.setTextContent(Base64.getEncoder().encodeToString(rawSignature));

      // SignatureValue burada JDK Base64 (line break'siz) ile yazılıyor; ancak X509Certificate
      // text node'u Santuario `addKeyInfo` üzerinden geliyor ve MIME 76-char CRLF taşıyor.
      // Tüm imza subtree'sini standart LF wrap'ine normalize et.
      rewrapBase64InSignatureSubtree(document.getDocumentElement());
      return serialise(document);
    }
  }

  /**
   * EMSA-PKCS1-v1_5 (RFC 8017 §9.2) için DigestInfo (DER) prefix'ini hash byte'larının başına
   * ekler. CKM_RSA_PKCS ham PKCS#1 v1.5 padding yapar ama DigestInfo encoding'ini bizden bekler.
   *
   * <p>Hardcoded prefix'ler PKCS#1 standardından alındı; SHA-256 / SHA-384 / SHA-512 destekli.
   */
  static byte[] rsaDigestInfo(byte[] hash, String digestJca) {
    byte[] prefix;
    String alg = digestJca == null ? "" : digestJca.toUpperCase(Locale.ROOT);
    switch (alg) {
      case "SHA-256":
        prefix =
            new byte[] {
              0x30,
              0x31,
              0x30,
              0x0d,
              0x06,
              0x09,
              0x60,
              (byte) 0x86,
              0x48,
              0x01,
              0x65,
              0x03,
              0x04,
              0x02,
              0x01,
              0x05,
              0x00,
              0x04,
              0x20
            };
        break;
      case "SHA-384":
        prefix =
            new byte[] {
              0x30,
              0x41,
              0x30,
              0x0d,
              0x06,
              0x09,
              0x60,
              (byte) 0x86,
              0x48,
              0x01,
              0x65,
              0x03,
              0x04,
              0x02,
              0x02,
              0x05,
              0x00,
              0x04,
              0x30
            };
        break;
      case "SHA-512":
        prefix =
            new byte[] {
              0x30,
              0x51,
              0x30,
              0x0d,
              0x06,
              0x09,
              0x60,
              (byte) 0x86,
              0x48,
              0x01,
              0x65,
              0x03,
              0x04,
              0x02,
              0x03,
              0x05,
              0x00,
              0x04,
              0x40
            };
        break;
      default:
        throw new IllegalArgumentException(
            "Desteklenmeyen RSA DigestInfo algoritması: " + digestJca);
    }
    byte[] out = new byte[prefix.length + hash.length];
    System.arraycopy(prefix, 0, out, 0, prefix.length);
    System.arraycopy(hash, 0, out, prefix.length, hash.length);
    return out;
  }

  /** RSA cert'i için modül bit'leri; EC cert için field bit'leri. Tanılama best-effort. */
  static Integer estimateKeySizeBits(X509Certificate cert) {
    java.security.PublicKey pk = cert.getPublicKey();
    if (pk instanceof java.security.interfaces.RSAPublicKey) {
      return ((java.security.interfaces.RSAPublicKey) pk).getModulus().bitLength();
    }
    if (pk instanceof java.security.interfaces.ECPublicKey) {
      java.security.interfaces.ECPublicKey ec = (java.security.interfaces.ECPublicKey) pk;
      return ec.getParams().getCurve().getField().getFieldSize();
    }
    return null;
  }

  /**
   * Fallback log mesajını cause zincirinde algılanan pattern'a göre kişiselleştirir. CKA_ID
   * collision ile CKR_USER_NOT_LOGGED_IN farklı kart patolojileri; destek loglarında hangi pattern
   * tetikledi görmek tanılama için kritik.
   */
  private void logFallbackReason(Throwable cause, SignDocumentDto dto) {
    String reason = describePathology(cause);
    log.warn(
        "xades4j path SunPKCS11 patolojisi ({}) yüzünden başarısız; IAIK PKCS#11 imza yoluna"
            + " düşülüyor (terminal={}, certId={}).",
        reason,
        dto.getTerminalName(),
        dto.getCertificateId());
  }

  /** Cause zincirinde bilinen SunPKCS11 patolojilerinden hangisinin tetiklendiğini açıklar. */
  private static String describePathology(Throwable t) {
    Throwable match =
        io.mersel.dss.agent.api.exceptions.CauseChainExtractor.walk(
            t,
            new java.util.function.Predicate<Throwable>() {
              @Override
              public boolean test(Throwable cur) {
                String msg = cur.getMessage();
                if (msg == null) {
                  return false;
                }
                String lower = msg.toLowerCase(Locale.ROOT);
                return lower.contains("ckr_user_not_logged_in")
                    || lower.contains("ckr_function_not_supported")
                    || lower.contains("update() failed")
                    || (lower.contains("invalid keystore state") && lower.contains("cka_id"))
                    || (lower.contains("private keys sharing") && lower.contains("cka_id"));
              }
            });
    if (match == null) {
      return "bilinmeyen pattern";
    }
    String lower = match.getMessage().toLowerCase(Locale.ROOT);
    if (lower.contains("ckr_user_not_logged_in")) {
      return "CKR_USER_NOT_LOGGED_IN (session-scoped login state, AKİS / SafeSign tipik)";
    }
    if (lower.contains("ckr_function_not_supported") || lower.contains("update() failed")) {
      return "CKR_FUNCTION_NOT_SUPPORTED (raw-only firmware, multi-part C_SignUpdate desteklenmiyor)";
    }
    if (lower.contains("invalid keystore state") && lower.contains("cka_id")) {
      return "CKA_ID collision (NES Bulut dual-key SIGN0+SIGN1)";
    }
    if (lower.contains("private keys sharing") && lower.contains("cka_id")) {
      return "CKA_ID collision (private keys sharing CKA_ID)";
    }
    return "bilinmeyen pattern";
  }

  /** Mevcut uyarı listesine ekleme yapar (immutable list olduğu için kopya alıp set eder). */
  private static void mergeWarning(SignatureDiagnostics diag, String warning) {
    if (diag == null || warning == null) return;
    java.util.List<String> next = new java.util.ArrayList<String>();
    if (diag.getWarnings() != null) {
      next.addAll(diag.getWarnings());
    }
    if (!next.contains(warning)) {
      next.add(warning);
    }
    diag.setWarnings(next);
  }

  /**
   * Sertifikanın public key algoritmasına göre {@link SignatureProfileResolver}'a verilecek
   * keyHint'i seçer: {@code "EC"} veya {@code "RSA"}. Tek otörite kaynağıdır; {@link #isEcdsa} ile
   * aynı mantık. Frontend "kart EC mı RSA mı?" sorusunu trace JSON'undan görür.
   */
  static String resolveKeyHint(X509Certificate cert) {
    return isEcdsa(cert) ? "EC" : "RSA";
  }

  /**
   * Resolver'ın seçtiği {@code fallbackStrategy} token'ın yalnız <em>raw</em> imzalama mekanizması
   * ({@code CKM_RSA_PKCS} ya da {@code CKM_ECDSA}) desteklediğini — yani digest'in yazılım tarafında
   * hesaplanıp karta padding'siz / DigestInfo'lu verilmesi gerektiğini — gösteriyorsa {@code true}.
   *
   * <p>Bu kartlarda xades4j → SunPKCS11 yolu {@code SHA256withRSA} çağrısını multi-part {@code
   * C_SignUpdate}'e çevirir; AKIS / SafeSign tipi firmware'ler {@code C_SignUpdate}'i implemente
   * etmediği için {@code CKR_FUNCTION_NOT_SUPPORTED} ("update() failed") fırlatır. {@link
   * #doXadesBesSign} bu durumda doomed xades4j turunu atlayıp doğrudan {@link
   * #doXadesBesSignNative} (yazılım digest + tek {@code C_Sign}) yoluna yönlendirir.
   *
   * <p>Combined mekanizmalar ({@code CKM_SHA256_RSA_PKCS}, {@code CKM_ECDSA_SHA384}, {@code
   * CKM_SHA1_RSA_PKCS}, ...) kartta digest'i kendisi hesapladığı için SunPKCS11 yolu sorunsuz
   * çalışır ve bu metot {@code false} döner.
   */
  static boolean requiresNativeRawSign(String fallbackStrategy) {
    return "raw-rsa-soft-digest".equals(fallbackStrategy)
        || "raw-ecdsa-soft-digest".equals(fallbackStrategy);
  }

  /** {@link #resolveKeyHint} için boolean adapter. */
  static boolean isEcdsa(X509Certificate cert) {
    if (cert == null || cert.getPublicKey() == null) return false;
    String algo = cert.getPublicKey().getAlgorithm();
    if (algo == null) return false;
    String upper = algo.toUpperCase(Locale.ROOT);
    return upper.contains("EC") && !upper.contains("RSA");
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

    // 3) KeyInfo — imzacı sertifikası + KeyValue (RSA/EC public key) ile zenginleştirilir.
    //
    // Cert public key tipine göre KeyInfo yapısı:
    //   - RSA: <ds:KeyValue><ds:RSAKeyValue><ds:Modulus/><ds:Exponent/>
    //   - EC : <ds:KeyValue><dsig11:ECKeyValue><dsig11:NamedCurve URI="urn:oid:..."/>
    //          <dsig11:PublicKey>...</dsig11:PublicKey></dsig11:ECKeyValue></ds:KeyValue>
    //
    // JSR 105 `KeyInfoFactory.newKeyValue(PublicKey)` alt katmanda xmlsec
    // `KeyValue(Document, PublicKey)` ctor'una yönlenir; ctor `instanceof RSAPublicKey`
    // (→ <ds:RSAKeyValue>) ve `instanceof ECPublicKey` (→ <dsig11:ECKeyValue>) branch'lerini
    // otomatik kurar (`org.apache.xml.security.keys.content.KeyValue` line 92-115). TÜBİTAK
    // XAdES uygulama kılavuzu KeyInfo'da KeyValue blogu da bekler; ana xades4j akışıyla
    // (`doXadesBesSign` → `BasicSignatureOptions.includePublicKey(true)`) parite sağlanır.
    KeyInfoFactory kif = fac.getKeyInfoFactory();
    X509Data x509Data = kif.newX509Data(Collections.<Object>singletonList(signingCert));
    javax.xml.crypto.dsig.keyinfo.KeyValue keyValue = kif.newKeyValue(signingCert.getPublicKey());
    KeyInfo ki = kif.newKeyInfo(Arrays.asList(x509Data, keyValue));

    // 4) XMLSignature — Id ve SignatureValue Id explicit, Object listesi içeride QP taşıyor
    javax.xml.crypto.dsig.XMLSignature xmlSig =
        fac.newXMLSignature(
            si, ki, Collections.singletonList(xmlObject), counterSigId, counterSigValueId);

    DOMSignContext sc = new DOMSignContext(privateKey, counterSig);
    sc.putNamespacePrefix(DS_NS, "ds");
    sc.putNamespacePrefix(XADES_NS, "xades");

    xmlSig.sign(sc);

    // Sadece counter-signature subtree'sinde rewrap yapıyoruz; parent imzanın c14n parity'sini
    // korumak için onun text node'larına dokunmuyoruz (zaten counter-sign'dan ÖNCE parent imza
    // doğrulanmış / digest'lenmiş; mutasyon riski almıyoruz).
    rewrapBase64InSignatureSubtree(counterSig);
    return serialise(doc);
  }

  /**
   * {@link #doCounterSignature}'ın IAIK PKCS#11 wrapper karşılığı: SunPKCS11 P11KeyStore'u (ve
   * JSR-105 {@code DOMSignContext} PrivateKey imzasını) tamamen atlayıp doğrudan PKCS#11 spec
   * çağrılarıyla ({@code C_SignInit + C_Sign}) counter-signature üretir.
   *
   * <p>Bu yol yalnız <b>fallback</b> olarak çağrılır: SunPKCS11 yolu CKA_ID collision /
   * CKR_USER_NOT_LOGGED_IN ya da raw-only firmware'in CKR_FUNCTION_NOT_SUPPORTED patolojisiyle
   * patladığında {@link #signHrXmlCounterSignature} buraya yönlendirir.
   *
   * <p>Üretilen XAdES-BES counter-signature {@link #doCounterSignature} ile <b>bire bir aynı
   * yapısaldır</b>: SignedInfo c14n = inclusive-with-comments; iki Reference (kendi
   * SignedProperties — transform yok, Type=SignedProperties; karşı imzalanan SignatureValue —
   * inclusive-with-comments c14n transform, Type=CountersignedSignature); KeyInfo = X509Data +
   * KeyValue; Object/QualifyingProperties/SignedProperties (SigningTime + SigningCertificate). Tek
   * fark imza üretiminin SunPKCS11 yerine {@link IaikPkcs11Signer} üzerinden yazılım digest +
   * DigestInfo prefix (RSA) / ham hash (EC) + tek {@code C_Sign} ile yapılmasıdır.
   */
  byte[] doCounterSignatureNative(
      byte[] xmlBytes,
      Path libraryPath,
      String certIdentifier,
      String pin,
      SignatureDiagnostics diag)
      throws Exception {
    BouncyCastleSetup.ensureRegistered();
    Init.init();

    try (IaikPkcs11Signer signer = IaikPkcs11Signer.open(libraryPath, pin)) {
      IaikPkcs11Signer.NativeSigningKey nativeKey = signer.findSigningKey(certIdentifier);
      X509Certificate signingCert = nativeKey.getCertificate();
      boolean ec = nativeKey.isEc() || isEcdsa(signingCert);

      String digestUrl = ec ? DIGEST_SHA384 : DigestMethod.SHA256;
      String sigUrl = ec ? SIG_ECDSA_SHA384 : SIG_RSA_SHA256;
      String digestJca = ec ? "SHA-384" : "SHA-256";
      long pkcs11Mechanism = ec ? IaikPkcs11Signer.CKM_ECDSA : IaikPkcs11Signer.CKM_RSA_PKCS;
      String mechanismLabel = ec ? "CKM_ECDSA" : "CKM_RSA_PKCS";

      diag.setKeyAlgorithm(ec ? "EC" : "RSA");
      try {
        diag.setKeySize(estimateKeySizeBits(signingCert));
      } catch (RuntimeException ignore) {
        /* tanılama best-effort */
      }
      diag.setAttemptedSignatureAlgorithm(sigUrl);
      diag.setResolvedJcaSignature(ec ? "RAW-ECDSA-NATIVE" : "RAW-RSA-NATIVE");
      diag.setResolvedPkcs11Mechanism(mechanismLabel);
      diag.setFallbackStrategy("iaik-pkcs11-sunpkcs11-bypass");
      mergeWarning(
          diag,
          "İmza, SunPKCS11 atlanıp IAIK PKCS#11 wrapper üzerinden (yazılım digest + tek C_Sign)"
              + " atıldı.");

      Document doc = parseXml(xmlBytes);

      NodeList sigs = doc.getElementsByTagNameNS(DS_NS, "Signature");
      if (sigs.getLength() == 0) {
        throw new IllegalArgumentException(
            "XML'de imzalanacak <ds:Signature> bulunamadı (counter-sig için zorunlu).");
      }
      Element existingSig = (Element) sigs.item(0);

      Element parentSigValueEl = findChildSignatureValue(existingSig);
      if (parentSigValueEl == null) {
        throw new IllegalArgumentException(
            "Mevcut <ds:Signature> içinde <ds:SignatureValue> yok.");
      }
      // Karşı imzalanacak SignatureValue'a XML ID garantisi (URI fragment dereferencing için).
      String parentSigValueId = parentSigValueEl.getAttribute("Id");
      if (parentSigValueId == null || parentSigValueId.isEmpty()) {
        parentSigValueId = "Signature-Value-Id-" + UUID.randomUUID();
        parentSigValueEl.setAttribute("Id", parentSigValueId);
      }
      parentSigValueEl.setIdAttribute("Id", true);

      Element ussp = findOrCreateUnsignedSignatureProperties(existingSig, doc);
      Element counterSig = doc.createElementNS(XADES_NS, "xades:CounterSignature");
      ussp.appendChild(counterSig);

      String counterSigId = "Signature-Id-" + UUID.randomUUID();
      String counterSigValueId = "Signature-Value-Id-" + UUID.randomUUID();
      String signedPropsId = "Signed-Properties-Id-" + UUID.randomUUID();
      String objectId = "Object-Id-" + UUID.randomUUID();
      String signedPropsRefId = "Reference-Id-" + UUID.randomUUID();
      String counterRefId = "Reference-Id-" + UUID.randomUUID();

      // SignedInfo c14n = inclusive WithComments (JSR-105 doCounterSignature ile paritede).
      org.apache.xml.security.signature.XMLSignature santSig =
          new org.apache.xml.security.signature.XMLSignature(
              doc, "", sigUrl, Transforms.TRANSFORM_C14N_WITH_COMMENTS);
      santSig.setId(counterSigId);
      counterSig.appendChild(santSig.getElement());

      // Reference 1: kendi SignedProperties — transform YOK, Type=SignedProperties.
      santSig.addDocument(
          "#" + signedPropsId, null, digestUrl, signedPropsRefId, XADES_TYPE_SIGNED_PROPERTIES);

      // Reference 2: karşı imzalanan SignatureValue — inclusive-with-comments c14n transform,
      // Type=CountersignedSignature.
      Transforms counterTfs = new Transforms(doc);
      counterTfs.addTransform(Transforms.TRANSFORM_C14N_WITH_COMMENTS);
      santSig.addDocument(
          "#" + parentSigValueId,
          counterTfs,
          digestUrl,
          counterRefId,
          XADES_TYPE_COUNTERSIGNED_SIGNATURE);

      // KeyInfo: <ds:X509Data><ds:X509Certificate> + <ds:KeyValue> (instanceof dispatch ile
      // RSAKeyValue / ECKeyValue).
      santSig.addKeyInfo(signingCert);
      santSig.addKeyInfo(signingCert.getPublicKey());

      // Object/QualifyingProperties/SignedProperties (BES native path'iyle aynı şablon).
      ObjectContainer obj = new ObjectContainer(doc);
      obj.setId(objectId);
      Element qualifyingProps = buildQualifyingProperties(doc, counterSigId, signedPropsId);
      Element signedPropsEl = (Element) qualifyingProps.getFirstChild();
      populateSignedProperties(doc, signedPropsEl, signingCert, digestUrl);
      obj.getElement().appendChild(qualifyingProps);
      santSig.appendObject(obj);

      // Reference DigestValue'ları hesapla (PrivateKey'siz).
      santSig.getSignedInfo().generateDigestValues();

      // SignedInfo canonical bytes → software digest → native PKCS#11 imza.
      byte[] siCanonical = santSig.getSignedInfo().getCanonicalizedOctetStream();
      MessageDigest md = MessageDigest.getInstance(digestJca);
      byte[] tbsHash = md.digest(siCanonical);
      byte[] dataToSign = ec ? tbsHash : rsaDigestInfo(tbsHash, digestJca);
      byte[] rawSignature = signer.sign(nativeKey, pkcs11Mechanism, dataToSign);
      if (rawSignature == null || rawSignature.length == 0) {
        throw new SignatureOperationException(
            "Native PKCS#11 C_Sign boş imza döndürdü (mech=" + mechanismLabel + ").");
      }

      NodeList svList = santSig.getElement().getElementsByTagNameNS(DS_NS, "SignatureValue");
      if (svList.getLength() == 0) {
        throw new SignatureOperationException(
            "Santuario XMLSignature iskeletinde <ds:SignatureValue> bulunamadı.");
      }
      Element svEl = (Element) svList.item(0);
      svEl.setAttribute("Id", counterSigValueId);
      svEl.setTextContent(Base64.getEncoder().encodeToString(rawSignature));

      // Sadece counter-signature subtree'sinde rewrap — parent imzanın c14n parity'sini koru.
      rewrapBase64InSignatureSubtree(counterSig);
      return serialise(doc);
    }
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

  /**
   * Sertifikanın DER kodlamasını verilen XAdES digest algoritmasıyla hash'leyip Base64 döner.
   * SHA-256, SHA-384, SHA-512 ve SHA-1 destekli — {@link SignatureProfileResolver} sahada nadiren
   * de olsa SHA-512/SHA-1 seçebiliyor.
   */
  private static String certificateDigestBase64(X509Certificate cert, String digestUrl)
      throws Exception {
    String javaAlg;
    if (digestUrl == null) {
      javaAlg = "SHA-256";
    } else if (digestUrl.endsWith("sha512")) {
      javaAlg = "SHA-512";
    } else if (digestUrl.endsWith("sha384")) {
      javaAlg = "SHA-384";
    } else if (digestUrl.endsWith("sha1")) {
      javaAlg = "SHA-1";
    } else {
      javaAlg = "SHA-256";
    }
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

  /* ================================================================== */
  /* Base64 line-wrap normalisation                                      */
  /* ================================================================== */

  /**
   * Standart XAdES Base64 satır genişliği: 76 karakter (RFC 2045 / MIME default; Apache Santuario,
   * OpenSSL, eu.europa.esig DSS hepsi aynı genişlikte üretir). Sahada üretilen imzaların görsel
   * kimliği ile uyumlu olsun.
   */
  private static final int BASE64_LINE_WIDTH = 76;

  /**
   * Verilen subtree içindeki {@code <ds:X509Certificate>} ve {@code <ds:SignatureValue>}
   * elementlerinin Base64 text içeriğini standart 76 karakter LF wrap'ine normalize eder.
   *
   * <h3>Neden gerekli?</h3>
   *
   * <p>Apache Santuario, {@code <ds:X509Certificate>} ve {@code <ds:SignatureValue>} text
   * node'larını {@code Base64.encodeToString} (Apache Commons MIME mode) ile yazar; çıktı 76
   * karakterde {@code \r\n} ile kırılır. DOM Transformer XML 1.0 spec gereği literal {@code \r}
   * karakterini text içeriğinde {@code &#13;} entity'si olarak escape eder (round-trip korunsun
   * diye), sonuçta agent'ın ürettiği XAdES dosyası standart araçlarla üretilenlerden görsel olarak
   * farklı çıkıyordu (her satır sonu {@code &#13;} taşıyordu).
   *
   * <h3>Neden global property ile değil node-bazlı?</h3>
   *
   * <p>{@code -Dorg.apache.xml.security.ignoreLineBreaks=true} sistem property'si Santuario sınıfı
   * yüklenmeden ÖNCE set edilmek zorunda; agent başlatılırken Spring autoconfigure Santuario'yu
   * zaten yüklemiş oluyor. Bu node-bazlı normalize global yan etki yaratmadan aynı sonucu veriyor.
   *
   * <h3>İmza geçerliliği etkilenir mi?</h3>
   *
   * <p>Hayır. {@code <ds:X509Certificate>} ve {@code <ds:SignatureValue>} text node'ları
   * <b>canonicalization / digest girişi değil</b>; yalnız Base64 decode edilip ham byte'lara
   * çevriliyor. Base64 decoder whitespace'i (space/tab/CR/LF) zaten yok sayar. Aynı yaklaşım server
   * projesinde {@code TestUserCounterSignatureService#rewrapBase64InSubtree} tarafından
   * kullanılıyor (kardeş regresyon testi: {@code TestUserCounterSignatureCleanOutputTest}).
   *
   * <p>Diğer Base64 taşıyan ama digest girişi olabilen elementler ({@code DigestValue}, {@code
   * CertDigest > DigestValue}) bu normalize'in dışında bırakılır; onlar bizim tarafımızdan zaten
   * tek satır olarak yazılıyor (JDK {@code Base64.getEncoder()} default line-break üretmez),
   * dokunmaya gerek yok.
   */
  static void rewrapBase64InSignatureSubtree(Element root) {
    if (root == null) {
      return;
    }
    rewrapBase64ForAll(root, DS_NS, "X509Certificate");
    rewrapBase64ForAll(root, DS_NS, "SignatureValue");
  }

  private static void rewrapBase64ForAll(Element root, String ns, String localName) {
    NodeList list = root.getElementsByTagNameNS(ns, localName);
    for (int i = 0; i < list.getLength(); i++) {
      Element el = (Element) list.item(i);
      String text = el.getTextContent();
      if (text == null || text.isEmpty()) {
        continue;
      }
      // Önce mevcut whitespace'leri (Santuario CRLF, Transformer indentation) sıyır → ham
      // Base64. Sonra standart LF wrap ile yeniden böl.
      String raw = text.replaceAll("[\\r\\n\\t ]+", "");
      if (raw.isEmpty()) {
        continue;
      }
      String wrapped = wrapBase64(raw);
      if (!wrapped.equals(text)) {
        el.setTextContent(wrapped);
      }
    }
  }

  /**
   * Base64 string'ini {@link #BASE64_LINE_WIDTH} karakter genişliğinde LF ile böler. Standalone,
   * kütüphane bağımlılığı yok — JDK MIME encoder'ı line separator olarak {@code \r\n} kullanır
   * (XML'de aynı problem); JDK basic encoder hiç wrap yapmaz (tek-satır görsel kirlilik).
   */
  static String wrapBase64(String base64) {
    int len = base64.length();
    if (len <= BASE64_LINE_WIDTH) {
      return base64;
    }
    StringBuilder sb = new StringBuilder(len + len / BASE64_LINE_WIDTH + 1);
    int offset = 0;
    while (offset < len) {
      int end = Math.min(offset + BASE64_LINE_WIDTH, len);
      sb.append(base64, offset, end);
      if (end < len) {
        sb.append('\n');
      }
      offset = end;
    }
    return sb.toString();
  }

  /**
   * İmzalama akışı başlamadan önce toplanabilecek tanılama bağlamını üretir: terminal adı, ATR,
   * algılanan kart tipi, çözülen lib yolu. Resolver bu yapıyı zenginleştirerek (token info,
   * mekanizma listesi, fallback stratejisi) hata yanıtına ekler.
   */
  SignatureDiagnostics baseDiagnosticsFor(SignDocumentDto dto, Path libraryPath) {
    SignatureDiagnostics diag = new SignatureDiagnostics();
    if (dto != null) {
      diag.setTerminalName(dto.getTerminalName());
    }
    if (libraryPath != null) {
      diag.setPkcs11Library(libraryPath.toString());
    }
    if (readerService != null && dto != null && dto.getTerminalName() != null) {
      try {
        SmartCardInfo info = readerService.findByTerminalName(dto.getTerminalName());
        if (info != null) {
          diag.setAtr(info.getAtrHex());
          if (info.getCardType() != null) {
            diag.setCardType(info.getCardType().getName());
          }
        }
      } catch (RuntimeException scanFail) {
        // Tanılama best-effort; readerService çökerse imzalama yine devam etsin.
        log.debug("Tanılama bağlamı için kart bilgisi alınamadı: {}", scanFail.getMessage());
      }
    }
    return diag;
  }

  /**
   * Cause zincirinde "Unsupported parameters" / "CKR_MECHANISM_INVALID" / "Mechanism not supported"
   * gibi bilinen algoritma uyumsuzluğu pattern'lerini ararsa {@code
   * SIGNATURE_ALGORITHM_UNSUPPORTED} kodunu üretir; aksi halde generic {@code SIGNATURE_FAILED}.
   * Frontend bu kod farkıyla "kart firmware'i güncelleme önerisi" gösterebilir.
   */
  static String classifySignatureFailure(Throwable e) {
    if (e == null) return SignatureOperationException.CODE_FAILED;
    String[] needles = {
      "unsupported parameters",
      "ckr_mechanism_invalid",
      "ckr_key_type_inconsistent",
      "ckr_function_not_supported",
      "mechanism not supported",
      "no such algorithm",
      "unsupportedalgorithm"
    };
    for (String n : needles) {
      if (CauseChainExtractor.findContaining(e, n) != null) {
        return SignatureOperationException.CODE_ALGORITHM_UNSUPPORTED;
      }
    }
    return SignatureOperationException.CODE_FAILED;
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
