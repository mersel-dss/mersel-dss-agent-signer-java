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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.mersel.dss.agent.api.config.SignerProperties;

/**
 * Akıllı kartın döndürdüğü kısmî sertifika zincirini (tipik olarak yalnız end-entity), AIA
 * (Authority Information Access) extension'larını takip ederek root CA'ya kadar tamamlar.
 *
 * <p>PAdES-B-LT ve XAdES-B-LT seviyesinde tam chain'in imza içine gömülmesi gerekir; akıllı kart
 * üzerindeki keystore çoğunlukla sadece end-entity sertifikasını barındırdığından bu sınıf chain
 * tamamlama görevini üstlenir.
 *
 * <h2>Davranış</h2>
 *
 * <ul>
 *   <li>{@link SignerProperties.Chain#isAiaEnabled()} kapalıysa giriş zinciri aynen döndürülür.
 *   <li>Self-signed (issuer = subject) sertifikaya ulaşıldığında durulur.
 *   <li>{@link SignerProperties.Chain#getMaxDepth()} aşılırsa durulur (loop koruması).
 *   <li>Ağ hatası, geçersiz sertifika veya AIA URL'i bulunmazsa <em>sessizce</em> mevcut zincirle
 *       devam eder — kesinti olmaz, çünkü PAdES-B-B (basic) zaten geçerlidir.
 *   <li>HTTP cevabı PKCS#7/CMS bundle ise içerdiği tüm sertifikalar değerlendirilir; subject =
 *       beklenen issuer olanı seçilir.
 *   <li>Aynı subject DN tekrar görünürse atlanır (dejavu set).
 * </ul>
 *
 * <h2>Bağımlılıklar</h2>
 *
 * Sadece JDK ({@link HttpURLConnection}) ve halihazırda projede bulunan BouncyCastle ASN.1 parser
 * kullanılır. Vendor lock-in yok.
 */
@Service
public class CertificateChainBuilder {

  private static final Logger log = LoggerFactory.getLogger(CertificateChainBuilder.class);

  /** RFC 5280: id-ad-caIssuers OID = 1.3.6.1.5.5.7.48.2 */
  private static final org.bouncycastle.asn1.ASN1ObjectIdentifier ID_AD_CA_ISSUERS =
      X509ObjectIdentifiers.id_ad_caIssuers;

  private final SignerProperties.Chain chainProps;
  private final Function<String, byte[]> httpFetcher;

  @Autowired
  public CertificateChainBuilder(SignerProperties properties) {
    this(properties.getChain(), null);
  }

  /** Test-friendly ctor: HTTP fetcher'ı override edilebilir (mocked downloads için). */
  CertificateChainBuilder(SignerProperties.Chain chainProps, Function<String, byte[]> httpFetcher) {
    this.chainProps = chainProps;
    this.httpFetcher = httpFetcher != null ? httpFetcher : this::defaultHttpGet;
  }

  /**
   * AIA takibi yapmayan no-op builder döndürür. Birim testlerde network çağrısı tetiklenmesini
   * önlemek için kullanılır.
   */
  public static CertificateChainBuilder passthrough() {
    SignerProperties.Chain disabled = new SignerProperties.Chain();
    disabled.setAiaEnabled(false);
    return new CertificateChainBuilder(disabled, url -> null);
  }

  /**
   * Verilen zinciri AIA takibi ile genişletir. Giriş zinciri en az 1 elemanlı olmalıdır (end-entity
   * ilk sıradadır).
   *
   * @param input akıllı kart / keystore'dan gelen kısmî zincir (en az end-entity)
   * @return tamamlanmış zincir (yeni dizi); AIA kapalıysa veya genişletilemediyse {@code input}
   *     birebir kopyalanır
   */
  public Certificate[] build(Certificate[] input) {
    if (input == null || input.length == 0) {
      return input;
    }
    if (!chainProps.isAiaEnabled()) {
      return input;
    }
    if (!(input[0] instanceof X509Certificate)) {
      return input;
    }

    List<X509Certificate> chain = new ArrayList<X509Certificate>(input.length + 2);
    Set<String> seenSubjects = new HashSet<String>();
    for (Certificate c : input) {
      if (c instanceof X509Certificate) {
        X509Certificate x = (X509Certificate) c;
        chain.add(x);
        seenSubjects.add(canonicalSubject(x));
      }
    }

    int maxDepth = Math.max(2, chainProps.getMaxDepth());
    while (chain.size() < maxDepth) {
      X509Certificate last = chain.get(chain.size() - 1);
      if (isSelfSigned(last)) {
        log.debug("Chain build: self-signed root yakalandı, depth={}", chain.size());
        break;
      }
      X509Certificate issuer = downloadIssuer(last, seenSubjects);
      if (issuer == null) {
        log.debug(
            "Chain build: '{}' için issuer indirilemedi, mevcut zincir (depth={}) ile dönülüyor",
            last.getSubjectX500Principal().getName(),
            chain.size());
        break;
      }
      chain.add(issuer);
      seenSubjects.add(canonicalSubject(issuer));
    }

    if (chain.size() == input.length) {
      return input; // genişlemedi
    }

    log.info(
        "Sertifika zinciri AIA ile tamamlandı: girişte {} → çıkışta {} sertifika",
        input.length,
        chain.size());

    Certificate[] out = new Certificate[chain.size()];
    return chain.toArray(out);
  }

  /* ------------------------------------------------------------------ */
  /* AIA URL extraction + download                                      */
  /* ------------------------------------------------------------------ */

