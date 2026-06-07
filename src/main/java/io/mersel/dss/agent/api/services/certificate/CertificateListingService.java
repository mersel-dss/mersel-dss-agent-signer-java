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

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.OptionalLong;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.mersel.dss.agent.api.exceptions.CertificateLookupException;
import io.mersel.dss.agent.api.models.CertificateResponse;
import io.mersel.dss.agent.api.models.CertificateStatusResponse;
import io.mersel.dss.agent.api.models.enums.CertificatePurpose;
import io.mersel.dss.agent.api.models.enums.TurkishCertificatePolicy;
import io.mersel.dss.agent.api.services.keystore.IaikPkcs11Signer;
import io.mersel.dss.agent.api.services.keystore.Pkcs11PublicCertificateReader;
import io.mersel.dss.agent.api.services.keystore.TokenCertificate;
import io.mersel.dss.agent.api.services.smartcard.SmartCardManager;
import io.mersel.dss.agent.api.services.virtualtoken.VirtualToken;

/**
 * Verilen terminaldeki karta <b>PIN'siz</b> bağlanır, X.509 sertifikalarını {@link
 * Pkcs11PublicCertificateReader} üzerinden çeker (low-level {@code C_FindObjects} → SunPKCS11
 * P11KeyStore'un {@code CKF_LOGIN_REQUIRED} yüzünden zorladığı C_Login'i bypass eder). PIN yalnız
 * imzalama uçlarında ({@code /pades/sign}, {@code /xades/sign}, {@code POST /gibApplication})
 * istenir.
 *
 * <p>Sertifika zinciri token'daki tüm cert'lerden subject-issuer matching ile in-memory inşa edilir
 * ({@link #buildChainFromBundle}); KamuSM kartlarında ara/kök CA cert'leri genellikle birlikte
 * yazılıdır. Eksik halkalar AIA üzerinden {@code RevocationChecker}/{@code CertificateChainBuilder}
 * tarafından sonradan tamamlanabilir.
 *
 * <p>KeyUsage / EKU / CertificatePolicies / QCStatements parse'ı + iş amacı türetimini {@link
 * CertificateInspector} üzerinden hesaplar; liste seviyesinde "önerilen" sertifika
 * post-processing'i {@link #annotateRecommendation(List)} ile yapılır.
 */
@Service
public class CertificateListingService {

  private static final Logger log = LoggerFactory.getLogger(CertificateListingService.class);

  private final SmartCardManager cardManager;
  private final CertificateInspector inspector;
  private final CertificateChainBuilder chainBuilder;

  public CertificateListingService(
      SmartCardManager cardManager,
      CertificateInspector inspector,
      CertificateChainBuilder chainBuilder) {
    this.cardManager = cardManager;
    this.inspector = inspector;
    this.chainBuilder = chainBuilder;
  }

  public List<CertificateResponse> listCertificates(String terminalName, String pkcs11LibraryPath) {
    return listCertificates(terminalName, pkcs11LibraryPath, null);
  }

  public List<CertificateResponse> listCertificates(
      String terminalName, String pkcs11LibraryPath, String cardType) {
    Path libraryPath = cardManager.resolveLibrary(terminalName, pkcs11LibraryPath, cardType);
    log.info(
        "PKCS#11 lib çözümlendi (low-level PIN'siz listeleme): {} (terminal={},"
            + " pkcs11LibraryPath={}, cardType={})",
        libraryPath,
        terminalName,
        pkcs11LibraryPath,
        cardType);

    // Aynı anda birden çok kart takılıyken seçilen okuyucunun slot'una daralt; eşleşme yoksa
    // (terminalName boş ya da vendor slot açıklaması okuyucu adıyla uyuşmuyor) tüm slotlar okunur.
    OptionalLong scopedSlot = IaikPkcs11Signer.matchSlotIdByTerminal(libraryPath, terminalName);
    if (scopedSlot.isPresent()) {
      log.info(
          "Sertifika listeleme terminal='{}' → slotID={} ile sınırlandı.",
          terminalName,
          scopedSlot.getAsLong());
    }

    List<TokenCertificate> tokenCerts = Pkcs11PublicCertificateReader.read(libraryPath, scopedSlot);
    List<X509Certificate> bundle = new ArrayList<X509Certificate>(tokenCerts.size());
    for (TokenCertificate tc : tokenCerts) {
      bundle.add(tc.getCertificate());
    }

    List<CertificateResponse> result = new ArrayList<CertificateResponse>(tokenCerts.size());
    for (TokenCertificate tc : tokenCerts) {
      X509Certificate[] chain = buildChainFromBundle(tc.getCertificate(), bundle);
      result.add(toResponse(tc, chain));
    }
    return annotateRecommendation(result);
  }

