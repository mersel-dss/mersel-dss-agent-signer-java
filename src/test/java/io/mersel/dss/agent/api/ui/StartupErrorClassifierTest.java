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
package io.mersel.dss.agent.api.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.BindException;

import org.junit.jupiter.api.Test;

/**
 * {@link StartupErrorClassifier} birim testleri — port-in-use senaryosunun "uygulama zaten
 * çalışıyor" mesajına, diğer hataların generic kovaya düştüğünü ve teknik dökümün her zaman
 * üretildiğini doğrular.
 */
class StartupErrorClassifierTest {

  /** Spring Boot {@code PortInUseException}'ı taklit eden minimal sahte (getPort() dahil). */
  private static final class FakePortInUseException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int port;

    FakePortInUseException(int port) {
      super("Port " + port + " is already in use");
      this.port = port;
    }

    public int getPort() {
      return port;
    }
  }

  @Test
  void classify_portInUseException_isDetectedWithPort() {
    Throwable wrapped =
        new IllegalStateException(
            "Failed to start bean 'webServerStartStop'", new FakePortInUseException(15212));

    StartupError error = StartupErrorClassifier.classify(wrapped);

    assertThat(error.getKind()).isEqualTo(StartupError.Kind.PORT_IN_USE);
    assertThat(error.getHeadline()).isEqualTo("Uygulama Zaten Çalışıyor");
    assertThat(error.getMessage()).contains("15212 portu");
    assertThat(error.getMessage()).contains("başka bir örneği");
    assertThat(error.getDetails()).contains("FakePortInUseException");
  }

  @Test
  void classify_bindException_isPortInUse() {
    Throwable wrapped =
        new RuntimeException("server start failed", new BindException("Address already in use"));

    StartupError error = StartupErrorClassifier.classify(wrapped);

    assertThat(error.getKind()).isEqualTo(StartupError.Kind.PORT_IN_USE);
  }

  @Test
  void classify_addressAlreadyInUseMessage_isPortInUse() {
    StartupError error =
        StartupErrorClassifier.classify(
            new RuntimeException("bind() failed: Address already in use"));
    assertThat(error.getKind()).isEqualTo(StartupError.Kind.PORT_IN_USE);
  }

  @Test
  void classify_unrelatedError_isGeneric() {
    Throwable error =
        new IllegalStateException(
            "Bean creation failed", new NullPointerException("certificate store missing"));

    StartupError classified = StartupErrorClassifier.classify(error);

    assertThat(classified.getKind()).isEqualTo(StartupError.Kind.GENERIC);
    assertThat(classified.getHeadline()).isEqualTo("Uygulama Başlatılamadı");
    assertThat(classified.getMessage()).contains("certificate store missing");
    assertThat(classified.getDetails()).contains("NullPointerException");
  }

  @Test
  void classify_null_producesGenericWithoutThrowing() {
    StartupError classified = StartupErrorClassifier.classify(null);
    assertThat(classified.getKind()).isEqualTo(StartupError.Kind.GENERIC);
    assertThat(classified.getDetails()).isNotEmpty();
  }
}
