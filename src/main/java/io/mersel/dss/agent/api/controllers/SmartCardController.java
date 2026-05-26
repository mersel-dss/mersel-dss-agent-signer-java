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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.mersel.dss.agent.api.models.CertificateResponse;
import io.mersel.dss.agent.api.models.SmartCardDetail;
import io.mersel.dss.agent.api.models.SmartCardResponse;
import io.mersel.dss.agent.api.models.enums.CertificatePurpose;
import io.mersel.dss.agent.api.services.certificate.CertificateListingService;
import io.mersel.dss.agent.api.services.smartcard.PcscDiagnostics;
import io.mersel.dss.agent.api.services.smartcard.SmartCardInfo;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Akıllı kart algılama ve sertifika listeleme uçları. */
@RestController
@Tag(name = "Akıllı Kart", description = "Sistemdeki kartları ve karttaki sertifikaları listeler.")
public class SmartCardController {

  private final SmartCardReaderService readerService;
  private final CertificateListingService certificateListingService;

  public SmartCardController(
      SmartCardReaderService readerService, CertificateListingService certificateListingService) {
    this.readerService = readerService;
    this.certificateListingService = certificateListingService;
  }

  @Operation(
      summary =
          "Sistemdeki kartları (terminal + ATR + tanınan tip) ve host ortam metadata'sını"
              + " (osName / osVersion / osArch / javaVersion) listeler.")
  @GetMapping("/smartcard")
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
    return ResponseEntity.ok(new SmartCardResponse(details).withCurrentHost());
  }

  @Operation(
      summary =
          "PCSC ortam tanılaması: OS, JDK, yüklü native lib yolu, provider ve her terminalin"
              + " anlık durumu. Kart algılanmadığında ilk başvurulacak uç.")
  @GetMapping("/smartcard/diagnostics")
  public ResponseEntity<PcscDiagnostics> diagnostics() {
    return ResponseEntity.ok(readerService.diagnose());
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
  @GetMapping("/smartcard/certificate")
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

    List<CertificateResponse> certs =
        certificateListingService.listCertificates(terminalName, pkcs11LibraryPath, cardType);
    return ResponseEntity.ok(applyFilters(certs, purposeFilter, eligibleOnly));
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
