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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.SignatureUtil;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;
import io.mersel.dss.agent.api.testsupport.PfxLoader;
import io.mersel.dss.agent.api.testsupport.PfxTestKey;
import io.mersel.dss.agent.api.util.InMemoryMultipartFile;

/**
 * {@link PadesService} davranışını Kamu SM'in publicly published test PFX'leriyle doğrular.
 *
 * <p>Her algoritma için ayrı bir parametrik run koşar:
 *
 * <ul>
 *   <li>{@code KURUM01_RSA2048} → RSA-2048 → SHA-256/RSA imza
 *   <li>{@code KURUM02_EC384} → EC-P384 → SHA-384/ECDSA imza
 * </ul>
 *
 * <p>PFX dosyası repo'da yoksa {@code Assumptions.assumeTrue} ile graceful skip yapılır (CI
 * senaryosunda PFX henüz yerleştirilmemiş durum için).
 */
class PadesServiceTest {

  @ParameterizedTest(name = "[{0}] PAdES signs and verifies")
  @EnumSource(PfxTestKey.class)
  void signsPdfAndProducesValidSignature(PfxTestKey key) throws Exception {
    assumeTrue(key.isAvailable(), "Skip — PFX bulunamadı: " + key.getAbsolutePath());

    PfxLoader.Loaded loaded = PfxLoader.load(key);

    // X.509 serial'i identifier olarak ver (hex upper)
    String serialHex = loaded.certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);

    byte[] pdf = generatePdf("Mersel DSS Agent PAdES — " + key.algorithm());
    SignDocumentDto dto = new SignDocumentDto();
    dto.setTerminalName("test");
    dto.setPin(new String(key.getPassword()));
    dto.setCertificateId(serialHex);
    dto.setContent(new InMemoryMultipartFile("file", "test.pdf", "application/pdf", pdf));

    ByteArrayOutputStream signedOut = new ByteArrayOutputStream();
    PadesService service =
        new PadesService(
            null,
            io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder.passthrough());

    try (Pkcs11Session session = loaded.openSession()) {
      service.signWithSession(session, dto, signedOut);
    }

    byte[] signedPdf = signedOut.toByteArray();
    assertNotNull(signedPdf);
    assertTrue(signedPdf.length > pdf.length, "İmzalı PDF daha büyük olmalı.");

    try (PdfReader reader = new PdfReader(new ByteArrayInputStream(signedPdf));
        PdfDocument signedDoc = new PdfDocument(reader)) {
      SignatureUtil util = new SignatureUtil(signedDoc);
      List<String> names = util.getSignatureNames();
      assertFalse(names.isEmpty(), "En az bir imza alanı olmalı.");
    }
  }

  @Test
  void aliasResolvableByX509Serial() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    String serialHex = loaded.certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);

    try (Pkcs11Session session = loaded.openSession()) {
      // Alias adı direkt
      assertEquals(loaded.alias, session.resolveAlias(loaded.alias));
      // Serial (büyük/küçük harf + 0x prefix)
      assertEquals(loaded.alias, session.resolveAlias(serialHex));
      assertEquals(loaded.alias, session.resolveAlias(serialHex.toLowerCase(Locale.ROOT)));
      assertEquals(loaded.alias, session.resolveAlias("0x" + serialHex));
    }
  }

  @Test
  void digestAlgorithmSelectionRsa() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM01_RSA2048;
    assumeTrue(key.isAvailable());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    assertEquals(DigestAlgorithms.SHA256, PadesService.chooseDigestAlgorithm(loaded.certificate));
  }

  @Test
  void digestAlgorithmSelectionEcdsa() throws Exception {
    PfxTestKey key = PfxTestKey.KURUM02_EC384;
    assumeTrue(key.isAvailable());

    PfxLoader.Loaded loaded = PfxLoader.load(key);
    assertEquals(DigestAlgorithms.SHA384, PadesService.chooseDigestAlgorithm(loaded.certificate));
  }

  /* ---------------- helpers ---------------- */

  private static byte[] generatePdf(String text) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
        Document layout = new Document(pdfDoc)) {
      layout.add(new Paragraph(text));
    }
    return out.toByteArray();
  }
}
