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

import java.io.IOException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.qualified.QCStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.models.CertificateStatusResponse;
import io.mersel.dss.agent.api.models.enums.CertificatePurpose;
import io.mersel.dss.agent.api.models.enums.ExtendedKeyUsage;
import io.mersel.dss.agent.api.models.enums.KeyUsage;
import io.mersel.dss.agent.api.models.enums.TurkishCertificatePolicy;

/**
 * X.509 sertifika introspection katmanı:
 *
 * <ul>
 *   <li>Validity + revocation delegation ({@link RevocationChecker}).
 *   <li>KeyUsage (RFC 5280 §4.2.1.3) + ExtendedKeyUsage (§4.2.1.12) parse'ı.
 *   <li>CertificatePolicies (§4.2.1.4) parse'ı + Türkiye'ye özgü politika eşleştirmesi ({@link
 *       TurkishCertificatePolicy}).
 *   <li>QCStatements (ETSI EN 319 412-5, RFC 3739) parse'ı.
 *   <li>Yüksek seviyeli iş amacı türetimi ({@link CertificatePurpose}) — Türkçe politika OID'i
 *       varsa RFC karar ağacını atlar ve doğrudan kilitler.
 * </ul>
 */
@Component
public class CertificateInspector {

  private static final Logger log = LoggerFactory.getLogger(CertificateInspector.class);

  private final RevocationChecker revocationChecker;

  public CertificateInspector(RevocationChecker revocationChecker) {
    this.revocationChecker = revocationChecker;
  }

  /** Hızlı yerel kontrol: sadece validity + extension'lar. Network yok. */
  public CertificateStatusResponse inspect(X509Certificate cert) {
    if (cert == null) {
      return new CertificateStatusResponse(
          CertificateStatusResponse.Status.UNKNOWN, "Sertifika boş.");
    }
    try {
      cert.checkValidity();
    } catch (CertificateExpiredException e) {
      return new CertificateStatusResponse(
          CertificateStatusResponse.Status.EXPIRED,
          "Sertifika süresi dolmuş: " + cert.getNotAfter());
    } catch (CertificateNotYetValidException e) {
      return new CertificateStatusResponse(
          CertificateStatusResponse.Status.UNKNOWN,
          "Sertifika henüz geçerli değil: " + cert.getNotBefore());
    }
    return new CertificateStatusResponse(
        CertificateStatusResponse.Status.ACTIVE, "Sertifika geçerlilik penceresi içinde.");
  }

  public CertificateStatusResponse inspectWithRevocation(
      X509Certificate cert, X509Certificate[] chain) {
    CertificateStatusResponse local = inspect(cert);
    if (local.getStatus() != CertificateStatusResponse.Status.ACTIVE) {
      return local;
    }
    return revocationChecker.check(cert, chain);
  }

  /**
   * QCStatements extension ({@code 1.3.6.1.5.5.7.1.3}) varlığı — ETSI EN 319 412-5'e göre
   * "nitelikli" (QES) sertifika işareti. Alt statement OID'leri için {@link
   * #qcStatementOids(X509Certificate)}.
   */
  public boolean isQualified(X509Certificate cert) {
    if (cert == null) {
      return false;
    }
    try {
      byte[] ext = cert.getExtensionValue(Extension.qCStatements.getId());
      return ext != null;
    } catch (Exception e) {
      log.debug("QCStatements okunamadı", e);
      return false;
    }
  }

  public boolean hasNonRepudiation(X509Certificate cert) {
    return keyUsage(cert).contains(KeyUsage.NON_REPUDIATION);
  }

  /* ---------------- KeyUsage / EKU ---------------- */

  /**
   * RFC 5280 KeyUsage extension'ından bit-set döner. Extension yoksa veya parse hatasında boş set
   * döner.
   */
  public Set<KeyUsage> keyUsage(X509Certificate cert) {
    if (cert == null) {
      return Collections.emptySet();
    }
    boolean[] bits = cert.getKeyUsage();
    if (bits == null) {
      return Collections.emptySet();
    }
    EnumSet<KeyUsage> out = EnumSet.noneOf(KeyUsage.class);
    for (KeyUsage ku : KeyUsage.values()) {
      int idx = ku.bit();
      if (idx < bits.length && bits[idx]) {
        out.add(ku);
      }
    }
    return out;
  }

