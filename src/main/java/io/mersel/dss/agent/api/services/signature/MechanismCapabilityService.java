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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.mersel.dss.agent.api.services.keystore.Pkcs11MechanismProbe;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Mechanisms;
import io.mersel.dss.agent.api.services.smartcard.SmartCardInfo;
import io.mersel.dss.agent.api.services.smartcard.SmartCardManager;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;

/**
 * "Bu kart hangi PKCS#11 mekanizmalarını destekliyor + xades4j default profil çalışır mı" sorusunu
 * yanıtlayan servis.
 *
 * <p>Pure-data: hiç PIN istemez, kart sayacını harcamaz. {@link Pkcs11MechanismProbe}'a delegasyon
 * yapar, sonucu yüksek seviyeli {@link MechanismCapabilityResponse} olarak sarar. {@link
 * SignatureProfileResolver} ile aynı kararları kullanır — frontend'in gördüğü öneri ile gerçek
 * imzalama yolu arasında drift olmaz.
 */
@Service
public class MechanismCapabilityService {

  private static final Logger log = LoggerFactory.getLogger(MechanismCapabilityService.class);

  private final SmartCardManager cardManager;
  private final SmartCardReaderService readerService;

  public MechanismCapabilityService(
      SmartCardManager cardManager, SmartCardReaderService readerService) {
    this.cardManager = cardManager;
    this.readerService = readerService;
  }

  public MechanismCapabilityResponse describe(
      String terminalName, String pkcs11LibraryPath, String cardTypeOverride) {
    Path libraryPath =
        cardManager.resolveLibrary(terminalName, pkcs11LibraryPath, cardTypeOverride);
    log.info(
        "Mekanizma yetenek sorgusu: terminal={}, lib={}, override={}",
        terminalName,
        libraryPath,
        cardTypeOverride);

    SmartCardInfo info =
        terminalName == null ? null : readerService.findByTerminalName(terminalName);

    Pkcs11MechanismProbe.ProbeResult probe = Pkcs11MechanismProbe.probe(libraryPath);

    MechanismCapabilityResponse out = new MechanismCapabilityResponse();
    out.setTerminalName(terminalName);
    out.setPkcs11Library(libraryPath.toString());
    if (info != null) {
      out.setAtr(info.getAtrHex());
      if (info.getCardType() != null) {
        out.setCardType(info.getCardType().getName());
      }
    }
    out.setTokenLabel(probe.getTokenLabel());
    out.setTokenManufacturerId(probe.getTokenManufacturerId());
    out.setTokenModel(probe.getTokenModel());
    out.setTokenFirmwareVersion(probe.getTokenFirmwareVersion());
    out.setTokenHardwareVersion(probe.getTokenHardwareVersion());
    out.setTokenSerialMasked(probe.getTokenSerialMasked());
    out.setMechanismListSupported(probe.isSupported());
    out.setMechanisms(probe.getMechanisms());
    out.setXadesProfile(buildSummary(probe));
    return out;
  }

  /**
   * Mekanizma listesinden xades4j profil özeti üretir. Aynı CKM→URL mapping'i kullanan tek otorite
   * {@link SignatureProfileResolver#chooseAlgorithms}'dır; bu metot sadece prob tipine (RSA/ECDSA)
   * göre seçim yapıp oraya delegasyon yapar — duplicate tablo yok.
   */
  static MechanismCapabilityResponse.XadesProfileSummary buildSummary(
      Pkcs11MechanismProbe.ProbeResult probe) {
    MechanismCapabilityResponse.XadesProfileSummary s =
        new MechanismCapabilityResponse.XadesProfileSummary();
    List<String> warnings = new ArrayList<String>();

    if (!probe.isSupported()) {
      warnings.add(
          "PKCS#11 wrapper bu JDK'da C_GetMechanismList'e izin vermedi; mekanizma listesi"
              + " okunamadı. JDK'yi 1.8.0_181 veya daha yeni bir patch'e güncellemek genellikle"
              + " yeterlidir.");
      s.setWarnings(warnings);
      s.setRecommendedSignatureUrl("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
      s.setRecommendedDigestUrl("http://www.w3.org/2001/04/xmlenc#sha256");
      s.setDefaultProfileWorks(true);
      return s;
    }

    String rsa = probe.firstAvailable(Pkcs11Mechanisms.RSA_SIGN_MECHANISMS);
    String ec = probe.firstAvailable(Pkcs11Mechanisms.ECDSA_SIGN_MECHANISMS);
    s.setPreferredRsaMechanism(rsa);
    s.setPreferredEcdsaMechanism(ec);

    if (rsa == null && ec == null) {
      warnings.add(
          "Token'da RSA veya ECDSA imzalama mekanizması bulunamadı. Kart imzalama için"
              + " kullanılamaz; kart firmware'ini ya da PKCS#11 sürücü sürümünü kontrol edin.");
      s.setRecommendedSignatureUrl(null);
      s.setRecommendedDigestUrl(null);
      s.setDefaultProfileWorks(false);
      s.setFallbackStrategy("none-available");
      s.setWarnings(warnings);
      return s;
    }

    // RSA tercih edilir (Türkiye e-imza ekosisteminin %95'i RSA-2048).
    String chosen = rsa != null ? rsa : ec;
    SignatureProfileResolver.Choice choice = SignatureProfileResolver.chooseAlgorithms(chosen);
    s.setRecommendedSignatureUrl(choice.signatureUrl);
    s.setRecommendedDigestUrl(choice.digestUrl);
    s.setFallbackStrategy(choice.fallbackStrategy);
    s.setDefaultProfileWorks(choice.fallbackStrategy == null);
    if (choice.warnings != null && !choice.warnings.isEmpty()) {
      warnings.addAll(choice.warnings);
    }
    if (!warnings.isEmpty()) {
      s.setWarnings(warnings);
    }
    return s;
  }
}
