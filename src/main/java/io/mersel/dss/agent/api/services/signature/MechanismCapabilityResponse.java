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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.mersel.dss.agent.api.services.keystore.Pkcs11MechanismProbe;

/**
 * {@code GET /smartcard/mechanisms} JSON yanıtı. Düz, okunması kolay yapıda; frontend bu çıktıyı
 * "Sorun bildir" / "Tanılama" panelinde aynen render edebilir.
 *
 * <p>{@link XadesProfileSummary} alt-yapısı en kritik bilgi: "kartınızın aktif xades profili",
 * "fallback gerekiyor mu", "hangi uyarılar var".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MechanismCapabilityResponse {

  private String terminalName;
  private String atr;
  private String cardType;
  private String pkcs11Library;

  private String tokenLabel;
  private String tokenManufacturerId;
  private String tokenModel;
  private String tokenFirmwareVersion;
  private String tokenHardwareVersion;
  private String tokenSerialMasked;

  /** Token reflection desteği var mı (JDK 1.8 patch'ine bağlı). */
  private boolean mechanismListSupported;

  private List<Pkcs11MechanismProbe.MechanismEntry> mechanisms;

  /** XAdES için yüksek seviyeli özet (RSA + ECDSA önerileri, fallback durumu, uyarılar). */
  private XadesProfileSummary xadesProfile;

  public MechanismCapabilityResponse() {
    /* Jackson */
  }

  /* getters / setters --------------------------------------------------- */

  public String getTerminalName() {
    return terminalName;
  }

  public void setTerminalName(String terminalName) {
    this.terminalName = terminalName;
  }

  public String getAtr() {
    return atr;
  }

  public void setAtr(String atr) {
    this.atr = atr;
  }

  public String getCardType() {
    return cardType;
  }

  public void setCardType(String cardType) {
    this.cardType = cardType;
  }

  public String getPkcs11Library() {
    return pkcs11Library;
  }

  public void setPkcs11Library(String pkcs11Library) {
    this.pkcs11Library = pkcs11Library;
  }

  public String getTokenLabel() {
    return tokenLabel;
  }

  public void setTokenLabel(String tokenLabel) {
    this.tokenLabel = tokenLabel;
  }

  public String getTokenManufacturerId() {
    return tokenManufacturerId;
  }

  public void setTokenManufacturerId(String tokenManufacturerId) {
    this.tokenManufacturerId = tokenManufacturerId;
  }

  public String getTokenModel() {
    return tokenModel;
  }

  public void setTokenModel(String tokenModel) {
    this.tokenModel = tokenModel;
  }

  public String getTokenFirmwareVersion() {
    return tokenFirmwareVersion;
  }

  public void setTokenFirmwareVersion(String tokenFirmwareVersion) {
    this.tokenFirmwareVersion = tokenFirmwareVersion;
  }

  public String getTokenHardwareVersion() {
    return tokenHardwareVersion;
  }

  public void setTokenHardwareVersion(String tokenHardwareVersion) {
    this.tokenHardwareVersion = tokenHardwareVersion;
  }

  public String getTokenSerialMasked() {
    return tokenSerialMasked;
  }

  public void setTokenSerialMasked(String tokenSerialMasked) {
    this.tokenSerialMasked = tokenSerialMasked;
  }

  public boolean isMechanismListSupported() {
    return mechanismListSupported;
  }

  public void setMechanismListSupported(boolean mechanismListSupported) {
    this.mechanismListSupported = mechanismListSupported;
  }

  public List<Pkcs11MechanismProbe.MechanismEntry> getMechanisms() {
    return mechanisms;
  }

  public void setMechanisms(List<Pkcs11MechanismProbe.MechanismEntry> mechanisms) {
    this.mechanisms =
        mechanisms == null
            ? null
            : Collections.unmodifiableList(
                new ArrayList<Pkcs11MechanismProbe.MechanismEntry>(mechanisms));
  }

  public XadesProfileSummary getXadesProfile() {
    return xadesProfile;
  }

  public void setXadesProfile(XadesProfileSummary xadesProfile) {
    this.xadesProfile = xadesProfile;
  }

  /* ------------------- nested type ------------------- */

  /**
   * RSA ve ECDSA için xades4j'ye verilecek profilin özeti. Frontend bu özeti tek satır olarak
   * ("Kartınız RSA-SHA256 imza için uygun, ECDSA imza desteği yok") gösterebilir.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class XadesProfileSummary {

    /** RSA imzalama için ilk uygun {@code CKM_*} (yoksa null). */
    private String preferredRsaMechanism;

    /** ECDSA imzalama için ilk uygun {@code CKM_*} (yoksa null). */
    private String preferredEcdsaMechanism;

    /**
     * Önerilen XAdES Signature URL'i (RSA varsayılır; ECDSA-only kart varsa ECDSA URL'i). null →
     * imzalama için uygun mekanizma yok, kart kullanılamaz.
     */
    private String recommendedSignatureUrl;

    /** Önerilen XAdES Digest URL'i. */
    private String recommendedDigestUrl;

    /** Default xades4j profili işliyor mu? false ise {@link #fallbackStrategy} doludur. */
    private boolean defaultProfileWorks;

    /** "raw-rsa-soft-digest" gibi sözel kod; default işliyorsa null. */
    private String fallbackStrategy;

    /** Algoritma seçimi sırasında üretilen uyarı satırları (insan-okur). */
    private List<String> warnings;

    public XadesProfileSummary() {
      /* Jackson */
    }

    public String getPreferredRsaMechanism() {
      return preferredRsaMechanism;
    }

    public void setPreferredRsaMechanism(String preferredRsaMechanism) {
      this.preferredRsaMechanism = preferredRsaMechanism;
    }

    public String getPreferredEcdsaMechanism() {
      return preferredEcdsaMechanism;
    }

    public void setPreferredEcdsaMechanism(String preferredEcdsaMechanism) {
      this.preferredEcdsaMechanism = preferredEcdsaMechanism;
    }

    public String getRecommendedSignatureUrl() {
      return recommendedSignatureUrl;
    }

    public void setRecommendedSignatureUrl(String recommendedSignatureUrl) {
      this.recommendedSignatureUrl = recommendedSignatureUrl;
    }

    public String getRecommendedDigestUrl() {
      return recommendedDigestUrl;
    }

    public void setRecommendedDigestUrl(String recommendedDigestUrl) {
      this.recommendedDigestUrl = recommendedDigestUrl;
    }

    public boolean isDefaultProfileWorks() {
      return defaultProfileWorks;
    }

    public void setDefaultProfileWorks(boolean defaultProfileWorks) {
      this.defaultProfileWorks = defaultProfileWorks;
    }

    public String getFallbackStrategy() {
      return fallbackStrategy;
    }

    public void setFallbackStrategy(String fallbackStrategy) {
      this.fallbackStrategy = fallbackStrategy;
    }

    public List<String> getWarnings() {
      return warnings;
    }

    public void setWarnings(List<String> warnings) {
      this.warnings =
          warnings == null ? null : Collections.unmodifiableList(new ArrayList<String>(warnings));
    }
  }
}