  /**
   * ExtendedKeyUsage extension'ından {@link ExtendedKeyUsageResult} döner: tüm ham OID'ler, enum'a
   * düşen tipli set ve standart-dışı OID'lerin alt seti.
   */
  public ExtendedKeyUsageResult extendedKeyUsage(X509Certificate cert) {
    if (cert == null) {
      return ExtendedKeyUsageResult.EMPTY;
    }
    List<String> oids;
    try {
      oids = cert.getExtendedKeyUsage();
    } catch (CertificateParsingException cpe) {
      log.debug("EKU parse hatası: {}", cpe.getMessage());
      return ExtendedKeyUsageResult.EMPTY;
    }
    if (oids == null || oids.isEmpty()) {
      return ExtendedKeyUsageResult.EMPTY;
    }
    Set<String> all = new LinkedHashSet<String>();
    Set<ExtendedKeyUsage> typed = new LinkedHashSet<ExtendedKeyUsage>();
    Set<String> unknown = new LinkedHashSet<String>();
    for (String oid : oids) {
      if (oid == null || oid.isEmpty()) {
        continue;
      }
      all.add(oid);
      java.util.Optional<ExtendedKeyUsage> mapped = ExtendedKeyUsage.fromOid(oid);
      if (mapped.isPresent()) {
        typed.add(mapped.get());
      } else {
        unknown.add(oid);
      }
    }
    return new ExtendedKeyUsageResult(
        Collections.unmodifiableSet(all),
        Collections.unmodifiableSet(typed),
        Collections.unmodifiableSet(unknown));
  }

  /* ---------------- CertificatePolicies (2.5.29.32) ---------------- */

  /**
   * CertificatePolicies extension'ından {@link CertificatePoliciesResult} döner: tüm ham policy
   * OID'leri + enum'a eşleşen Türkçe politika seti.
   *
   * <p>TÜBİTAK Kamu SM e-imza / Mali Mühür kartlarında SIGNING vs ENCRYPTION ayrımının en güvenilir
   * kaynağı buradadır.
   */
  public CertificatePoliciesResult certificatePolicies(X509Certificate cert) {
    if (cert == null) {
      return CertificatePoliciesResult.EMPTY;
    }
    byte[] extBytes = cert.getExtensionValue(Extension.certificatePolicies.getId());
    if (extBytes == null) {
      return CertificatePoliciesResult.EMPTY;
    }
    Set<String> all = new LinkedHashSet<String>();
    Set<TurkishCertificatePolicy> turkish = new LinkedHashSet<TurkishCertificatePolicy>();
    try {
      ASN1Primitive inner = unwrapOctetString(extBytes);
      if (!(inner instanceof ASN1Sequence)) {
        return CertificatePoliciesResult.EMPTY;
      }
      ASN1Sequence seq = (ASN1Sequence) inner;
      for (int i = 0; i < seq.size(); i++) {
        PolicyInformation pi = PolicyInformation.getInstance(seq.getObjectAt(i));
        String oid = pi.getPolicyIdentifier().getId();
        all.add(oid);
        TurkishCertificatePolicy.fromOid(oid).ifPresent(turkish::add);
      }
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
      log.debug("CertificatePolicies parse hatası: {}", e.getMessage());
      return CertificatePoliciesResult.EMPTY;
    }
    return new CertificatePoliciesResult(
        Collections.unmodifiableSet(all), Collections.unmodifiableSet(turkish));
  }

  /* ---------------- QCStatements (1.3.6.1.5.5.7.1.3) ---------------- */