  private X509Certificate downloadIssuer(X509Certificate cert, Set<String> seenSubjects) {
    List<String> urls = extractCaIssuersUrls(cert);
    if (urls.isEmpty()) {
      return null;
    }
    String expectedIssuerDn = canonicalIssuer(cert);
    for (String url : urls) {
      byte[] body;
      try {
        body = httpFetcher.apply(url);
      } catch (RuntimeException e) {
        log.debug("AIA indirme hatası url={}: {}", url, e.getMessage());
        continue;
      }
      if (body == null || body.length == 0) continue;

      X509Certificate match = pickIssuer(body, expectedIssuerDn, seenSubjects);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  static List<String> extractCaIssuersUrls(X509Certificate cert) {
    List<String> urls = new ArrayList<String>();
    try {
      byte[] aiaExtBytes = cert.getExtensionValue(Extension.authorityInfoAccess.getId());
      if (aiaExtBytes == null) return urls;
      ASN1Primitive prim = unwrapOctetString(aiaExtBytes);
      AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(prim);
      for (AccessDescription ad : aia.getAccessDescriptions()) {
        if (!ID_AD_CA_ISSUERS.equals(ad.getAccessMethod())) continue;
        GeneralName name = ad.getAccessLocation();
        if (name.getTagNo() == GeneralName.uniformResourceIdentifier) {
          String url = ((DERIA5String) name.getName()).getString();
          if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            urls.add(url);
          }
        }
      }
    } catch (Exception e) {
      log.debug("CA Issuers AIA parse hatası", e);
    }
    return urls;
  }

  /**
   * İndirilen body DER tek sertifika, PEM tek sertifika veya PKCS#7/CMS bundle olabilir. Hangi
   * format gelirse gelsin, içindeki sertifikalardan beklenen subject = beklenen issuer DN olanı
   * döndürür; eşleşme yoksa null.
   */
  static X509Certificate pickIssuer(byte[] body, String expectedSubjectDn, Set<String> seen) {
    Collection<? extends Certificate> parsed = parseCertificates(body);
    if (parsed == null || parsed.isEmpty()) return null;

    X509Certificate fallback = null;
    for (Certificate c : parsed) {
      if (!(c instanceof X509Certificate)) continue;
      X509Certificate x = (X509Certificate) c;
      String subject = canonicalSubject(x);
      if (seen.contains(subject)) continue;
      if (subject.equalsIgnoreCase(expectedSubjectDn)) {
        return x;
      }
      if (fallback == null) fallback = x;
    }
    return fallback;
  }

  private static Collection<? extends Certificate> parseCertificates(byte[] body) {
    CertificateFactory cf;
    try {
      cf = CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      return null;
    }
    try (InputStream in = new ByteArrayInputStream(body)) {
      Collection<? extends Certificate> bundle = cf.generateCertificates(in);
      if (bundle != null && !bundle.isEmpty()) return bundle;
    } catch (Exception ignored) {
      // single-cert path'i dene
    }
    try (InputStream in = new ByteArrayInputStream(body)) {
      Certificate single = cf.generateCertificate(in);
      List<Certificate> list = new ArrayList<Certificate>(1);
      list.add(single);
      return list;
    } catch (Exception e) {
      log.debug(
          "İndirilen body sertifikaya parse edilemedi (size={}): {}", body.length, e.getMessage());
      return null;
    }
  }

  /* ------------------------------------------------------------------ */
  /* HTTP                                                                */
  /* ------------------------------------------------------------------ */

  private byte[] defaultHttpGet(String url) {
    HttpURLConnection conn = null;
    try {
      URL u = new URL(url);
      conn = (HttpURLConnection) u.openConnection();
      conn.setConnectTimeout(chainProps.getHttpTimeoutMs());
      conn.setReadTimeout(chainProps.getHttpTimeoutMs());
      conn.setRequestMethod("GET");
      conn.setRequestProperty("User-Agent", "mersel-dss-agent-signer/2.x AIA-chain-builder");
      conn.setRequestProperty(
          "Accept",
          "application/pkix-cert, application/x-x509-ca-cert, application/pkcs7-mime, */*");
      conn.setInstanceFollowRedirects(true);
      int status = conn.getResponseCode();
      if (status < 200 || status >= 300) {
        log.debug("AIA HTTP {} → status={}", url, status);
        return null;
      }
      try (InputStream in = conn.getInputStream()) {
        return readAll(in);
      }
    } catch (Exception e) {
      throw new RuntimeException("AIA HTTP başarısız: " + e.getMessage(), e);
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private static byte[] readAll(InputStream in) throws java.io.IOException {
    java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
    byte[] tmp = new byte[4096];
    int n;
    while ((n = in.read(tmp)) > 0) {
      buf.write(tmp, 0, n);
    }
    return buf.toByteArray();
  }

  /* ------------------------------------------------------------------ */
  /* Helpers                                                             */
  /* ------------------------------------------------------------------ */

  private static ASN1Primitive unwrapOctetString(byte[] extBytes) throws java.io.IOException {
    try (ASN1InputStream is = new ASN1InputStream(extBytes)) {
      ASN1Primitive outer = is.readObject();
      if (outer instanceof ASN1OctetString) {
        try (ASN1InputStream inner = new ASN1InputStream(((ASN1OctetString) outer).getOctets())) {
          return inner.readObject();
        }
      }
      return outer;
    }
  }

  private static boolean isSelfSigned(X509Certificate cert) {
    return canonicalSubject(cert).equalsIgnoreCase(canonicalIssuer(cert));
  }

  private static String canonicalSubject(X509Certificate cert) {
    return cert.getSubjectX500Principal().getName("CANONICAL");
  }

  private static String canonicalIssuer(X509Certificate cert) {
    return cert.getIssuerX500Principal().getName("CANONICAL");
  }
}
