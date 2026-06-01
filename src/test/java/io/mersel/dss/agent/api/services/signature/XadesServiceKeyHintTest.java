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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

import org.apache.xml.security.algorithms.JCEMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link XadesService#resolveKeyHint(X509Certificate)} ve {@link
 * org.apache.xml.security.algorithms.JCEMapper#setProviderId(String)} bridge davranışı için kanıt
 * testleri.
 *
 * <p>Bu testler iki yapısal kararı kilitler:
 *
 * <ul>
 *   <li><b>keyHint resolution</b>: Sertifika public key tipine göre {@link
 *       SignatureProfileResolver}'a verilecek keyHint string'i RSA mı yoksa EC mi olmalı? Resolver
 *       kart mekanizmalarını bu hint'e göre RSA-PKCS / RSA-PSS / ECDSA branch'ine sokar.
 *   <li><b>JCEMapper provider bridge</b>: Apache Santuario'nun {@code JCEMapper.providerId} global
 *       static alanı set edilebilir, get edilebilir ve null'a geri alınabilir. Bu alan, opaque
 *       PKCS#11 private key'lerin (CKA_SENSITIVE=true, modulus/exponent extractable değil) BC'nin
 *       {@code instanceof RSAPrivateKey} kontrolünü bypass etmesini sağlar — xmlsec
 *       Signature.getInstance'ı SunPKCS11 provider'ına yönlendirir.
 * </ul>
 */
class XadesServiceKeyHintTest {

  private String previousProviderId;

  @BeforeEach
  void capturePreviousProviderId() {
    previousProviderId = JCEMapper.getProviderId();
  }

  @AfterEach
  void restorePreviousProviderId() {
    JCEMapper.setProviderId(previousProviderId);
  }

  @Test
  void rsaPublicKey_yieldsRsaHint() {
    X509Certificate cert = mock(X509Certificate.class);
    RSAPublicKey pub = mock(RSAPublicKey.class);
    when(pub.getAlgorithm()).thenReturn("RSA");
    when(pub.getModulus()).thenReturn(BigInteger.valueOf(0x10001L));
    when(cert.getPublicKey()).thenReturn(pub);

    assertThat(XadesService.resolveKeyHint(cert)).isEqualTo("RSA");
  }

  @Test
  void ecPublicKey_yieldsEcHint() {
    X509Certificate cert = mock(X509Certificate.class);
    ECPublicKey pub = mock(ECPublicKey.class);
    when(pub.getAlgorithm()).thenReturn("EC");
    when(cert.getPublicKey()).thenReturn(pub);

    assertThat(XadesService.resolveKeyHint(cert)).isEqualTo("EC");
  }

  @Test
  void ecdsaAlgorithmName_yieldsEcHint() {
    X509Certificate cert = mock(X509Certificate.class);
    ECPublicKey pub = mock(ECPublicKey.class);
    when(pub.getAlgorithm()).thenReturn("ECDSA");
    when(cert.getPublicKey()).thenReturn(pub);

    assertThat(XadesService.resolveKeyHint(cert)).isEqualTo("EC");
  }

  @Test
  void rsassaPssAlgorithmName_yieldsRsaHint() {
    X509Certificate cert = mock(X509Certificate.class);
    RSAPublicKey pub = mock(RSAPublicKey.class);
    when(pub.getAlgorithm()).thenReturn("RSASSA-PSS");
    when(cert.getPublicKey()).thenReturn(pub);

    assertThat(XadesService.resolveKeyHint(cert)).isEqualTo("RSA");
  }

  @Test
  void nullCertificate_defaultsToRsa() {
    assertThat(XadesService.resolveKeyHint(null)).isEqualTo("RSA");
  }

  @Test
  void jceMapperProviderId_canBeSetAndRetrieved() {
    String fakeP11ProviderName = "SunPKCS11-merselTestProvider-abcdef00";

    JCEMapper.setProviderId(fakeP11ProviderName);

    assertThat(JCEMapper.getProviderId()).isEqualTo(fakeP11ProviderName);
  }

  @Test
  void jceMapperProviderId_canBeResetToNull() {
    JCEMapper.setProviderId("SunPKCS11-someProvider");
    JCEMapper.setProviderId(null);

    assertThat(JCEMapper.getProviderId()).isNull();
  }

  @Test
  void jceMapperProviderId_tryFinallySafelyRestoresPreviousValue() {
    String original = "SunPKCS11-baseline";
    JCEMapper.setProviderId(original);

    String captured = JCEMapper.getProviderId();
    try {
      JCEMapper.setProviderId("SunPKCS11-merselXadesSigner-1");
      assertThat(JCEMapper.getProviderId()).isEqualTo("SunPKCS11-merselXadesSigner-1");
    } finally {
      JCEMapper.setProviderId(captured);
    }

    assertThat(JCEMapper.getProviderId()).isEqualTo(original);
  }
}