  /**
   * Sanal kart ("Dummy Card") için sertifikaları listeler. PKCS#11 sanal kartta lib yolu doğrudan
   * verilerek mevcut PIN'siz okuma yolu kullanılır; PKCS#12 (PFX) sanal kartta yüklenmiş software
   * keystore üzerinden alias'lar gezilir, zincir keystore'dan alınır ve aynı {@code toResponse} +
   * {@code annotateRecommendation} işlenir.
   */
  public List<CertificateResponse> listFromVirtual(VirtualToken token) {
    if (token == null) {
      throw new IllegalArgumentException("token null olamaz.");
    }
    if (token.isPkcs11()) {
      return listCertificates(token.getName(), token.getPkcs11LibraryPath(), null);
    }
    return listFromPkcs12(token);
  }

  private List<CertificateResponse> listFromPkcs12(VirtualToken token) {
    KeyStore ks = token.getKeyStore();
    char[] pw = token.passwordChars();
    List<CertificateResponse> result = new ArrayList<CertificateResponse>();
    try {
      Enumeration<String> aliases = ks.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        Certificate leaf;
        try {
          if (!ks.isKeyEntry(alias)) {
            continue; // yalnız imza için private key taşıyan girişleri göster
          }
          leaf = ks.getCertificate(alias);
        } catch (Exception perAlias) {
          log.debug("PFX alias '{}' okunamadı: {}", alias, perAlias.getMessage());
          continue;
        }
        if (!(leaf instanceof X509Certificate)) {
          continue;
        }
        X509Certificate[] chain = pkcs12Chain(ks, alias, (X509Certificate) leaf);
        result.add(toResponse(new TokenCertificate(alias, (X509Certificate) leaf), chain));
      }
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new CertificateLookupException("PFX sertifikaları listelenemedi: " + e.getMessage(), e);
    } finally {
      java.util.Arrays.fill(pw, '\0');
    }
    if (result.isEmpty()) {
      throw new CertificateLookupException("PFX içinde sertifika bulunamadı.");
    }
    return annotateRecommendation(result);
  }

  /** PKCS#12 keystore'dan alias zincirini X.509 dizisine çevirir; yoksa tek leaf döner. */
  private static X509Certificate[] pkcs12Chain(KeyStore ks, String alias, X509Certificate leaf) {
    try {
      Certificate[] chain = ks.getCertificateChain(alias);
      if (chain != null && chain.length > 0) {
        List<X509Certificate> out = new ArrayList<X509Certificate>(chain.length);
        for (Certificate c : chain) {
          if (c instanceof X509Certificate) {
            out.add((X509Certificate) c);
          }
        }
        if (!out.isEmpty()) {
          return out.toArray(new X509Certificate[0]);
        }
      }
    } catch (Exception e) {
      log.debug("PFX alias '{}' zinciri alınamadı: {}", alias, e.getMessage());
    }
    return new X509Certificate[] {leaf};
  }

  /**
   * Token'daki diğer sertifikalar arasından subject == leaf.issuer matching ile zinciri inşa eder.
   * Self-signed (subject == issuer) noktasında durur; aksi takdirde uygun issuer bulunamazsa
   * zinciri orada keser. KamuSM kartlarında genelde ara + kök CA birlikte yazılı olduğundan tam
   * zincir döner.
   */
  static X509Certificate[] buildChainFromBundle(
      X509Certificate leaf, List<X509Certificate> bundle) {
    if (leaf == null) {
      return new X509Certificate[0];
    }
    List<X509Certificate> chain = new ArrayList<X509Certificate>();
    chain.add(leaf);
    X509Certificate current = leaf;
    while (true) {
      X500Principal subj = current.getSubjectX500Principal();
      X500Principal iss = current.getIssuerX500Principal();
      if (subj != null && subj.equals(iss)) {
        break; // self-signed root, zincir tamam
      }
      X509Certificate issuer = null;
      for (X509Certificate cand : bundle) {
        if (cand == current) {
          continue;
        }
        if (containsByIdentity(chain, cand)) {
          continue; // döngü guard
        }
        if (cand.getSubjectX500Principal().equals(iss)) {
          issuer = cand;
          break;
        }
      }
      if (issuer == null) {
        break;
      }
      chain.add(issuer);
      current = issuer;
    }
    return chain.toArray(new X509Certificate[0]);
  }

  private static boolean containsByIdentity(List<X509Certificate> list, X509Certificate cert) {
    for (X509Certificate c : list) {
      if (c == cert) {
        return true;
      }
    }
    return false;
  }

  /**
   * Token'dan inşa edilen kısmî zinciri AIA (Authority Information Access) extension üzerinden kök
   * CA'ya kadar tamamlamayı dener. {@link CertificateChainBuilder} self-signed root'a varınca
   * durur, network hatasında sessizce mevcut zincirle devam eder; bu yüzden başarısızlık riski yok
   * — en kötü ihtimalle gelen zincir aynen geri döner.
   *
   * <p>Listeleme akışı boyunca her sertifika için ayrı AIA çağrısı yapılır; KamuSM kartlarında
   * genellikle 1-2 ara CA + 1 root indirilir (toplam ~2-3 HTTP, timeout 3sn × 3). Cache eklemek
   * istersen {@link CertificateChainBuilder} seviyesinde ortak bir LRU mantıklı olur.
   */
  X509Certificate[] extendChainViaAia(X509Certificate[] localChain) {
    if (localChain == null || localChain.length == 0) {
      return localChain;
    }
    if (chainBuilder == null) {
      return localChain;
    }
    try {
      java.security.cert.Certificate[] expanded = chainBuilder.build(localChain);
      X509Certificate[] x509 = filterX509(expanded);
      return x509.length >= localChain.length ? x509 : localChain;
    } catch (RuntimeException e) {
      log.debug("AIA chain genişletme başarısız (listeleme): {}", e.getMessage());
      return localChain;
    }
  }

  /**
   * "Önerilen" sertifikayı belirler. Aday'lar: {@code eligibleForSignature=true} olanlar. Sıralama
   * (öncelik sırası):
   *
   * <ol>
   *   <li>{@code qualified=true} (ETSI QES) önce.
   *   <li>Türkçe SIGNING politikası taşıyanlar (Mali Mühür İmza / KamuSM QES).
   *   <li>{@code purpose==SIGNING} (saf imza), {@code MIXED} sonra.
   *   <li>En yeni {@code notBefore}.
   * </ol>
   *
   * <p>Liste değiştirilir ve aynı liste döner (chain'lemek için).
   */
  List<CertificateResponse> annotateRecommendation(List<CertificateResponse> all) {
    if (all == null || all.isEmpty()) {
      return all;
    }
    for (CertificateResponse cr : all) {
      cr.setRecommended(false);
    }
    List<CertificateResponse> candidates = new ArrayList<CertificateResponse>();
    for (CertificateResponse cr : all) {
      if (cr.isEligibleForSignature()) {
        candidates.add(cr);
      }
    }
    if (candidates.isEmpty()) {
      return all;
    }
    Collections.sort(candidates, RECOMMENDATION_PRIORITY);
    candidates.get(0).setRecommended(true);
    return all;
  }

  /**
   * Recommendation öncelik karşılaştırıcısı — küçük indeks daha "önerilen". 4 kademeli sıralama:
   * qualified → Türkçe SIGNING policy → purpose=SIGNING → en yeni notBefore.
   */
  static final Comparator<CertificateResponse> RECOMMENDATION_PRIORITY =
      new Comparator<CertificateResponse>() {
        @Override
        public int compare(CertificateResponse a, CertificateResponse b) {
          int aq = (Boolean.TRUE.equals(a.getQualified())) ? 0 : 1;
          int bq = (Boolean.TRUE.equals(b.getQualified())) ? 0 : 1;
          if (aq != bq) {
            return Integer.compare(aq, bq);
          }
          int atr = hasTurkishSigningPolicy(a) ? 0 : 1;
          int btr = hasTurkishSigningPolicy(b) ? 0 : 1;
          if (atr != btr) {
            return Integer.compare(atr, btr);
          }
          int ap = (a.getPurpose() == CertificatePurpose.SIGNING) ? 0 : 1;
          int bp = (b.getPurpose() == CertificatePurpose.SIGNING) ? 0 : 1;
          if (ap != bp) {
            return Integer.compare(ap, bp);
          }
          Instant ai = parseInstant(a.getNotBefore());
          Instant bi = parseInstant(b.getNotBefore());
          if (ai == null && bi == null) return 0;
          if (ai == null) return 1;
          if (bi == null) return -1;
          return bi.compareTo(ai);
        }
      };

  private static boolean hasTurkishSigningPolicy(CertificateResponse cr) {
    if (cr.getTurkishCertificatePolicies() == null) return false;
    for (TurkishCertificatePolicy p : cr.getTurkishCertificatePolicies()) {
      if (p.impliedPurpose() == CertificatePurpose.SIGNING) {
        return true;
      }
    }
    return false;
  }

  private CertificateResponse toResponse(
      TokenCertificate tc, java.security.cert.Certificate[] chainRaw) {
    X509Certificate cert = tc.getCertificate();
    X509Certificate[] chain = filterX509(chainRaw);

    CertificateResponse resp = new CertificateResponse();
    String x509Serial = cert.getSerialNumber().toString(16);
    resp.setId(x509Serial);
    resp.setLabel(tc.getAlias());

    String subjectDn = cert.getSubjectX500Principal().getName();
    String cn = extractRdn(subjectDn, BCStyle.CN);
    String taxId = extractSerialNumberOrTckn(cert);
    resp.setSubject(cn != null ? cn : subjectDn);
    resp.setIssuer(cert.getIssuerX500Principal().getName());
    resp.setTaxId(taxId);
    resp.setX509SerialNumber(x509Serial);
    resp.setNotBefore(formatInstant(cert.getNotBefore()));
    resp.setNotAfter(formatInstant(cert.getNotAfter()));

    // Token'a yazılı bundle çoğunlukla yalnız leaf içeriyor (özellikle KamuSM kartlarında).
    // RevocationChecker.findIssuer() chain üzerinde issuer bulamazsa OCSP/CRL'i atlamak zorunda
    // kalıyor → status=UNKNOWN. Bu yüzden bundle-only chain'i AIA üzerinden root'a kadar
    // genişletip o tam zinciri RevocationChecker'a veriyoruz. CertificateChainBuilder zaten
    // self-signed root noktasında durur ve AIA HTTP timeout'larını yutar — başarısızsa elimizdeki
    // zincirle devam eder, listeleme kesilmez.
    X509Certificate[] revocationChain = extendChainViaAia(chain);
    CertificateStatusResponse statusResp = inspector.inspectWithRevocation(cert, revocationChain);
    resp.setStatus(statusResp.getStatus());
    resp.setValid(computeValidity(cert));
    resp.setQualified(inspector.isQualified(cert));

    resp.setKeyUsages(inspector.keyUsage(cert));

    CertificateInspector.ExtendedKeyUsageResult ekuRes = inspector.extendedKeyUsage(cert);
    resp.setExtendedKeyUsages(ekuRes.getTyped());
    resp.setExtendedKeyUsageOids(ekuRes.getAllOids());

    CertificateInspector.CertificatePoliciesResult polRes = inspector.certificatePolicies(cert);
    resp.setCertificatePolicyOids(polRes.getAllOids());
    resp.setTurkishCertificatePolicies(polRes.getTurkishPolicies());

    resp.setQcStatementOids(inspector.qcStatementOids(cert));

    CertificatePurpose purpose =
        inspector.purpose(resp.getKeyUsages(), ekuRes.getTyped(), polRes.getTurkishPolicies());
    resp.setPurpose(purpose);

    resp.setEligibleForSignature(computeEligibility(cert, purpose, statusResp));
    return resp;
  }

  private static boolean computeEligibility(
      X509Certificate cert, CertificatePurpose purpose, CertificateStatusResponse status) {
    if (purpose != CertificatePurpose.SIGNING && purpose != CertificatePurpose.MIXED) {
      return false;
    }
    if (status == null) {
      return false;
    }
    CertificateStatusResponse.Status s = status.getStatus();
    if (s == CertificateStatusResponse.Status.REVOKED
        || s == CertificateStatusResponse.Status.EXPIRED) {
      return false;
    }
    Instant now = Instant.now();
    Date notBefore = cert.getNotBefore();
    Date notAfter = cert.getNotAfter();
    if (notBefore != null && now.isBefore(notBefore.toInstant())) {
      return false;
    }
    if (notAfter != null && now.isAfter(notAfter.toInstant())) {
      return false;
    }
    return true;
  }

  /**
   * Sertifikanın "şu anda zamansal geçerlilik penceresi içinde mi?" boolean'ı — saf {@code
   * notBefore <= now <= notAfter} kontrolü. Network'siz, deterministik; OCSP/CRL durumundan
   * <b>bağımsız</b>.
   *
   * <p>Tasarım gerekçesi: KamuSM kartlarında token'a yazılı sertifika zincirinde issuer cert eksik
   * kalabiliyor (özellikle eski kartlar yalnız leaf yazıyor); bu durumda {@link RevocationChecker}
   * {@code UNKNOWN} döndürür. Eski sürümde {@code valid = (status == ACTIVE)} olduğundan, OCSP'i
   * bağlanamayan veya issuer'ı eksik olan tamamen sağlam bir cert {@code valid=false} görünüyordu.
   * Frontend bu kullanıcının "geçerli sertifikam var ama UI bana yok diyor" şikayetiyle
   * karşılaşıyordu. Yeni sözleşme: {@code valid} <b>yalnız</b> süre kontrolü yapar. Revocation
   * durumunu kullanıcıya göstermek isteyen frontend hâlâ {@code status} alanını okuyabilir.
   *
   * @return cert null değil, notBefore geçmiş ve notAfter henüz dolmamışsa {@code true}
   */
  static boolean computeValidity(X509Certificate cert) {
    if (cert == null) {
      return false;
    }
    Instant now = Instant.now();
    Date notBefore = cert.getNotBefore();
    Date notAfter = cert.getNotAfter();
    if (notBefore != null && now.isBefore(notBefore.toInstant())) {
      return false;
    }
    if (notAfter != null && now.isAfter(notAfter.toInstant())) {
      return false;
    }
    return true;
  }

  private static X509Certificate[] filterX509(java.security.cert.Certificate[] chain) {
    if (chain == null) return new X509Certificate[0];
    List<X509Certificate> out = new ArrayList<X509Certificate>();
    for (java.security.cert.Certificate c : chain) {
      if (c instanceof X509Certificate) out.add((X509Certificate) c);
    }
    return out.toArray(new X509Certificate[0]);
  }

  private static String extractRdn(String dn, org.bouncycastle.asn1.ASN1ObjectIdentifier oid) {
    try {
      X500Name x500 = new X500Name(dn);
      RDN[] rdns = x500.getRDNs(oid);
      if (rdns == null || rdns.length == 0) return null;
      return IETFUtils.valueToString(rdns[0].getFirst().getValue());
    } catch (Exception e) {
      return null;
    }
  }

  private static String extractSerialNumberOrTckn(X509Certificate cert) {
    try {
      JcaX509CertificateHolder holder = new JcaX509CertificateHolder(cert);
      X500Name subject = holder.getSubject();

      RDN[] snRdns = subject.getRDNs(BCStyle.SERIALNUMBER);
      if (snRdns != null && snRdns.length > 0) {
        String raw = IETFUtils.valueToString(snRdns[0].getFirst().getValue());
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
          return digits.length() > 11 ? digits.substring(digits.length() - 11) : digits;
        }
      }

      RDN[] cnRdns = subject.getRDNs(BCStyle.CN);
      if (cnRdns != null && cnRdns.length > 0) {
        String cn = IETFUtils.valueToString(cnRdns[0].getFirst().getValue());
        String digits = cn.replaceAll("[^0-9]", "");
        if (digits.length() == 10 || digits.length() == 11) return digits;
      }
    } catch (Exception e) {
      log.debug("Sertifika SN çıkarılamadı", e);
    }
    return null;
  }

  private static String formatInstant(Date date) {
    if (date == null) return null;
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        ZonedDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC));
  }

  private static Instant parseInstant(String iso) {
    if (iso == null || iso.isEmpty()) return null;
    try {
      return ZonedDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
    } catch (RuntimeException re) {
      return null;
    }
  }
}
