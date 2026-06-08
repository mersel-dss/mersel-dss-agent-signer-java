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
package io.mersel.dss.agent.api.services.timestamp.tubitak;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/** {@link TubitakAuthenticationHelper} kimlik token üretimi. */
class TubitakAuthenticationHelperTest {

  private static final byte[] DATA_HASH = new byte[32]; // 32 bayt sıfır = SHA-256 boyutu

  @Test
  void producesParseableDerHexToken() throws Exception {
    String hex = TubitakAuthenticationHelper.encryptIdentity(12345, "secret", DATA_HASH);

    assertThat(hex).isNotEmpty();
    assertThat(hex).matches("[0-9a-f]+");

    // Hex çözülünce geçerli bir ASN.1 SEQUENCE olmalı: {customerId, salt, iter, iv, encryptedData}
    ASN1Primitive primitive = ASN1Primitive.fromByteArray(Hex.decode(hex));
    assertThat(primitive).isInstanceOf(ASN1Sequence.class);

    ASN1Sequence seq = (ASN1Sequence) primitive;
    assertThat(seq.size()).isEqualTo(5);
    assertThat(((ASN1Integer) seq.getObjectAt(0)).getValue()).isEqualTo(BigInteger.valueOf(12345));
  }

  @Test
  void randomSaltAndIvMakeTokensDiffer() {
    String first = TubitakAuthenticationHelper.encryptIdentity(1, "p", DATA_HASH);
    String second = TubitakAuthenticationHelper.encryptIdentity(1, "p", DATA_HASH);
    assertThat(first).isNotEqualTo(second);
  }
}