  /**
   * QCStatements extension'ından ham statement OID'leri döner (ETSI EN 319 412-5 §5).
   *
   * <p>Bilinen statement OID'leri:
   *
   * <ul>
   *   <li>{@code 0.4.0.1862.1.1} — esi4-qcStatement-1 (QcCompliance — RFC 3739).
   *   <li>{@code 0.4.0.1862.1.4} — esi4-qcStatement-4 (QcSSCD — anahtar SSCD'de tutuluyor).
   *   <li>{@code 0.4.0.1862.1.6} — esi4-qcStatement-6 (QcType: eSign / eSeal / eWebsite).
   *   <li>{@code 0.4.0.1862.1.5} — esi4-qcStatement-5 (PDS — PKI Disclosure Statements).
   *   <li>{@code 1.3.6.1.5.5.7.11.2} — RFC 3739 id-qcs-pkixQCSyntax-v2.
   * </ul>
   */
  public Set<String> qcStatementOids(X509Certificate cert) {
    if (cert == null) {
      return Collections.emptySet();
    }
    byte[] extBytes = cert.getExtensionValue(Extension.qCStatements.getId());
    if (extBytes == null) {
      return Collections.emptySet();
    }
    Set<String> out = new LinkedHashSet<String>();
    try {
      ASN1Primitive inner = unwrapOctetString(extBytes);
      if (!(inner instanceof ASN1Sequence)) {
        return Collections.emptySet();
      }
      ASN1Sequence seq = (ASN1Sequence) inner;
      for (int i = 0; i < seq.size(); i++) {
        QCStatement qc = QCStatement.getInstance(seq.getObjectAt(i));
        out.add(qc.getStatementId().getId());
      }
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
      log.debug("QCStatements parse hatası: {}", e.getMessage());
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(out);
  }

  /* ---------------- purpose() karar ağacı ---------------- */

  /**
   * Yüksek seviyeli iş amacı türetimi — Türkçe politika OID'leri öncelikli, sonra RFC 5280 KeyUsage
   * / EKU karar ağacı.
   *
   * <p>Karar sırası:
   *
   * <ol>
   *   <li>{@link TurkishCertificatePolicy} → impliedPurpose ile kilitle (TÜBİTAK Kamu SM
   *       sertifikalarının asıl sinyali).
   *   <li>{@code KEY_CERT_SIGN} veya {@code CRL_SIGN} → {@link CertificatePurpose#OTHER}.
   *   <li>{@code NON_REPUDIATION} → {@link CertificatePurpose#SIGNING} (QES için kritik bit).
   *   <li>{@code DIGITAL_SIGNATURE} + EKU CLIENT_AUTH/SERVER_AUTH + encipherment yok → {@link
   *       CertificatePurpose#AUTHENTICATION} (TLS cert).
   *   <li>{@code DIGITAL_SIGNATURE} + encipherment yok → {@link CertificatePurpose#SIGNING}.
   *   <li>Sadece encipherment → {@link CertificatePurpose#ENCRYPTION}.
   *   <li>{@code DIGITAL_SIGNATURE} + encipherment → {@link CertificatePurpose#MIXED}.
   *   <li>Aksi hâlde {@link CertificatePurpose#OTHER}.
   * </ol>
   */
  public CertificatePurpose purpose(X509Certificate cert) {
    return purpose(
        keyUsage(cert),
        extendedKeyUsage(cert).getTyped(),
        certificatePolicies(cert).getTurkishPolicies());
  }

  /** Test-friendly arg-only overload — saf fonksiyon. */
  public CertificatePurpose purpose(
      Set<KeyUsage> ku, Set<ExtendedKeyUsage> eku, Set<TurkishCertificatePolicy> turkishPolicies) {
    if (ku == null) {
      ku = Collections.emptySet();
    }
    if (eku == null) {
      eku = Collections.emptySet();
    }
    if (turkishPolicies == null) {
      turkishPolicies = Collections.emptySet();
    }

    // 1. Türkçe politika kısa-yolu — TÜBİTAK Kamu SM kartlarında bit kombinasyonu
    // bazen non-standart, ama policy OID daima doğru kategoriyi söyler.
    if (!turkishPolicies.isEmpty()) {
      boolean signing = false;
      boolean encryption = false;
      for (TurkishCertificatePolicy p : turkishPolicies) {
        if (p.impliedPurpose() == CertificatePurpose.SIGNING) {
          signing = true;
        } else if (p.impliedPurpose() == CertificatePurpose.ENCRYPTION) {
          encryption = true;
        }
      }
      if (signing && encryption) {
        return CertificatePurpose.MIXED;
      }
      if (signing) {
        return CertificatePurpose.SIGNING;
      }
      if (encryption) {
        return CertificatePurpose.ENCRYPTION;
      }
    }

    // 2. RFC 5280 generic karar ağacı.
    boolean caBits = ku.contains(KeyUsage.KEY_CERT_SIGN) || ku.contains(KeyUsage.CRL_SIGN);
    if (caBits) {
      return CertificatePurpose.OTHER;
    }
    boolean digSig = ku.contains(KeyUsage.DIGITAL_SIGNATURE);
    boolean nonRep = ku.contains(KeyUsage.NON_REPUDIATION);
    boolean encipher =
        ku.contains(KeyUsage.KEY_ENCIPHERMENT) || ku.contains(KeyUsage.DATA_ENCIPHERMENT);
    boolean tlsAuth =
        eku.contains(ExtendedKeyUsage.CLIENT_AUTH) || eku.contains(ExtendedKeyUsage.SERVER_AUTH);

    // NON_REPUDIATION her zaman SIGNING'i kazanır — QES için kritik bit.
    if (nonRep) {
      return CertificatePurpose.SIGNING;
    }
    if (digSig && tlsAuth && !encipher) {
      return CertificatePurpose.AUTHENTICATION;
    }
    if (digSig && !encipher) {
      return CertificatePurpose.SIGNING;
    }
    if (encipher && !digSig) {
      return CertificatePurpose.ENCRYPTION;
    }
    if (digSig && encipher) {
      return CertificatePurpose.MIXED;
    }
    return CertificatePurpose.OTHER;
  }

  /* ---------------- helpers ---------------- */

  private static ASN1Primitive unwrapOctetString(byte[] extensionValueBytes) throws IOException {
    ASN1InputStream ais1 = new ASN1InputStream(extensionValueBytes);
    try {
      ASN1Primitive outer = ais1.readObject();
      ASN1OctetString octet = ASN1OctetString.getInstance(outer);
      ASN1InputStream ais2 = new ASN1InputStream(octet.getOctets());
      try {
        return ais2.readObject();
      } finally {
        ais2.close();
      }
    } finally {
      ais1.close();
    }
  }

  /* ---------------- result envelopes ---------------- */

  /** {@link #extendedKeyUsage(X509Certificate)} dönüş zarfı. */
  public static final class ExtendedKeyUsageResult {
    public static final ExtendedKeyUsageResult EMPTY =
        new ExtendedKeyUsageResult(
            Collections.<String>emptySet(),
            Collections.<ExtendedKeyUsage>emptySet(),
            Collections.<String>emptySet());

    private final Set<String> allOids;
    private final Set<ExtendedKeyUsage> typed;
    private final Set<String> unknownOids;

    public ExtendedKeyUsageResult(
        Set<String> allOids, Set<ExtendedKeyUsage> typed, Set<String> unknownOids) {
      this.allOids = allOids;
      this.typed = typed;
      this.unknownOids = unknownOids;
    }

    /** EKU extension'ında bulunan TÜM OID'ler — enum'a düşenler dahil. */
    public Set<String> getAllOids() {
      return allOids;
    }

    /** Enum'a düşen tipli EKU değerleri. */
    public Set<ExtendedKeyUsage> getTyped() {
      return typed;
    }

    /** Enum'a düşmeyen ham OID'ler (TÜBİTAK / vendor-specific). */
    public Set<String> getUnknownOids() {
      return unknownOids;
    }
  }

  /** {@link #certificatePolicies(X509Certificate)} dönüş zarfı. */
  public static final class CertificatePoliciesResult {
    public static final CertificatePoliciesResult EMPTY =
        new CertificatePoliciesResult(
            Collections.<String>emptySet(), Collections.<TurkishCertificatePolicy>emptySet());

    private final Set<String> allOids;
    private final Set<TurkishCertificatePolicy> turkishPolicies;

    public CertificatePoliciesResult(
        Set<String> allOids, Set<TurkishCertificatePolicy> turkishPolicies) {
      this.allOids = allOids;
      this.turkishPolicies = turkishPolicies;
    }

    /** Tüm ham policy OID'leri (KamuSM + ETSI + uluslararası ne varsa). */
    public Set<String> getAllOids() {
      return allOids;
    }

    /** TÜBİTAK Kamu SM enum'una eşleşen politikalar (varsa). */
    public Set<TurkishCertificatePolicy> getTurkishPolicies() {
      return turkishPolicies;
    }
  }
}
