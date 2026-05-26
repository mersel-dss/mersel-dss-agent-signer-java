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
package io.mersel.dss.agent.api.models;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.mersel.dss.agent.api.models.enums.CertificatePurpose;
import io.mersel.dss.agent.api.models.enums.ExtendedKeyUsage;
import io.mersel.dss.agent.api.models.enums.KeyUsage;
import io.mersel.dss.agent.api.models.enums.TurkishCertificatePolicy;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PKCS#11 token üzerinde bulunan bir X.509 sertifikası için REST yanıt modeli.
 *
 * <p>Her extension için iki bakış vardır:
 *
 * <ul>
 *   <li><b>Tipli enum seti</b> — bildiğimiz/standart değerler (kolay filtreleme, IDE auto-complete,
 *       OpenAPI'de enum görünür).
 *   <li><b>Ham OID seti</b> — extension'da bulunan TÜM OID'ler (audit / debug / yeni vendor OID'i
 *       için forward-compat).
 * </ul>
 *
 * <p>İki set <b>kesişimseldir</b>: enum'a düşen bir OID hem {@code …Oids} hem de {@code …} (tipli)
 * alanda görünür. Bu, "tipli set sadece tanınanları taşıyor" surprise'ını ortadan kaldırır ve
 * frontend'in tek alan üzerinden tablo render etmesini mümkün kılar.
 */
@Schema(description = "PKCS#11 token'da bulunan bir X.509 sertifikası")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateResponse {

  @Schema(description = "Sertifikanın kart üzerindeki PKCS#11 alias'ı (kanonik ID).")
  private String id;

  @Schema(description = "Sertifika konusunun CN (Common Name) alanı; CN yoksa ham subject DN.")
  private String subject;

  @Schema(description = "Verici (issuer) RFC 2253 DN.")
  private String issuer;

  @Schema(
      description =
          "VKN (10 hane) veya TCKN (11 hane). X.509 SubjectDN'in SERIALNUMBER OID (2.5.4.5)'inden"
              + " veya CN içeriğinden çıkarılır.")
  private String taxId;

  @Schema(description = "X.509 sertifika serial number'ı (hex, alt çizgi/boşluk yok).")
  private String x509SerialNumber;

  @Schema(description = "Geçerlilik başlangıcı (ISO-8601 UTC, ör. `2024-12-02T09:48:54Z`).")
  private String notBefore;

  @Schema(description = "Geçerlilik bitişi (ISO-8601 UTC).")
  private String notAfter;

  @Schema(description = "Sertifikanın geçerlilik durumu (ACTIVE, EXPIRED, REVOKED, UNKNOWN).")
  private CertificateStatusResponse.Status status;

  @Schema(
      description =
          "Sertifika ETSI EN 319 412-5 anlamında nitelikli (QES — QCStatements extension içerir)"
              + " mi? Alt statement OID'leri için `qcStatementOids` alanına bakın.")
  private Boolean qualified;

  /* ---------- KeyUsage (RFC 5280 §4.2.1.3, OID 2.5.29.15) ---------- */

  @Schema(
      description =
          "KeyUsage extension'ından parse edilen tipli bit'ler (DIGITAL_SIGNATURE,"
              + " NON_REPUDIATION, KEY_ENCIPHERMENT, vs.). Boş set: extension yok.")
  private Set<KeyUsage> keyUsages = Collections.emptySet();

  /* ---------- ExtendedKeyUsage (RFC 5280 §4.2.1.12, OID 2.5.29.37) ---------- */

  @Schema(
      description =
          "ExtendedKeyUsage extension'ından enum'a eşleşen tipli değerler (SERVER_AUTH,"
              + " CLIENT_AUTH, CODE_SIGNING, EMAIL_PROTECTION, TIME_STAMPING, OCSP_SIGNING,"
              + " DOCUMENT_SIGNING).")
  private Set<ExtendedKeyUsage> extendedKeyUsages = Collections.emptySet();

  @Schema(
      description =
          "ExtendedKeyUsage extension'ında bulunan TÜM ham OID'ler — enum'a düşenler dahil."
              + " Audit / vendor-specific OID görünürlüğü için.")
  private Set<String> extendedKeyUsageOids = Collections.emptySet();

  /* ---------- CertificatePolicies (RFC 5280 §4.2.1.4, OID 2.5.29.32) ---------- */

  @Schema(
      description =
          "CertificatePolicies extension'ında bulunan TÜM ham policy OID'leri. TÜBİTAK Kamu SM"
              + " e-imza / Mali Mühür ayrımının asıl kaynağı.")
  private Set<String> certificatePolicyOids = Collections.emptySet();

  @Schema(
      description =
          "TÜBİTAK Kamu SM enum'una eşleşen Türkçe politikalar (KAMU_SM_QES_INDIVIDUAL,"
              + " KAMU_SM_QES_CORPORATE, MALI_MUHUR_SIGNING, MALI_MUHUR_ENCRYPTION).")
  private Set<TurkishCertificatePolicy> turkishCertificatePolicies = Collections.emptySet();

  /* ---------- QCStatements (ETSI EN 319 412-5, OID 1.3.6.1.5.5.7.1.3) ---------- */

  @Schema(
      description =
          "QCStatements extension'ından ham statement OID'leri (QcCompliance, QcSSCD, QcType,"
              + " PDS, vs.). `qualified=true` ise bu set boş olmamalıdır.")
  private Set<String> qcStatementOids = Collections.emptySet();

  /* ---------- Türetilmiş ---------- */

  @Schema(
      description =
          "Sertifikanın iş amacı (Türkçe politika OID'i varsa kilit, yoksa RFC 5280 KU/EKU karar"
              + " ağacı): SIGNING / ENCRYPTION / AUTHENTICATION / MIXED / OTHER.")
  private CertificatePurpose purpose;

  @Schema(
      description =
          "PAdES/XAdES imzalama için uygun mu (purpose=SIGNING|MIXED + geçerli tarih + REVOKED"
              + " değil).")
  private boolean eligibleForSignature;

  @Schema(
      description =
          "Bu kart üzerinde imzalama için ÖNERİLEN sertifika (qualified > Türkçe SIGNING policy >"
              + " en yeni notBefore). Frontend listede pre-select etmeli.")
  private boolean recommended;

  public CertificateResponse() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getTaxId() {
    return taxId;
  }

  public void setTaxId(String taxId) {
    this.taxId = taxId;
  }

  public String getX509SerialNumber() {
    return x509SerialNumber;
  }

  public void setX509SerialNumber(String x509SerialNumber) {
    this.x509SerialNumber = x509SerialNumber;
  }

  public String getNotBefore() {
    return notBefore;
  }

  public void setNotBefore(String notBefore) {
    this.notBefore = notBefore;
  }

  public String getNotAfter() {
    return notAfter;
  }

  public void setNotAfter(String notAfter) {
    this.notAfter = notAfter;
  }

  public CertificateStatusResponse.Status getStatus() {
    return status;
  }

  public void setStatus(CertificateStatusResponse.Status status) {
    this.status = status;
  }

  public Boolean getQualified() {
    return qualified;
  }

  public void setQualified(Boolean qualified) {
    this.qualified = qualified;
  }

  public Set<KeyUsage> getKeyUsages() {
    return keyUsages;
  }

  public void setKeyUsages(Set<KeyUsage> keyUsages) {
    this.keyUsages =
        (keyUsages == null)
            ? Collections.<KeyUsage>emptySet()
            : new LinkedHashSet<KeyUsage>(keyUsages);
  }

  public Set<ExtendedKeyUsage> getExtendedKeyUsages() {
    return extendedKeyUsages;
  }

  public void setExtendedKeyUsages(Set<ExtendedKeyUsage> extendedKeyUsages) {
    this.extendedKeyUsages =
        (extendedKeyUsages == null)
            ? Collections.<ExtendedKeyUsage>emptySet()
            : new LinkedHashSet<ExtendedKeyUsage>(extendedKeyUsages);
  }

  public Set<String> getExtendedKeyUsageOids() {
    return extendedKeyUsageOids;
  }

  public void setExtendedKeyUsageOids(Set<String> extendedKeyUsageOids) {
    this.extendedKeyUsageOids =
        (extendedKeyUsageOids == null)
            ? Collections.<String>emptySet()
            : new LinkedHashSet<String>(extendedKeyUsageOids);
  }

  public Set<String> getCertificatePolicyOids() {
    return certificatePolicyOids;
  }

  public void setCertificatePolicyOids(Set<String> certificatePolicyOids) {
    this.certificatePolicyOids =
        (certificatePolicyOids == null)
            ? Collections.<String>emptySet()
            : new LinkedHashSet<String>(certificatePolicyOids);
  }

  public Set<TurkishCertificatePolicy> getTurkishCertificatePolicies() {
    return turkishCertificatePolicies;
  }

  public void setTurkishCertificatePolicies(
      Set<TurkishCertificatePolicy> turkishCertificatePolicies) {
    this.turkishCertificatePolicies =
        (turkishCertificatePolicies == null)
            ? Collections.<TurkishCertificatePolicy>emptySet()
            : new LinkedHashSet<TurkishCertificatePolicy>(turkishCertificatePolicies);
  }

  public Set<String> getQcStatementOids() {
    return qcStatementOids;
  }

  public void setQcStatementOids(Set<String> qcStatementOids) {
    this.qcStatementOids =
        (qcStatementOids == null)
            ? Collections.<String>emptySet()
            : new LinkedHashSet<String>(qcStatementOids);
  }

  public CertificatePurpose getPurpose() {
    return purpose;
  }

  public void setPurpose(CertificatePurpose purpose) {
    this.purpose = purpose;
  }

  public boolean isEligibleForSignature() {
    return eligibleForSignature;
  }

  public void setEligibleForSignature(boolean eligibleForSignature) {
    this.eligibleForSignature = eligibleForSignature;
  }

  public boolean isRecommended() {
    return recommended;
  }

  public void setRecommended(boolean recommended) {
    this.recommended = recommended;
  }
}
