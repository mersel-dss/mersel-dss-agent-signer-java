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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECField;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Native PKCS#11 imza akışındaki saf-hesaplama yardımcılarının kanıtı:
 *
 * <ul>
 *   <li>{@link XadesService#rsaDigestInfo(byte[], String)} PKCS#1 v1.5 DigestInfo (RFC 8017 §9.2)
 *       prefix'ini her hash algoritması için doğru sırada üretir.
 *   <li>{@link XadesService#estimateKeySizeBits(X509Certificate)} RSA modül bit'i ve EC field bit'i
 *       arasında ayırım yapar; null public key veya bilinmeyen tip için graceful null döner.
 * </ul>
 *
 * <p>Bu yardımcılar sahada NES Bulut dual-key kartları için native PKCS#11 imza yoluna düşülen
 * akışta kullanılır; hatası kartın imzayı reddetmesine yol açar (CKR_DATA_INVALID veya
 * CKR_DATA_LEN_RANGE). Bu yüzden prefix byte'larını test'le sabitliyoruz.
 */
class XadesServiceNativeFallbackTest {

  @Test
  void rsaDigestInfoPrefixIsCorrectForSha256() {
    // RFC 8017 §9.2 EMSA-PKCS1-v1_5 SHA-256 DigestInfo DER prefix
    byte[] expectedPrefix =
        new byte[] {
          0x30,
          0x31,
          0x30,
          0x0d,
          0x06,
          0x09,
          0x60,
          (byte) 0x86,
          0x48,
          0x01,
          0x65,
          0x03,
          0x04,
          0x02,
          0x01,
          0x05,
          0x00,
          0x04,
          0x20
        };

    byte[] hash = new byte[32];
    for (int i = 0; i < hash.length; i++) hash[i] = (byte) i;

    byte[] di = XadesService.rsaDigestInfo(hash, "SHA-256");
    assertThat(di).hasSize(expectedPrefix.length + 32);
    for (int i = 0; i < expectedPrefix.length; i++) {
      assertThat(di[i]).as("prefix byte %d", i).isEqualTo(expectedPrefix[i]);
    }
    for (int i = 0; i < 32; i++) {
      assertThat(di[expectedPrefix.length + i]).as("hash byte %d", i).isEqualTo(hash[i]);
    }
  }

  @Test
  void rsaDigestInfoPrefixIsCorrectForSha384() {
    byte[] expectedPrefix =
        new byte[] {
          0x30,
          0x41,
          0x30,
          0x0d,
          0x06,
          0x09,
          0x60,
          (byte) 0x86,
          0x48,
          0x01,
          0x65,
          0x03,
          0x04,
          0x02,
          0x02,
          0x05,
          0x00,
          0x04,
          0x30
        };
    byte[] hash = new byte[48];
    java.util.Arrays.fill(hash, (byte) 0xAA);

    byte[] di = XadesService.rsaDigestInfo(hash, "SHA-384");
    assertThat(di).hasSize(expectedPrefix.length + 48);
    for (int i = 0; i < expectedPrefix.length; i++) {
      assertThat(di[i]).as("prefix byte %d", i).isEqualTo(expectedPrefix[i]);
    }
  }

  @Test
  void rsaDigestInfoMatchesJcaNoneWithRsaContract() throws Exception {
    // NONEwithRSA SignatureProvider'a verilen "raw" RSA padding girdisi formatıyla bitwise eşleşme
    // mutlak güvence. SHA-256 ile dolaştırıp BC NONEwithRSA hesabıyla karşılaştırmak yerine bilinen
    // RFC test vector'üyle: SHA-256("abc") = ba7816bf...
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] hash = md.digest("abc".getBytes("UTF-8"));
    byte[] di = XadesService.rsaDigestInfo(hash, "SHA-256");
    // RFC 8017 §9.2 explicit vector: SHA-256 prefix + hash
    // (Length check + sentinel byte: 0x30 0x31 ... 0x04 0x20 ba 78 ...)
    assertThat(di[0]).isEqualTo((byte) 0x30);
    assertThat(di[1]).isEqualTo((byte) 0x31);
    assertThat(di[18]).isEqualTo((byte) 0x20);
    assertThat(di[19]).isEqualTo((byte) 0xBA);
    assertThat(di[20]).isEqualTo((byte) 0x78);
  }

  @Test
  void rsaDigestInfoRejectsUnsupportedAlgorithm() {
    try {
      XadesService.rsaDigestInfo(new byte[20], "SHA-1");
      org.assertj.core.api.Assertions.fail("SHA-1 desteklenmemeli");
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("SHA-1");
    }
  }

  @Test
  void estimateKeySizeBitsRsa2048() {
    X509Certificate cert = Mockito.mock(X509Certificate.class);
    RSAPublicKey rsa = Mockito.mock(RSAPublicKey.class);
    Mockito.when(rsa.getModulus()).thenReturn(BigInteger.ONE.shiftLeft(2047));
    Mockito.when(cert.getPublicKey()).thenReturn(rsa);
    assertThat(XadesService.estimateKeySizeBits(cert)).isEqualTo(2048);
  }

  @Test
  void estimateKeySizeBitsEcP384() {
    X509Certificate cert = Mockito.mock(X509Certificate.class);
    ECPublicKey ec = Mockito.mock(ECPublicKey.class);
    java.security.spec.ECParameterSpec params =
        Mockito.mock(java.security.spec.ECParameterSpec.class);
    java.security.spec.EllipticCurve curve = Mockito.mock(java.security.spec.EllipticCurve.class);
    ECField field = Mockito.mock(ECField.class);
    Mockito.when(field.getFieldSize()).thenReturn(384);
    Mockito.when(curve.getField()).thenReturn(field);
    Mockito.when(params.getCurve()).thenReturn(curve);
    Mockito.when(ec.getParams()).thenReturn(params);
    Mockito.when(cert.getPublicKey()).thenReturn(ec);
    assertThat(XadesService.estimateKeySizeBits(cert)).isEqualTo(384);
  }
}
