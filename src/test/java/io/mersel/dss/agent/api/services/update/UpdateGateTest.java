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
 */
package io.mersel.dss.agent.api.services.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.config.SignerProperties;

/**
 * {@link UpdateGate} state machine + listener kontratı testleri. Interceptor + UI senkronizasyonu
 * tek truth source bu bean olduğu için listener'ın aynı state için tekrar tetiklenmemesi ve
 * mandatory toggle'ın {@link UpdateGate#isMandatoryBlocked()} davranışını doğru yansıtması önemli.
 */
class UpdateGateTest {

  private SignerProperties props;
  private UpdateGate gate;

  @BeforeEach
  void setUp() {
    props = new SignerProperties();
    gate = new UpdateGate(props);
  }

  @Test
  void initialStateIsClearAndNotBlocked() {
    assertThat(gate.getPending()).isNull();
    assertThat(gate.isMandatoryBlocked()).isFalse();
  }

  @Test
  void availableUpdateOpensGateInMandatoryMode() {
    UpdateInfo info = available("1.0.1");
    gate.publish(info);
    assertThat(gate.getPending()).isSameAs(info);
    assertThat(gate.isMandatoryBlocked()).isTrue();
  }

  @Test
  void availableUpdateInNonMandatoryModeKeepsRequestsFlowing() {
    props.getUpdate().setMandatory(false);
    gate.publish(available("1.0.1"));
    assertThat(gate.getPending()).isNotNull();
    assertThat(gate.isMandatoryBlocked()).isFalse();
  }

  @Test
  void upToDateInfoClearsPending() {
    gate.publish(available("1.0.1"));
    gate.publish(UpdateInfo.upToDate("1.0.0"));
    assertThat(gate.getPending()).isNull();
    assertThat(gate.isMandatoryBlocked()).isFalse();
  }

  @Test
  void listenerFiresOnInitialSetAndOnStateChange() {
    AtomicReference<UpdateInfo> last = new AtomicReference<>();
    gate.setListener(last::set);
    assertThat(last.get()).isNull();

    UpdateInfo v101 = available("1.0.1");
    gate.publish(v101);
    assertThat(last.get()).isSameAs(v101);
  }

  @Test
  void listenerDoesNotFireForSameLatestVersion() {
    AtomicReference<Integer> calls = new AtomicReference<>(0);
    gate.setListener(info -> calls.updateAndGet(c -> c + 1));
    // setListener kayıt anında bir kez tetikledi (mevcut state ile null).
    int baseline = calls.get();

    gate.publish(available("1.0.1"));
    gate.publish(available("1.0.1"));
    gate.publish(available("1.0.1"));

    assertThat(calls.get()).isEqualTo(baseline + 1);
  }

  @Test
  void clearResetsPendingAndFiresListener() {
    AtomicReference<UpdateInfo> last = new AtomicReference<>();
    gate.publish(available("1.0.1"));
    gate.setListener(last::set);
    assertThat(last.get()).isNotNull();

    gate.clear();
    assertThat(last.get()).isNull();
    assertThat(gate.getPending()).isNull();
  }

  private static UpdateInfo available(String latest) {
    return UpdateInfo.available(
        "1.0.0",
        latest,
        "v" + latest,
        "https://example/release",
        "https://example/" + latest + ".jar",
        "",
        "");
  }
}
