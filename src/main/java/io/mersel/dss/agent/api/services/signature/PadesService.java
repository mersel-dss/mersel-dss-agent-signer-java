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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.IExternalDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSignatureAppearance;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;
import io.mersel.dss.agent.api.services.smartcard.SmartCardManager;

/**
 * PDF belgelerini PAdES-B (CADES) ile imzalar. iText 7.2.5 + {@link Pkcs11Session} kullanır.
 *
 * <p>Eski projedeki davranışla uyumlu:
 *
 * <ul>
 *   <li>{@code certificateId} olarak alias <em>veya</em> X.509 serial number (büyük/küçük harf hex)
 *       verilebilir; resolveAlias bunu çözer.
 *   <li>{@code appendMode=true} ise PDF'in mevcut imza alanları korunarak ikinci bir imza eklenir
 *       (incremental update).
 *   <li>Sertifikanın açık anahtar algoritmasına göre RSA-SHA256 veya ECDSA-SHA384 seçilir.
 * </ul>
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

  private final SmartCardManager cardManager;
  private final CertificateChainBuilder chainBuilder;

  public PadesService(SmartCardManager cardManager, CertificateChainBuilder chainBuilder) {
    this.cardManager = cardManager;
    this.chainBuilder = chainBuilder;
  }

  /** Tek-girişli high-level uç: lib çözümler, oturum açar, imzalar, kapatır. */
  public void sign(SignDocumentDto dto, OutputStream signedOut) {
    if (dto == null || dto.getContent() == null) {
      throw new IllegalArgumentException("İmzalanacak içerik boş.");
    }
    Path libraryPath =
        cardManager.resolveLibrary(dto.getTerminalName(), dto.getPkcs11LibraryPath());
    log.info(
        "PAdES imzalama: lib={}, terminal={}, certId={}, appendMode={}",
        libraryPath,
        dto.getTerminalName(),
        dto.getCertificateId(),
        Boolean.TRUE.equals(dto.getAppendMode()));

    try (Pkcs11Session session = Pkcs11Session.open(libraryPath, dto.getPin())) {
      signWithSession(session, dto, signedOut);
    }
  }

  /**
   * Test-friendly orta seviye uç: çağıran kişi {@link Pkcs11Session}'ı kendisi yönetir ({@link
   * Pkcs11Session#wrapForTest} ile software keystore de olabilir).
   */
  public void signWithSession(Pkcs11Session session, SignDocumentDto dto, OutputStream signedOut) {
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
    Certificate[] chain = chainBuilder.build(session.getCertificateChain(alias));

    String digestAlg = chooseDigestAlgorithm((X509Certificate) chain[0]);
    boolean appendMode = Boolean.TRUE.equals(dto.getAppendMode());
    String reason = resolveReason(dto.getReason());
    String location = resolveLocation(dto.getLocation());

    log.debug(
        "PAdES alias={}, digest={}, chainLen={}, appendMode={}, reason='{}', location='{}'",
        alias,
        digestAlg,
        chain.length,
        appendMode,
        reason,
        location);

    try {
      doSign(
          pdfBytes,
          signedOut,
          privateKey,
          chain,
          session.getProvider().getName(),
          digestAlg,
          appendMode,
          reason,
          location);
    } catch (Exception e) {
      throw new SignatureOperationException("PAdES imzalama başarısız: " + e.getMessage(), e);
    }
  }

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
   * Sertifikanın açık anahtar algoritmasına göre uygun digest seçimi.
   *
   * <ul>
   *   <li>EC/ECDSA → SHA-384
   *   <li>RSA → SHA-256
   *   <li>Diğer (DSA, EdDSA, …) → SHA-256 (best effort)
   * </ul>
   */
  static String chooseDigestAlgorithm(X509Certificate cert) {
    String algo = cert.getPublicKey().getAlgorithm();
    if (algo == null) return DigestAlgorithms.SHA256;
    String upper = algo.toUpperCase();
    if (upper.contains("EC") || upper.contains("ECDSA")) {
      return DigestAlgorithms.SHA384;
    }
    return DigestAlgorithms.SHA256;
  }

  private void doSign(
      byte[] pdfBytes,
      OutputStream signedOut,
      PrivateKey privateKey,
      Certificate[] chain,
      String providerName,
      String digestAlg,
      boolean appendMode,
      String reason,
      String location)
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
    appearance.setCertificate(chain[0]);

    signer.setFieldName("Mersel DSS Signer " + System.currentTimeMillis());

    IExternalSignature pks = new PrivateKeySignature(privateKey, digestAlg, providerName);
    IExternalDigest digest = new BouncyCastleDigest();

    signer.signDetached(digest, pks, chain, null, null, null, 0, PdfSigner.CryptoStandard.CADES);
  }
}
