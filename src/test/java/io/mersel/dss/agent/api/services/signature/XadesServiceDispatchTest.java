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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStoreException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import io.mersel.dss.agent.api.dtos.SignDocumentDto;
import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.models.SignatureDiagnostics;
import io.mersel.dss.agent.api.services.certificate.CertificateChainBuilder;
import io.mersel.dss.agent.api.services.smartcard.SmartCardManager;
import io.mersel.dss.agent.api.services.smartcard.SmartCardReaderService;

import xades4j.verification.UnexpectedJCAException;

/**
 * {@link XadesService#signXmlDocument} dispatch logic'i için kanıt testi: xades4j path SunPKCS11
 * dual-key CKA_ID çakışmasıyla patladığında akış native fallback'e dönmeli.
 *
 * <p>Sahada token kullanılmadan bu davranışı test etmek için {@code doXadesBesSign} ve {@code
 * doXadesBesSignNative} metotları Mockito spy üzerinden override edilir; çağrı sayıları doğrulanır.
 */
class XadesServiceDispatchTest {

  private SignDocumentDto dto() {
    SignDocumentDto dto = new SignDocumentDto();
    MultipartFile file =
        new MockMultipartFile(
            "content", "doc.xml", "text/xml", "<r/>".getBytes(StandardCharsets.UTF_8));
    dto.setContent(file);
    dto.setTerminalName("Feitian SCR301");
    dto.setPin("1234");
    dto.setCertificateId("a2c3dfb6572a06");
    return dto;
  }

  private XadesService newSpyService(Path libPath) {
    SmartCardManager cm = Mockito.mock(SmartCardManager.class);
    Mockito.when(cm.resolveLibrary(Mockito.anyString(), Mockito.any())).thenReturn(libPath);
    Mockito.when(cm.resolveLibrary(Mockito.anyString(), Mockito.isNull())).thenReturn(libPath);
    CertificateChainBuilder cb = Mockito.mock(CertificateChainBuilder.class);
    SmartCardReaderService rs = Mockito.mock(SmartCardReaderService.class);
    return Mockito.spy(
        new XadesService(
            cm, cb, rs, new io.mersel.dss.agent.api.services.virtualtoken.VirtualTokenRegistry()));
  }

