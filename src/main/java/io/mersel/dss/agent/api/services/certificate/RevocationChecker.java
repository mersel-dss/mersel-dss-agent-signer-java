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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.cert.ocsp.jcajce.JcaCertificateID;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.models.CertificateStatusResponse;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sertifika revocation kontrolü: önce AIA üzerinden OCSP, başarısızsa CDP üzerinden CRL fetch.
 * TÜBİTAK ESYA bağımlılığı yoktur; saf BouncyCastle + OkHttp.
 *
 * <p>Davranış:
 *
 * <ul>
 *   <li>Sertifika zincirinde issuer cert yoksa → {@link CertificateStatusResponse.Status#UNKNOWN}
 *   <li>Sertifikanın AIA extension'ından OCSP URL alınır; HTTP POST ile sorgulanır.
 *   <li>OCSP cevabı:
 *       <ul>
 *         <li>GOOD → {@code ACTIVE}
 *         <li>REVOKED → {@code REVOKED} (reason + tarih mesaja eklenir)
 *         <li>UNKNOWN veya response hatası → CRL fallback
 *       </ul>
 *   <li>CRL fallback: CDP extension'dan URL'ler alınır, fetch edilir, parse edilir.
 *   <li>Tüm kontroller başarısızsa {@code UNKNOWN}.
 * </ul>
 *
 * <p>Saldırı yüzeyini azaltmak için OCSP/CRL fetch için 5 sn connect / 10 sn read timeout
 * uygulanır; geç dönen yanıt yutulur ve UNKNOWN dönülür.
 */
@Component
public class RevocationChecker {

  private static final Logger log = LoggerFactory.getLogger(RevocationChecker.class);

  private static final MediaType OCSP_MEDIA_TYPE = MediaType.parse("application/ocsp-request");

  private final OkHttpClient http;

  public RevocationChecker() {
    this.http =
        new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .writeTimeout(Duration.ofSeconds(5))
            .callTimeout(15, TimeUnit.SECONDS)
            .build();
  }

  /** Yalnızca test enjeksiyonu için. */
  RevocationChecker(OkHttpClient http) {
    this.http = http;
  }

  /**
   * @param cert durumu sorgulanan sertifika
   * @param chain (cert dahil) tam sertifika zinciri — issuer'dan birini bulmak için kullanılır
   * @return durum + açıklama
   */
  public CertificateStatusResponse check(X509Certificate cert, X509Certificate[] chain) {
    if (cert == null) {
      return new CertificateStatusResponse(
          CertificateStatusResponse.Status.UNKNOWN, "Sertifika boş.");
    }

    X509Certificate issuer = findIssuer(cert, chain);
    if (issuer == null) {
      log.debug(
          "Issuer sertifikası zincir içinde bulunamadı: {}",
          cert.getIssuerX500Principal().getName());
      return new CertificateStatusResponse(
          CertificateStatusResponse.Status.UNKNOWN,
          "Issuer sertifikası mevcut zincirde yok; revocation kontrolü yapılamadı.");
    }

    // 1) OCSP dene
    CertificateStatusResponse ocsp = tryOcsp(cert, issuer);
    if (ocsp != null) return ocsp;

    // 2) CRL fallback
    CertificateStatusResponse crl = tryCrl(cert, issuer);
    if (crl != null) return crl;

    return new CertificateStatusResponse(
        CertificateStatusResponse.Status.UNKNOWN,
        "OCSP/CRL yanıt vermedi; revocation kontrolü yapılamadı.");
  }

  /* ------------------------------------------------------------------ */
  /* OCSP                                                                */
  /* ------------------------------------------------------------------ */

  private CertificateStatusResponse tryOcsp(X509Certificate cert, X509Certificate issuer) {
    List<String> ocspUrls = extractOcspUrls(cert);
    if (ocspUrls.isEmpty()) {
      log.debug("Sertifikada AIA OCSP URL yok: {}", cert.getSubjectX500Principal().getName());
      return null;
    }
    OCSPReq req;
    try {
      req = buildOcspRequest(cert, issuer);
    } catch (Exception e) {
      log.debug("OCSP request inşa edilemedi: {}", e.getMessage());
      return null;
    }
    byte[] encoded;
    try {
      encoded = req.getEncoded();
    } catch (IOException e) {
      log.debug("OCSP request encode edilemedi: {}", e.getMessage());
      return null;
    }

    for (String url : ocspUrls) {
      try {
        CertificateStatusResponse resp = postOcsp(url, encoded, cert, url);
        if (resp != null) return resp;
      } catch (Exception e) {
        log.debug("OCSP {} başarısız: {}", url, e.getMessage());
      }
    }
    return null;
  }

  private OCSPReq buildOcspRequest(X509Certificate cert, X509Certificate issuer) throws Exception {
    DigestCalculatorProvider digCalcProv = new BcDigestCalculatorProvider();
    CertificateID id =
        new JcaCertificateID(
            digCalcProv.get(CertificateID.HASH_SHA1), issuer, cert.getSerialNumber());
    OCSPReqBuilder builder = new OCSPReqBuilder();
    builder.addRequest(id);
    return builder.build();
  }

  private CertificateStatusResponse postOcsp(
      String url, byte[] encoded, X509Certificate cert, String responderUrl) throws IOException {
    Request request =
        new Request.Builder()
            .url(url)
            .post(RequestBody.create(encoded, OCSP_MEDIA_TYPE))
            .addHeader("Content-Type", "application/ocsp-request")
            .addHeader("Accept", "application/ocsp-response")
            .build();
    try (Response response = http.newCall(request).execute()) {
      ResponseBody body = response.body();
      if (!response.isSuccessful() || body == null) {
        log.debug("OCSP {} HTTP {}", url, response.code());
        return null;
      }
      byte[] respBytes = body.bytes();
      OCSPResp ocsp;
      try {
        ocsp = new OCSPResp(respBytes);
      } catch (IOException e) {
        log.debug("OCSP response parse hatası", e);
        return null;
      }
      if (ocsp.getStatus() != OCSPResp.SUCCESSFUL) {
        log.debug("OCSP servisi durumu başarısız: {}", ocsp.getStatus());
        return null;
      }
      Object basic = unwrapOcspResponse(ocsp);
      if (!(basic instanceof BasicOCSPResp)) {
        return null;
      }
      BasicOCSPResp basicResp = (BasicOCSPResp) basic;
      SingleResp[] responses = basicResp.getResponses();
      if (responses == null || responses.length == 0) {
        return null;
      }
      return interpretOcsp(responses[0], cert, responderUrl, basicResp);
    }
  }

  private static Object unwrapOcspResponse(OCSPResp ocsp) {
    try {
      return ocsp.getResponseObject();
    } catch (org.bouncycastle.cert.ocsp.OCSPException e) {
      log.debug("OCSP responseObject parse hatası: {}", e.getMessage());
      return null;
    }
  }

  private CertificateStatusResponse interpretOcsp(
      SingleResp single, X509Certificate cert, String responderUrl, BasicOCSPResp basicResp) {
    Object certStatus = single.getCertStatus();
    if (certStatus == null) {
      StringBuilder msg = new StringBuilder("OCSP GOOD");
      appendIfPresent(msg, "thisUpdate", single.getThisUpdate());
      appendIfPresent(msg, "nextUpdate", single.getNextUpdate());
      appendIfPresent(msg, "producedAt", basicResp == null ? null : basicResp.getProducedAt());
      appendIfPresent(msg, "responder", responderUrl);
      return new CertificateStatusResponse(CertificateStatusResponse.Status.ACTIVE, msg.toString());
    }
    if (certStatus instanceof RevokedStatus) {
      RevokedStatus revoked = (RevokedStatus) certStatus;
      Integer reason = null;
      try {
        if (revoked.hasRevocationReason()) {
          reason = revoked.getRevocationReason();
        }
      } catch (Exception ignore) {
        /* hasRevocationReason patlarsa reason unknown bırakılır */
      }
      StringBuilder msg = new StringBuilder("OCSP REVOKED");
      appendIfPresent(msg, "at", revoked.getRevocationTime());
      if (reason != null) {
        msg.append("; reason=")
            .append(crlReasonText(reason))
            .append(" (")
            .append(reason)
            .append(")");
      }
      appendIfPresent(msg, "responder", responderUrl);
      return new CertificateStatusResponse(
          CertificateStatusResponse.Status.REVOKED, msg.toString());
    }
    if (certStatus instanceof UnknownStatus) {
      log.debug(
          "OCSP responder '{}' UNKNOWN döndü (cert serial={}); CRL fallback denenecek.",
          responderUrl,
          cert.getSerialNumber());
      return null; // CRL fallback ile devam et
    }
    return new CertificateStatusResponse(
        CertificateStatusResponse.Status.UNKNOWN,
        "OCSP tanımsız durum: "
            + certStatus.getClass().getSimpleName()
            + " (responder="
            + responderUrl
            + ")");
  }

  private static List<String> extractOcspUrls(X509Certificate cert) {
    List<String> urls = new ArrayList<String>();
    try {
      byte[] aiaExtBytes = cert.getExtensionValue(Extension.authorityInfoAccess.getId());
      if (aiaExtBytes == null) return urls;
      ASN1Primitive aiaPrim = unwrapOctetString(aiaExtBytes);
      AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(aiaPrim);
      for (AccessDescription ad : aia.getAccessDescriptions()) {
        if (X509ObjectIdentifiers.id_ad_ocsp.equals(ad.getAccessMethod())
            || OCSPObjectIdentifiers.id_pkix_ocsp.equals(ad.getAccessMethod())) {
          GeneralName name = ad.getAccessLocation();
          if (name.getTagNo() == GeneralName.uniformResourceIdentifier) {
            String url = ((DERIA5String) name.getName()).getString();
            if (url != null && !url.isEmpty()) urls.add(url);
          }
        }
      }
    } catch (Exception e) {
      log.debug("AIA extension parse hatası", e);
    }
    return urls;
  }

  /* ------------------------------------------------------------------ */
  /* CRL                                                                 */
  /* ------------------------------------------------------------------ */

  private CertificateStatusResponse tryCrl(X509Certificate cert, X509Certificate issuer) {
    List<String> crlUrls = extractCrlUrls(cert);
    if (crlUrls.isEmpty()) {
      log.debug("Sertifikada CDP CRL URL yok: {}", cert.getSubjectX500Principal().getName());
      return null;
    }

    CertificateFactory cf;
    try {
      cf = CertificateFactory.getInstance("X.509");
    } catch (java.security.cert.CertificateException e) {
      log.debug("X.509 CertificateFactory yok", e);
      return null;
    }

    for (String url : crlUrls) {
      try {
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = http.newCall(req).execute()) {
          ResponseBody body = resp.body();
          if (!resp.isSuccessful() || body == null) continue;
          byte[] crlBytes = body.bytes();
          X509CRL crl = (X509CRL) cf.generateCRL(new ByteArrayInputStream(crlBytes));

          // Issuer doğrulama: CRL'in vereni gerçekten sertifikanın issuer'ı mı?
          try {
            crl.verify(issuer.getPublicKey());
          } catch (Exception verifyFail) {
            log.debug("CRL signature doğrulanamadı ({}): {}", url, verifyFail.getMessage());
            continue;
          }

          X509CRLEntry entry = crl.getRevokedCertificate(cert.getSerialNumber());
          if (entry != null) {
            StringBuilder msg = new StringBuilder("CRL REVOKED");
            appendIfPresent(msg, "at", entry.getRevocationDate());
            // CRL entry'de reason extension opsiyonel — yoksa sessiz geç.
            String reasonText = crlEntryReasonText(entry);
            if (reasonText != null) {
              msg.append("; reason=").append(reasonText);
            }
            appendIfPresent(msg, "thisUpdate", crl.getThisUpdate());
            appendIfPresent(msg, "nextUpdate", crl.getNextUpdate());
            appendIfPresent(msg, "url", url);
            return new CertificateStatusResponse(
                CertificateStatusResponse.Status.REVOKED, msg.toString());
          }
          StringBuilder msg = new StringBuilder("CRL listede revoke yok");
          appendIfPresent(msg, "thisUpdate", crl.getThisUpdate());
          appendIfPresent(msg, "nextUpdate", crl.getNextUpdate());
          appendIfPresent(msg, "url", url);
          return new CertificateStatusResponse(
              CertificateStatusResponse.Status.ACTIVE, msg.toString());
        }
      } catch (Exception e) {
        log.debug("CRL {} fetch/parse başarısız: {}", url, e.getMessage());
      }
    }
    return null;
  }

  private static List<String> extractCrlUrls(X509Certificate cert) {
    List<String> urls = new ArrayList<String>();
    try {
      byte[] cdpBytes = cert.getExtensionValue(Extension.cRLDistributionPoints.getId());
      if (cdpBytes == null) return urls;
      ASN1Primitive cdpPrim = unwrapOctetString(cdpBytes);
      CRLDistPoint cdp = CRLDistPoint.getInstance(cdpPrim);
      for (DistributionPoint dp : cdp.getDistributionPoints()) {
        DistributionPointName dpn = dp.getDistributionPoint();
        if (dpn == null) continue;
        if (dpn.getType() == DistributionPointName.FULL_NAME) {
          GeneralNames names = GeneralNames.getInstance(dpn.getName());
          for (GeneralName gn : names.getNames()) {
            if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
              String url = ((DERIA5String) gn.getName()).getString();
              if (url != null && url.toLowerCase().startsWith("http")) {
                urls.add(url);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.debug("CDP extension parse hatası", e);
    }
    return urls;
  }

  /* ------------------------------------------------------------------ */
  /* Helpers                                                             */
  /* ------------------------------------------------------------------ */

  /**
   * X.509 extension içeriği bir {@code OCTET STRING} olarak sarılır; içindeki gerçek ASN.1 yapıyı
   * çıkarır.
   */
  private static ASN1Primitive unwrapOctetString(byte[] extBytes) throws IOException {
    try (ASN1InputStream in1 = new ASN1InputStream(extBytes)) {
      ASN1Primitive outer = in1.readObject();
      if (outer instanceof ASN1OctetString) {
        byte[] octets = ((ASN1OctetString) outer).getOctets();
        try (ASN1InputStream in2 = new ASN1InputStream(octets)) {
          return in2.readObject();
        }
      }
      return outer;
    }
  }

  private static X509Certificate findIssuer(X509Certificate cert, X509Certificate[] chain) {
    if (chain == null) return null;
    for (X509Certificate c : chain) {
      if (c == null || c.equals(cert)) continue;
      if (c.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
        return c;
      }
    }
    return null;
  }

  /**
   * Mesaj inşa yardımcısı: değer non-null/non-empty ise {@code "; key=value"} ekler. Boş alanları
   * süzerek mesajın temiz görünmesini sağlar.
   */
  private static void appendIfPresent(StringBuilder builder, String key, Object value) {
    if (value == null) {
      return;
    }
    String text = value.toString();
    if (text.isEmpty()) {
      return;
    }
    if (builder.length() > 0) {
      builder.append("; ");
    }
    builder.append(key).append('=').append(text);
  }

  /**
   * RFC 5280 §5.3.1 CRLReason değerleri. Numerik kod yerine human-readable etiket döner; frontend
   * için doğrudan kullanılabilir.
   */
  static String crlReasonText(int reason) {
    switch (reason) {
      case 0:
        return "unspecified";
      case 1:
        return "keyCompromise";
      case 2:
        return "cACompromise";
      case 3:
        return "affiliationChanged";
      case 4:
        return "superseded";
      case 5:
        return "cessationOfOperation";
      case 6:
        return "certificateHold";
      case 8:
        return "removeFromCRL";
      case 9:
        return "privilegeWithdrawn";
      case 10:
        return "aACompromise";
      default:
        return "unknown(" + reason + ")";
    }
  }

  /**
   * CRL entry'sinde opsiyonel CRLReason extension'ı (OID {@code 2.5.29.21}) varsa human-readable
   * label döner; yoksa null. Extension'ın değeri DER-encoded ENUMERATED'tir.
   */
  static String crlEntryReasonText(X509CRLEntry entry) {
    if (entry == null) {
      return null;
    }
    byte[] ext = entry.getExtensionValue(Extension.reasonCode.getId());
    if (ext == null) {
      return null;
    }
    try (ASN1InputStream in1 = new ASN1InputStream(ext)) {
      ASN1Primitive outer = in1.readObject();
      if (!(outer instanceof ASN1OctetString)) {
        return null;
      }
      try (ASN1InputStream in2 = new ASN1InputStream(((ASN1OctetString) outer).getOctets())) {
        ASN1Primitive inner = in2.readObject();
        CRLReason bcReason = CRLReason.getInstance(inner);
        if (bcReason == null) {
          return null;
        }
        return crlReasonText(bcReason.getValue().intValue());
      }
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
      log.debug("CRL entry reasonCode parse hatası: {}", e.getMessage());
      return null;
    }
  }
}
