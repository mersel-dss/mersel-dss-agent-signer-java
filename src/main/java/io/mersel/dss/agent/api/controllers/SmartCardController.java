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
package io.mersel.dss.agent.api.controllers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.mersel.dss.agent.api.dtos.ValidatePinDto;
import io.mersel.dss.agent.api.models.CertificateResponse;
import io.mersel.dss.agent.api.models.PinValidationResponse;
import io.mersel.dss.agent.api.models.SmartCardDetail;
import io.mersel.dss.agent.api.models.SmartCardResponse;
import io.mersel.dss.agent.api.models.enums.CertificatePurpose;
import io.mersel.dss.agent.api.services.certificate.CertificateListingService;
import io.mersel.dss.agent.api.services.signature.MechanismCapabilityResponse;
import io.mersel.dss.agent.api.services.signature.MechanismCapabilityService;
import io.mersel.dss.agent.api.services.smartcard.PcscDiagnostics;
import io.mersel.dss.agent.api.services.smartcard.SmartCardInfo;
import io.mersel.dss.agent.api.services.smartcard.SmartCardPinValidator;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;
import io.mersel.dss.agent.api.services.virtualtoken.VirtualToken;
import io.mersel.dss.agent.api.services.virtualtoken.VirtualTokenRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Akıllı kart algılama ve sertifika listeleme uçları. */
@RestController
@Tag(name = "Akıllı Kart", description = "Sistemdeki kartları ve karttaki sertifikaları listeler.")
public class SmartCardController {

  private final SmartCardReaderService readerService;
  private final CertificateListingService certificateListingService;
  private final SmartCardPinValidator pinValidator;
  private final MechanismCapabilityService mechanismCapabilityService;
  private final VirtualTokenRegistry virtualTokenRegistry;

  @org.springframework.beans.factory.annotation.Autowired
  public SmartCardController(
      SmartCardReaderService readerService,
      CertificateListingService certificateListingService,
      SmartCardPinValidator pinValidator,
      MechanismCapabilityService mechanismCapabilityService,
      VirtualTokenRegistry virtualTokenRegistry) {
    this.readerService = readerService;
    this.certificateListingService = certificateListingService;
    this.pinValidator = pinValidator;
    this.mechanismCapabilityService = mechanismCapabilityService;
    this.virtualTokenRegistry = virtualTokenRegistry;
  }

