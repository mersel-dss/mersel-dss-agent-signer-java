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
package io.mersel.dss.agent.api.services.keystore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class Pkcs11MechanismsTest {

  @Test
  void nameOf_knownCkmIsReturnedSymbolically() {
    assertThat(Pkcs11Mechanisms.nameOf(0x00000040L)).isEqualTo("CKM_SHA256_RSA_PKCS");
    assertThat(Pkcs11Mechanisms.nameOf(0x00001044L)).isEqualTo("CKM_ECDSA_SHA256");
    assertThat(Pkcs11Mechanisms.nameOf(0x00000001L)).isEqualTo("CKM_RSA_PKCS");
  }

  @Test
  void nameOf_unknownCkmFallsBackToHex() {
    assertThat(Pkcs11Mechanisms.nameOf(0x12345678L)).isEqualTo("0x12345678");
  }

  @Test
  void valueOf_roundTripsKnownNames() {
    assertThat(Pkcs11Mechanisms.valueOf("CKM_SHA256_RSA_PKCS")).isEqualTo(0x00000040L);
    assertThat(Pkcs11Mechanisms.valueOf("ckm_sha256_rsa_pkcs")).isEqualTo(0x00000040L);
    assertThat(Pkcs11Mechanisms.valueOf("CKM_DOES_NOT_EXIST")).isEqualTo(-1L);
    assertThat(Pkcs11Mechanisms.valueOf(null)).isEqualTo(-1L);
  }

  @Test
  void decodeFlags_includesSignAndHwFlags() {
    long flags = Pkcs11Mechanisms.CKF_HW | Pkcs11Mechanisms.CKF_SIGN | Pkcs11Mechanisms.CKF_VERIFY;
    List<String> decoded = Pkcs11Mechanisms.decodeFlags(flags);
    assertThat(decoded).contains("CKF_HW", "CKF_SIGN", "CKF_VERIFY");
  }

  @Test
  void rsaCandidateOrderingPrefersSha256OverSha1() {
    int sha256 = Pkcs11Mechanisms.RSA_SIGN_MECHANISMS.indexOf("CKM_SHA256_RSA_PKCS");
    int sha1 = Pkcs11Mechanisms.RSA_SIGN_MECHANISMS.indexOf("CKM_SHA1_RSA_PKCS");
    int rawRsa = Pkcs11Mechanisms.RSA_SIGN_MECHANISMS.indexOf("CKM_RSA_PKCS");
    assertThat(sha256).isLessThan(sha1).isLessThan(rawRsa);
  }

  @Test
  void ecdsaCandidateOrderingPrefersSha384() {
    int sha384 = Pkcs11Mechanisms.ECDSA_SIGN_MECHANISMS.indexOf("CKM_ECDSA_SHA384");
    int sha256 = Pkcs11Mechanisms.ECDSA_SIGN_MECHANISMS.indexOf("CKM_ECDSA_SHA256");
    int raw = Pkcs11Mechanisms.ECDSA_SIGN_MECHANISMS.indexOf("CKM_ECDSA");
    assertThat(sha384).isLessThan(sha256).isLessThan(raw);
  }
}
