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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.signatures.PdfSignatureAppearance;
import com.itextpdf.signatures.PdfSigner;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder;
import io.mersel.dss.agent.api.services.keystore.BouncyCastleSetup;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;
import io.mersel.dss.agent.api.services.smartcard.SmartCardManager;
import io.mersel.dss.agent.api.services.virtualtoken.VirtualToken;
import io.mersel.dss.agent.api.services.virtualtoken.VirtualTokenRegistry;

/**
 * PDF belgelerini PAdES-B (CADES) ile imzalar. iText 7 (PdfSigner) + manuel CMS (BouncyCastle)
 * üzerinden çalışır; akıllı kart erişimi {@link Pkcs11Session} ile yapılır.
 *
 * <h2>Manuel CMS — neden?</h2>
 *
 * <p>iText 7'nin built-in {@code signDetached(... CryptoStandard.CADES)} yolu RFC 5035 §3'e göre
 * {@code id-aa-signingCertificateV2} attribute'ını kurarken {@code IssuerSerial} alanını
 * <em>OPTIONAL</em> kabul edip yazmıyor. TÜBİTAK / Kamu SM doğrulayıcılar (İmzAGER, MA3, Cybersoft
 * Verifier) ise bu alanı <b>ZORUNLU</b> sayar; eksik yazıldığında
 *
 * <pre>
 *   İmzacı Sertifikası Özelliği V2 Kontrolcüsü → Özellik issuer serial alanı içermiyor
 *   İmza Matematiksel Doğrulama Kontrolcüsü   → İmza matematiksel doğrulanamadı
 * </pre>
 *
 * şeklinde fail döner. Bu yüzden CMS üretimini {@link PadesCmsSigner} sınıfına devrediyoruz — iText
 * 7'nin {@link PdfSigner#signExternalContainer(com.itextpdf.signatures.IExternalSignatureContainer,
 * int)} kontratı ile sadece byte range hesabı + PDF revizyon yazımı iText'te kalır, CMS yapısı
 * tamamen bizim kontrolümüzde olur.
 *
 * <h2>Kontrat</h2>
 *
 * Kullanıcıya bakan davranış değişmez:
 *
 * <ul>
 *   <li>{@code certificateId} alias <em>veya</em> X.509 serial number (büyük/küçük harf hex)
 *       olabilir; resolveAlias bunu çözer.
 *   <li>{@code appendMode=true} ise PDF'in mevcut imza alanları korunarak ikinci bir imza eklenir
 *       (incremental update).
 *   <li>Sertifikanın açık anahtar algoritmasına göre RSA-SHA256 / ECDSA-(SHA256|SHA384|SHA512)
 *       seçilir. CA'nın sertifikayı imzalarken kullandığı algoritma kasıtlı yok sayılır ({@link
 *       PadesCmsSigner.SigAlg#forCertificate} javadoc'una bakın).
 * </ul>
 *
 * <h2>Estimated size</h2>
 *
 * iText 7 default'u (~8192 byte) chain uzun olduğunda yetersiz kalabiliyor. Manuel olarak {@link
 * #estimateContentsSize(int)} ile chain uzunluğuna göre tahmin yapıyoruz: tek end-entity için
 * ~10KB, her ek issuer için ~2KB. CMS placeholder iText tarafından imza çıktısının boyutuna göre
 * tek-shot reserve edilir; tahminin gerçek imzadan büyük olması gerekir.
 */
@Service
public class PadesService {

  private static final Logger log = LoggerFactory.getLogger(PadesService.class);

  /**
   * DTO'da {@code reason} verilmezse PDF'in imza panelinde gösterilecek varsayılan değer. Eski
   * proje davranışıyla geriye uyumludur (orada hardcoded olarak set ediliyordu); artık opsiyonel
   * olarak override edilebilir.
   */
  static final String DEFAULT_REASON = "e-Belge imzalama";

  /** DTO'da {@code location} verilmezse boş string set edilir (eski proje davranışı). */
  static final String DEFAULT_LOCATION = "";

  /**
   * iText {@code estimatedSize} placeholder'ı için baseline. CMS SignedData ortalama büyüklüğü
   * (single signer + 1 cert): ~3 KB; SigningCertificateV2 + signed attrs: ~0.5 KB; +50% güvenlik
   * marjı = ~5 KB end-entity için yeterli. Chain her ek sertifikada ~1.5 KB ekler.
   */
  private static final int BASE_PLACEHOLDER_BYTES = 5 * 1024;

