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
package io.mersel.dss.agent.api.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CauseChainExtractorTest {

  @Test
  void flatten_nullThrowable_returnsEmptyList() {
    assertThat(CauseChainExtractor.flatten(null)).isEmpty();
  }

  @Test
  void flatten_recordsTypeAndMessageInOrder() {
    Throwable root = new IllegalStateException("CKR_MECHANISM_INVALID");
    Throwable mid = new RuntimeException("Unsupported parameters", root);
    Throwable top = new RuntimeException("XAdES-BES imzalama başarısız", mid);

    List<CauseChainExtractor.Frame> frames = CauseChainExtractor.flatten(top);

    assertThat(frames).hasSize(3);
    assertThat(frames.get(0).getType()).isEqualTo(RuntimeException.class.getName());
    assertThat(frames.get(0).getMessage()).contains("XAdES-BES imzalama başarısız");
    assertThat(frames.get(1).getMessage()).contains("Unsupported parameters");
    assertThat(frames.get(2).getMessage()).contains("CKR_MECHANISM_INVALID");
    assertThat(frames.get(2).getType()).isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void flatten_respectsMaxDepth() {
    Throwable a = new RuntimeException("a");
    Throwable b = new RuntimeException("b", a);
    Throwable c = new RuntimeException("c", b);
    Throwable d = new RuntimeException("d", c);

    assertThat(CauseChainExtractor.flatten(d, 2)).hasSize(2);
  }

  @Test
  void flatten_breaksCycleSilently() {
    RuntimeException a = new RuntimeException("a");
    RuntimeException b = new RuntimeException("b", a);
    a.initCause(b); // a → b → a → ...

    List<CauseChainExtractor.Frame> frames = CauseChainExtractor.flatten(a);
    // Cycle koruması — ikinci kez aynı throwable'ı görünce durur.
    assertThat(frames).hasSizeBetween(1, 2);
  }

  @Test
  void rootMessage_returnsRootCauseMessage() {
    Throwable root = new RuntimeException("CKR_MECHANISM_INVALID");
    Throwable mid = new RuntimeException("Unsupported parameters", root);
    Throwable top = new IllegalStateException("imzalama başarısız", mid);

    assertThat(CauseChainExtractor.rootMessage(top)).isEqualTo("CKR_MECHANISM_INVALID");
  }

  @Test
  void rootMessage_returnsClassNameWhenMessageNull() {
    Throwable root = new IllegalArgumentException();
    Throwable top = new RuntimeException("wrap", root);
    assertThat(CauseChainExtractor.rootMessage(top)).isEqualTo("IllegalArgumentException");
  }

  @Test
  void findContaining_caseInsensitiveSubstringSearch() {
    Throwable root = new RuntimeException("CKR_MECHANISM_INVALID");
    Throwable top = new RuntimeException("İmzalama Başarısız", root);

    CauseChainExtractor.Frame mech =
        CauseChainExtractor.findContaining(top, "ckr_mechanism_invalid");
    assertThat(mech).isNotNull();
    assertThat(mech.getMessage()).isEqualTo("CKR_MECHANISM_INVALID");

    CauseChainExtractor.Frame missing = CauseChainExtractor.findContaining(top, "padding-error");
    assertThat(missing).isNull();
  }

  @Test
  void findContaining_alsoMatchesExceptionClassName() {
    Throwable root = new java.security.InvalidAlgorithmParameterException("bad");
    Throwable top = new RuntimeException("wrap", root);

    CauseChainExtractor.Frame f =
        CauseChainExtractor.findContaining(top, "InvalidAlgorithmParameterException");
    assertThat(f).isNotNull();
    assertThat(f.getType()).contains("InvalidAlgorithmParameterException");
  }
}