  @Operation(
      summary =
          "Sistemdeki kartları (terminal + ATR + tanınan tip) ve host ortam metadata'sını"
              + " (osName / osVersion / osArch / javaVersion) listeler.")
  @GetMapping(value = "/smartcard", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SmartCardResponse> listCards() {
    List<SmartCardInfo> infos = readerService.listCardsWithMeta();
    List<SmartCardDetail> details = new ArrayList<SmartCardDetail>();
    for (SmartCardInfo info : infos) {
      SmartCardDetail d = new SmartCardDetail();
      d.setTerminalName(info.getTerminalName());
      d.setAtr(info.getAtrHex());
      if (info.getCardType() != null) {
        d.setCardType(info.getCardType().getName());
        if (!info.getCardType().getLibraries().isEmpty()) {
          d.setPkcs11Library(info.getCardType().getLibraries().get(0));
        }
      }
      details.add(d);
    }
    // Fiziksel kartların ardına kullanıcının tanımladığı sanal kartları (Dummy Card) ekle.
    for (VirtualToken token : virtualTokenRegistry.list()) {
      SmartCardDetail d = new SmartCardDetail();
      d.setTerminalName(token.getName());
      d.setCardType(token.getDisplayCardType());
      d.setVirtual(true);
      d.setSource(token.getSourceType());
      if (token.isPkcs11()) {
        d.setPkcs11LibraryPath(token.getPkcs11LibraryPath());
      }
      details.add(d);
    }
    return ResponseEntity.ok(new SmartCardResponse(details).withCurrentHost());
  }

  @Operation(
      summary =
          "PCSC ortam tanılaması: OS, JDK, yüklü native lib yolu, provider ve her terminalin"
              + " anlık durumu. Kart algılanmadığında ilk başvurulacak uç.")
  @GetMapping(value = "/smartcard/diagnostics", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PcscDiagnostics> diagnostics() {
    return ResponseEntity.ok(readerService.diagnose());
  }

  @Operation(
      summary =
          "Belirtilen terminalin token'ı için PKCS#11 mekanizma listesi (CKM_*) ve XAdES uyumluluk"
              + " özeti. PIN gerektirmez.",
      description =
          "Token'ın `C_GetMechanismList` çıktısını sembolik formda (`CKM_SHA256_RSA_PKCS`,"
              + " `CKM_ECDSA_SHA384`, ...) döner. Aynı zamanda RSA ve ECDSA için ilk tercih"
              + " edilebilir mekanizmayı, fallback gerekip gerekmediğini ve uyarıları içeren"
              + " `xadesProfile` özetini hesaplar.\n\n"
              + "**Ne zaman kullanılır?**\n\n"
              + "1. Frontend kullanıcı kart taktıktan sonra imzalama akışından önce çağırarak"
              + " 'kartınız XAdES-BES için uygun' / 'firmware'iniz RSA-PSS desteklemiyor' gibi"
              + " ön-uyarı verebilir.\n"
              + "2. Bir kart 'Unsupported parameters' hatasıyla başarısız olduğunda destek bu uçla"
              + " gerçek mekanizma listesini görür ve fix önerir.\n\n"
              + "**Güvenlik**: PIN harcamaz, kart sayacını etkilemez (PKCS#11 spec §10.4 — public"
              + " session).")
  @GetMapping(value = "/smartcard/mechanisms", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MechanismCapabilityResponse> mechanisms(
      @Parameter(description = "PCSC terminal adı.", required = true)
          @NotBlank
          @RequestParam("terminalName")
          String terminalName,
      @Parameter(
              description =
                  "İsteğe bağlı PKCS#11 paylaşımlı kütüphane yolu (bare ad veya tam path).")
          @RequestParam(value = "pkcs11LibraryPath", required = false)
          String pkcs11LibraryPath,
      @Parameter(
              description =
                  "Layer 5 fallback: ATR algılaması başarısızken kullanıcının manuel seçtiği kart"
                      + " tipi (örn. AKIS, ALADDIN).")
          @RequestParam(value = "cardType", required = false)
          String cardType) {
    return ResponseEntity.ok(
        mechanismCapabilityService.describe(terminalName, pkcs11LibraryPath, cardType));
  }

  @Operation(
      summary = "Belirtilen terminaldeki karttan sertifikaları listeler (PIN gerektirmez).",
      description =
          "PKCS#11 spec'i gereği sertifika objeleri public (CKA_PRIVATE=FALSE) olduğundan bu uç"
              + " **C_Login yapmaz** — PIN istemez, PIN sayacını harcamaz, kart kilitlenme riski"
              + " yaratmaz. PIN yalnız imzalama uçlarında ({@code /pades/sign}, {@code"
              + " /xades/sign}) gereklidir.\n\n"
              + "Her sertifika için KeyUsage / ExtendedKeyUsage / CertificatePolicies /"
              + " QCStatements parse edilir; iş amacı (purpose) ve imzaya uygunluk"
              + " (eligibleForSignature) hesaplanır. Liste seviyesinde en uygun SIGNING"
              + " sertifikası `recommended=true` ile işaretlenir — frontend bu sertifikayı"
              + " pre-select etmelidir.\n\n"
              + "**`purpose` filtresi**: `SIGNING` → sadece imza için uygun cert'leri döner (UX"
              + " için temiz liste). `ENCRYPTION` / `AUTHENTICATION` / `MIXED` / `OTHER` → ilgili"
              + " kategori. `ALL` veya boş → tüm cert'ler (audit / debug).\n\n"
              + "**`eligibleOnly` (default: `true`)**: `eligibleForSignature=true` filtresi"
              + " (geçerli + imzaya uygun). Son kullanıcı seçim ekranı için varsayılan davranış"
              + " budur; audit / debug amaçlı tüm cert'leri görmek için `?eligibleOnly=false`"
              + " geçin. `purpose` ile AND ile birleşir.")
  @GetMapping(value = "/smartcard/certificate", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CertificateResponse>> listCertificates(
      @Parameter(description = "PCSC terminal adı.", required = true)
          @NotBlank
          @RequestParam("terminalName")
          String terminalName,
      @Parameter(
              description =
                  "İsteğe bağlı PKCS#11 paylaşımlı kütüphane yolu (bare ad veya tam path).")
          @RequestParam(value = "pkcs11LibraryPath", required = false)
          String pkcs11LibraryPath,
      @Parameter(
              description =
                  "Layer 5 fallback: ATR algılaması başarısızken kullanıcının manuel seçtiği kart"
                      + " tipi (örn. AKIS, ALADDIN).")
          @RequestParam(value = "cardType", required = false)
          String cardType,
      @Parameter(
              description =
                  "İş amacı filtresi: SIGNING / ENCRYPTION / AUTHENTICATION / MIXED / OTHER / ALL"
                      + " (default ALL). Case-insensitive.")
          @RequestParam(value = "purpose", required = false)
          String purposeFilter,
      @Parameter(
              description =
                  "Yalnız `eligibleForSignature=true` olan sertifikaları döner. **Default: true**"
                      + " — son kullanıcı seçim ekranı için temiz liste. Audit / debug amaçlı"
                      + " geçersiz / süresi dolmuş cert'leri de görmek için `false` geçin.")
          @RequestParam(value = "eligibleOnly", required = false, defaultValue = "true")
          boolean eligibleOnly) {

    VirtualToken virtual = virtualTokenRegistry.find(terminalName);
    List<CertificateResponse> certs =
        virtual != null
            ? certificateListingService.listFromVirtual(virtual)
            : certificateListingService.listCertificates(terminalName, pkcs11LibraryPath, cardType);
    return ResponseEntity.ok(applyFilters(certs, purposeFilter, eligibleOnly));
  }

  @Operation(
      summary = "Seçilen kart üzerinde PIN'i C_Login ile doğrular (imzalama yapılmaz).",
      description =
          "Frontend giriş ekranı için ucuz bir PIN doğrulama uçu. {@code C_Login} tetiklenir,"
              + " başarılı ise oturum derhal kapatılır (sertifika / private key okunmaz, dosya"
              + " yüklenmez). Frontend büyük PDF'leri yüklemeden önce PIN'in doğru olup olmadığını"
              + " bu uçla bilebilir.\n\n"
              + "**Sözleşme**:\n\n"
              + "- 200 + `{ valid: true, ... }` → PIN doğru.\n"
              + "- 401 + `{ code: \"PKCS11_AUTH_FAILED\", ... }` → PIN yanlış (standart"
              + " `ErrorModel`).\n"
              + "- 503 + `{ code: \"PKCS11_LIBRARY_NOT_FOUND\", ... }` → kart sürücüsü bulunamadı"
              + " ya da kart algılanamadı (Layer 5 fallback).\n\n"
              + "**Güvenlik uyarısı**: Her yanlış deneme kartın PIN sayacını harcar. KamuSM"
              + " kartlarında tipik 3 yanlış denemeden sonra PIN kilitlenir ve PUK ile reset"
              + " gerekir. Bu yüzden frontend ASLA otomatik retry yapmamalı, kullanıcıya açık"
              + " uyarı göstermelidir. PIN URL/query üzerinden değil her zaman JSON body ile"
              + " (HTTPS / loopback) gönderilmelidir.")
  @PostMapping(
      value = "/smartcard/pin/validate",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PinValidationResponse> validatePin(
      @Valid @RequestBody ValidatePinDto body) {
    // PKCS#12 (PFX) sanal kartta C_Login/PIN kavramı yoktur; parola kart tanımı sırasında
    // doğrulanıp bellekte tutulur. Bu yüzden PIN doğrulama anında geçerli kabul edilir
    // (frontend giriş akışı bozulmasın). PKCS#11 sanal kart ise gerçek sürücüye sahip olduğundan
    // normal C_Login yolundan geçer (resolveLibrary registry-aware).
    VirtualToken virtual = virtualTokenRegistry.find(body.getTerminalName());
    if (virtual != null && virtual.isPkcs12()) {
      return ResponseEntity.ok(
          new PinValidationResponse(
              true, body.getTerminalName(), virtual.getDisplayCardType(), null));
    }
    SmartCardPinValidator.ValidationResult result =
        pinValidator.validate(
            body.getTerminalName(), body.getPin(), body.getPkcs11LibraryPath(), body.getCardType());
    Path lib = result.getPkcs11LibraryPath();
    return ResponseEntity.ok(
        new PinValidationResponse(
            true,
            body.getTerminalName(),
            result.getCardType(),
            lib != null ? lib.toString() : null));
  }

  /**
   * `?purpose=...` ve `?eligibleOnly=true` query parametrelerini uygular. {@code recommended}
   * bayrağı filtre öncesi atandığı için, filtreden geçen liste hâlâ doğru pre-select hedefini taşır
   * (filtre {@code recommended=true} olanı eleyebilir; bu durumda frontend ilk SIGNING cert'i
   * seçer).
   */
  static List<CertificateResponse> applyFilters(
      List<CertificateResponse> source, String purposeFilter, boolean eligibleOnly) {
    if (source == null || source.isEmpty()) {
      return source;
    }
    CertificatePurpose target = parsePurpose(purposeFilter);
    if (target == null && !eligibleOnly) {
      return source;
    }
    List<CertificateResponse> out = new ArrayList<CertificateResponse>(source.size());
    for (CertificateResponse cr : source) {
      if (eligibleOnly && !cr.isEligibleForSignature()) {
        continue;
      }
      if (target != null && cr.getPurpose() != target) {
        continue;
      }
      out.add(cr);
    }
    return out;
  }

  private static CertificatePurpose parsePurpose(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.isEmpty() || "ALL".equalsIgnoreCase(s)) {
      return null;
    }
    try {
      return CertificatePurpose.valueOf(s.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