  /** Her ek (intermediate / root) sertifika için tahmini ek byte. */
  private static final int PER_EXTRA_CERT_BYTES = 2 * 1024;

  private final SmartCardManager cardManager;
  private final CertificateChainBuilder chainBuilder;
  private final VirtualTokenRegistry virtualTokenRegistry;

  public PadesService(
      SmartCardManager cardManager,
      CertificateChainBuilder chainBuilder,
      VirtualTokenRegistry virtualTokenRegistry) {
    this.cardManager = cardManager;
    this.chainBuilder = chainBuilder;
    this.virtualTokenRegistry = virtualTokenRegistry;
  }

  /** Tek-girişli high-level uç: lib çözümler, oturum açar, imzalar, kapatır. */
  public void sign(SignDocumentDto dto, OutputStream signedOut) {
    if (dto == null || dto.getContent() == null) {
      throw new IllegalArgumentException("İmzalanacak içerik boş.");
    }

    // Sanal PKCS#12 (PFX) kartı: yazılım keystore üzerinden imzala (PIN yerine kayıtlı parola).
    VirtualToken virtual = virtualTokenRegistry.find(dto.getTerminalName());
    if (virtual != null && virtual.isPkcs12()) {
      log.info(
          "PAdES imzalama (sanal PFX kartı): terminal={}, certId={}, appendMode={}",
          dto.getTerminalName(),
          dto.getCertificateId(),
          Boolean.TRUE.equals(dto.getAppendMode()));
      try (Pkcs11Session session =
          Pkcs11Session.forPkcs12(virtual.getKeyStore(), virtual.passwordString())) {
        signWithSession(session, dto, signedOut);
      }
      return;
    }

    // Fiziksel kart veya sanal PKCS#11 (lib yolu resolver tarafından registry'den çözülür).
    Path libraryPath =
        cardManager.resolveLibrary(dto.getTerminalName(), dto.getPkcs11LibraryPath());
    log.info(
        "PAdES imzalama: lib={}, terminal={}, certId={}, appendMode={}",
        libraryPath,
        dto.getTerminalName(),
        dto.getCertificateId(),
        Boolean.TRUE.equals(dto.getAppendMode()));

    try (Pkcs11Session session =
        Pkcs11Session.open(libraryPath, dto.getPin(), dto.getTerminalName())) {
      signWithSession(session, dto, signedOut);
    }
  }

  /**
   * Test-friendly orta seviye uç: çağıran kişi {@link Pkcs11Session}'ı kendisi yönetir ({@link
   * Pkcs11Session#wrapForTest} ile software keystore de olabilir).
   */
  public void signWithSession(Pkcs11Session session, SignDocumentDto dto, OutputStream signedOut) {
    // BC zaten Pkcs11Session.open içinde register ediliyor; software wrapForTest yolunda
    // explicit register gerekiyor (bazı testlerde session.open by-pass ediliyor).
    BouncyCastleSetup.ensureRegistered();

    byte[] pdfBytes;
    try {
      pdfBytes = dto.getContent().getBytes();
    } catch (IOException e) {
      throw new SignatureOperationException("PDF içeriği okunamadı: " + e.getMessage(), e);
    }
    if (pdfBytes.length == 0) {
      throw new IllegalArgumentException("İmzalanacak PDF boş.");
    }

    String alias = session.resolveAlias(dto.getCertificateId());
    PrivateKey privateKey = session.getPrivateKey(alias);
    Certificate[] rawChain = chainBuilder.build(session.getCertificateChain(alias));
    X509Certificate[] x509Chain = toX509(rawChain);

    PadesCmsSigner.SigAlg sigAlg = PadesCmsSigner.SigAlg.forCertificate(x509Chain[0]);
    boolean appendMode = Boolean.TRUE.equals(dto.getAppendMode());
    String reason = resolveReason(dto.getReason());
    String location = resolveLocation(dto.getLocation());
    int estimatedSize = estimateContentsSize(x509Chain.length);

    log.debug(
        "PAdES alias={}, sigAlg={}, chainLen={}, appendMode={}, reason='{}', location='{}',"
            + " estimatedSize={}",
        alias,
        sigAlg,
        x509Chain.length,
        appendMode,
        reason,
        location,
        estimatedSize);

    try {
      doSign(
          pdfBytes,
          signedOut,
          new PadesCmsSigner(privateKey, x509Chain, session.getProvider().getName(), sigAlg),
          x509Chain[0],
          reason,
          location,
          appendMode,
          estimatedSize);
    } catch (Exception e) {
      throw new SignatureOperationException("PAdES imzalama başarısız: " + e.getMessage(), e);
    }
  }