  @Test
  void duplicateCkaIdFallsBackToNativePath() throws Exception {
    Path libPath = Paths.get("/tmp/dummy-libakisp11.dylib");
    XadesService svc = newSpyService(libPath);

    KeyStoreException ks =
        new KeyStoreException(
            "invalid KeyStore state: found 2 private keys sharing CKA_ID"
                + " 0x5dba00cc74e842ccc17d4154fdf1062824d73419");
    UnexpectedJCAException xades4jFail = new UnexpectedJCAException(ks.getMessage(), ks);

    // 1) xades4j path patla
    Mockito.doThrow(xades4jFail)
        .when(svc)
        .doXadesBesSign(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.eq("a2c3dfb6572a06"),
            Mockito.eq("1234"),
            Mockito.any(SignatureDiagnostics.class));
    // 2) native path başarılı dön
    byte[] nativeOutput = "<r><Signature/></r>".getBytes(StandardCharsets.UTF_8);
    Mockito.doReturn(nativeOutput)
        .when(svc)
        .doXadesBesSignNative(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.eq("a2c3dfb6572a06"),
            Mockito.eq("1234"),
            Mockito.any(SignatureDiagnostics.class));

    byte[] result = svc.signXmlDocument(dto());
    assertThat(result).isEqualTo(nativeOutput);

    // Çağrı sayısı kanıtı: her iki yol da çağrıldı, sırasıyla.
    Mockito.verify(svc, Mockito.times(1))
        .doXadesBesSign(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));
    Mockito.verify(svc, Mockito.times(1))
        .doXadesBesSignNative(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));
  }

  @Test
  void unrelatedFailureDoesNotTriggerNativeFallback() throws Exception {
    Path libPath = Paths.get("/tmp/dummy-libakisp11.dylib");
    XadesService svc = newSpyService(libPath);

    // CKA_ID dışında, kart hatası — native'e DÜŞMEMELİ.
    RuntimeException pinFail = new RuntimeException("CKR_PIN_INCORRECT (0x000000A0)");
    Mockito.doThrow(pinFail)
        .when(svc)
        .doXadesBesSign(
            Mockito.any(byte[].class),
            Mockito.any(Path.class),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));

    assertThatThrownBy(() -> svc.signXmlDocument(dto()))
        .isInstanceOf(SignatureOperationException.class);

    Mockito.verify(svc, Mockito.never())
        .doXadesBesSignNative(
            Mockito.any(byte[].class),
            Mockito.any(Path.class),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));
  }

  @Test
  void nativeFallbackAlsoFailsSuppressesOriginal() throws Exception {
    Path libPath = Paths.get("/tmp/dummy-libakisp11.dylib");
    XadesService svc = newSpyService(libPath);

    KeyStoreException ks =
        new KeyStoreException(
            "invalid KeyStore state: found 2 private keys sharing CKA_ID 0xdeadbeef");
    Mockito.doThrow(new UnexpectedJCAException(ks.getMessage(), ks))
        .when(svc)
        .doXadesBesSign(
            Mockito.any(byte[].class),
            Mockito.any(Path.class),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));
    RuntimeException nativeFail = new RuntimeException("C_Sign başarısız: CKR_DATA_LEN_RANGE");
    Mockito.doThrow(nativeFail)
        .when(svc)
        .doXadesBesSignNative(
            Mockito.any(byte[].class),
            Mockito.any(Path.class),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));

    // Hem orijinal hem native failure raporlanmalı; native cause primary, original suppressed.
    ArgumentCaptor<byte[]> bytesCap = ArgumentCaptor.forClass(byte[].class);
    assertThatThrownBy(() -> svc.signXmlDocument(dto()))
        .isInstanceOf(SignatureOperationException.class)
        .hasMessageContaining("native fallback de patladı");

    Mockito.verify(svc, Mockito.times(1))
        .doXadesBesSignNative(
            bytesCap.capture(),
            Mockito.any(Path.class),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));
  }

  @Test
  void counterSignatureFallsBackToNativeOnCkaIdCollision() throws Exception {
    Path libPath = Paths.get("/tmp/dummy-libakisp11.dylib");
    XadesService svc = newSpyService(libPath);

    // SunPKCS11 counter-sig yolu CKA_ID collision ile patlar (dual-key SIGN0+SIGN1).
    SignatureOperationException sunFail =
        new SignatureOperationException(
            SignatureOperationException.CODE_FAILED,
            "XAdES CounterSignature başarısız: invalid KeyStore state: found 2 private keys"
                + " sharing CKA_ID 0xdeadbeef",
            new java.security.KeyStoreException("found 2 private keys sharing CKA_ID 0xdeadbeef"));
    Mockito.doThrow(sunFail)
        .when(svc)
        .signHrCounterSignatureViaSunPkcs11(
            Mockito.eq(libPath),
            Mockito.any(SignDocumentDto.class),
            Mockito.any(SignatureDiagnostics.class));

    byte[] nativeOutput = "<doc><Signature/></doc>".getBytes(StandardCharsets.UTF_8);
    Mockito.doReturn(nativeOutput)
        .when(svc)
        .doCounterSignatureNative(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.eq("a2c3dfb6572a06"),
            Mockito.eq("1234"),
            Mockito.any(SignatureDiagnostics.class));

    byte[] result = svc.signHrXmlCounterSignature(dto());
    assertThat(result).isEqualTo(nativeOutput);

    Mockito.verify(svc, Mockito.times(1))
        .doCounterSignatureNative(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));
  }

  @Test
  void counterSignatureFallsBackToNativeOnFunctionNotSupported() throws Exception {
    Path libPath = Paths.get("/tmp/dummy-libakisp11.dylib");
    XadesService svc = newSpyService(libPath);

    // Raw-only firmware: SunPKCS11 multi-part C_SignUpdate → CKR_FUNCTION_NOT_SUPPORTED.
    SignatureOperationException sunFail =
        new SignatureOperationException(
            SignatureOperationException.CODE_ALGORITHM_UNSUPPORTED,
            "XAdES CounterSignature başarısız: update() failed | root: CKR_FUNCTION_NOT_SUPPORTED",
            new RuntimeException("CKR_FUNCTION_NOT_SUPPORTED"));
    Mockito.doThrow(sunFail)
        .when(svc)
        .signHrCounterSignatureViaSunPkcs11(
            Mockito.eq(libPath),
            Mockito.any(SignDocumentDto.class),
            Mockito.any(SignatureDiagnostics.class));

    byte[] nativeOutput = "<doc><Signature/></doc>".getBytes(StandardCharsets.UTF_8);
    Mockito.doReturn(nativeOutput)
        .when(svc)
        .doCounterSignatureNative(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));

    assertThat(svc.signHrXmlCounterSignature(dto())).isEqualTo(nativeOutput);

    Mockito.verify(svc, Mockito.times(1))
        .doCounterSignatureNative(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));
  }

  @Test
  void counterSignatureFallsBackToNativeOnAttributeSensitive() throws Exception {
    Path libPath = Paths.get("/tmp/dummy-libakisp11.dylib");
    XadesService svc = newSpyService(libPath);

    // Sensitive EC anahtar: SunPKCS11 JSR-105 ECDSA yolu hassas attribute okumaya kalkışır →
    // ProviderException sarılı CKR_ATTRIBUTE_SENSITIVE (AKİS akisp11 EC kart, sahada görülen
    // trace).
    SignatureOperationException sunFail =
        new SignatureOperationException(
            SignatureOperationException.CODE_FAILED,
            "XAdES CounterSignature başarısız: sun.security.pkcs11.wrapper.PKCS11Exception:"
                + " CKR_ATTRIBUTE_SENSITIVE | root: CKR_ATTRIBUTE_SENSITIVE",
            new java.security.ProviderException(
                "sun.security.pkcs11.wrapper.PKCS11Exception: CKR_ATTRIBUTE_SENSITIVE"));
    Mockito.doThrow(sunFail)
        .when(svc)
        .signHrCounterSignatureViaSunPkcs11(
            Mockito.eq(libPath),
            Mockito.any(SignDocumentDto.class),
            Mockito.any(SignatureDiagnostics.class));

    byte[] nativeOutput = "<doc><Signature/></doc>".getBytes(StandardCharsets.UTF_8);
    Mockito.doReturn(nativeOutput)
        .when(svc)
        .doCounterSignatureNative(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));

    assertThat(svc.signHrXmlCounterSignature(dto())).isEqualTo(nativeOutput);

    Mockito.verify(svc, Mockito.times(1))
        .doCounterSignatureNative(
            Mockito.any(byte[].class),
            Mockito.eq(libPath),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));
  }

  @Test
  void counterSignatureUnrelatedFailureDoesNotFallback() throws Exception {
    Path libPath = Paths.get("/tmp/dummy-libakisp11.dylib");
    XadesService svc = newSpyService(libPath);

    // PIN hatası — native'e DÜŞMEMELİ, kullanıcıya olduğu gibi raporlanmalı.
    SignatureOperationException sunFail =
        new SignatureOperationException(
            SignatureOperationException.CODE_FAILED,
            "XAdES CounterSignature başarısız: CKR_PIN_INCORRECT (0x000000A0)",
            new RuntimeException("CKR_PIN_INCORRECT"));
    Mockito.doThrow(sunFail)
        .when(svc)
        .signHrCounterSignatureViaSunPkcs11(
            Mockito.eq(libPath),
            Mockito.any(SignDocumentDto.class),
            Mockito.any(SignatureDiagnostics.class));

    assertThatThrownBy(() -> svc.signHrXmlCounterSignature(dto()))
        .isInstanceOf(SignatureOperationException.class);

    Mockito.verify(svc, Mockito.never())
        .doCounterSignatureNative(
            Mockito.any(byte[].class),
            Mockito.any(Path.class),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(SignatureDiagnostics.class));
  }
}