  /**
   * iText 7 {@link PdfSigner#signExternalContainer} ile imzalama akışı.
   *
   * <p>{@code signExternalContainer} CMS bytes'ı {@code PadesCmsSigner.sign(InputStream)}'den tek
   * seferde alır ve PDF'in imza dictionary'sinin {@code /Contents} alanına gömer. Byte range,
   * placeholder size'ı düşülerek hesaplanır. {@link PadesCmsSigner#modifySigningDictionary}
   * Filter/SubFilter alanlarını set eder.
   */
  private void doSign(
      byte[] pdfBytes,
      OutputStream signedOut,
      PadesCmsSigner cmsSigner,
      X509Certificate signingCert,
      String reason,
      String location,
      boolean appendMode,
      int estimatedSize)
      throws Exception {
    PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
    StampingProperties stampingProps = new StampingProperties();
    if (appendMode) {
      stampingProps.useAppendMode();
    }
    PdfSigner signer = new PdfSigner(reader, signedOut, stampingProps);

    PdfSignatureAppearance appearance = signer.getSignatureAppearance();
    appearance.setReason(reason);
    appearance.setLocation(location);
    appearance.setCertificate(signingCert);

    signer.setSignDate(Calendar.getInstance());
    signer.setFieldName("Signature-" + System.currentTimeMillis());

    signer.signExternalContainer(cmsSigner, estimatedSize);
  }

  /* ============================================================== */
  /* Helpers                                                          */
  /* ============================================================== */

  /**
   * DTO {@code reason} alanı için trim + null/empty → default fallback. Görsel olarak PDF imza
   * panelinde gösterileceği için kullanıcının bilinçli "boş bırakmak" niyeti default ile aynı
   * sonuca varır; ham boş string set edilmez (PDF reader'lar bunu "(unspecified)" yerine boş
   * gösterebilir, UX kirli olur).
   */
  static String resolveReason(String dtoValue) {
    if (dtoValue == null) {
      return DEFAULT_REASON;
    }
    String trimmed = dtoValue.trim();
    return trimmed.isEmpty() ? DEFAULT_REASON : trimmed;
  }

  /**
   * DTO {@code location} alanı için trim — null veya boş string aynı şekilde ele alınır ve PDF'e
   * boş location yazılır (eski projedeki davranış).
   */
  static String resolveLocation(String dtoValue) {
    if (dtoValue == null) {
      return DEFAULT_LOCATION;
    }
    return dtoValue.trim();
  }

  /**
   * Chain uzunluğuna göre PDF imza placeholder boyutu tahmini.
   *
   * <p>iText 7 placeholder'ı tek-shot reserve eder; gerçek imza boyutundan küçük olamaz, aksi halde
   * {@code IOException("not enough space")} fırlar. Uzun zincir (Kamu SM 3-4 sertifika) için BASE +
   * N*EXTRA marjı bırakırız.
   */
  static int estimateContentsSize(int chainLength) {
    int extra = Math.max(0, chainLength - 1) * PER_EXTRA_CERT_BYTES;
    return BASE_PLACEHOLDER_BYTES + extra;
  }

  /**
   * AIA chain builder {@link Certificate}[] döner; manuel CMS path'i {@link X509Certificate}[]
   * bekler. Non-X509 elementler (ör. self-signed root içinde olmayacak ama defensif) atlanır; sıra
   * korunur (end-entity → issuer → root).
   */
  private static X509Certificate[] toX509(Certificate[] in) {
    if (in == null || in.length == 0) {
      throw new IllegalArgumentException("certificate chain boş");
    }
    List<X509Certificate> list = new ArrayList<>(in.length);
    for (Certificate c : in) {
      if (c instanceof X509Certificate) {
        list.add((X509Certificate) c);
      }
    }
    if (list.isEmpty()) {
      throw new IllegalArgumentException("certificate chain X.509 sertifika içermiyor");
    }
    return list.toArray(new X509Certificate[0]);
  }
}
