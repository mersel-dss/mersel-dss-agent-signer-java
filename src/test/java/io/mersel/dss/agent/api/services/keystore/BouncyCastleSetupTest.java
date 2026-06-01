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

import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link BouncyCastleSetup}'ın JCA provider listesini doğru sırayla yeniden düzenlediğini doğrular.
 *
 * <p>Her test öncesinde provider durumu temizlenir ve test sonrasında JVM'in default durumuna (BC
 * kayıtlı, SunEC kaldırılmış) çekilir; böylece test sıralaması diğer testleri etkilemez.
 */
class BouncyCastleSetupTest {

  private static final String SUN_EC = "SunEC";
  private static final String BC = BouncyCastleProvider.PROVIDER_NAME;

  private Provider originalSunEc;
  private Provider originalBc;
  private int originalBcPosition;

  @BeforeEach
  void snapshot() {
    originalSunEc = Security.getProvider(SUN_EC);
    originalBc = Security.getProvider(BC);
    originalBcPosition = providerPosition(BC);
  }

  @AfterEach
  void restore() {
    Security.removeProvider(BC);
    Security.removeProvider(SUN_EC);
    if (originalSunEc != null) {
      Security.addProvider(originalSunEc);
    }
    if (originalBc != null) {
      if (originalBcPosition > 0) {
        Security.insertProviderAt(originalBc, originalBcPosition);
      } else {
        Security.addProvider(originalBc);
      }
    }
  }

  @Test
  void ensureRegistered_freshState_addsBcAtPosition1_andRemovesSunEc() {
    Security.removeProvider(BC);
    if (Security.getProvider(SUN_EC) == null) {
      // SunEC bazı testlerde test sırasından önce zaten kaldırılmış olabilir; deterministik
      // test için sahte bir SunEC adı altında dummy provider kaydedip kaldırma yolunu da
      // doğrulayalım. JCA semantiği gerçek SunEC ile aynı (Security.removeProvider yalnız
      // ada bakar).
      Security.addProvider(new DummyProvider(SUN_EC));
    }

    boolean changed = BouncyCastleSetup.ensureRegistered();

    assertThat(changed).isTrue();
    assertThat(Security.getProvider(BC)).isNotNull();
    assertThat(Security.getProvider(SUN_EC)).isNull();
    assertThat(providerPosition(BC)).isEqualTo(1);
  }

  @Test
  void ensureRegistered_idempotent_secondCallIsNoop() {
    Security.removeProvider(BC);

    BouncyCastleSetup.ensureRegistered();
    boolean changedSecond = BouncyCastleSetup.ensureRegistered();

    assertThat(changedSecond).isFalse();
    assertThat(Security.getProvider(BC)).isNotNull();
    assertThat(providerPosition(BC)).isEqualTo(1);
  }

  @Test
  void ensureRegistered_whenBcAlreadyPresentButSunEcStillThere_removesSunEc() {
    Security.removeProvider(BC);
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    if (Security.getProvider(SUN_EC) == null) {
      Security.addProvider(new DummyProvider(SUN_EC));
    }

    boolean changed = BouncyCastleSetup.ensureRegistered();

    assertThat(changed).isTrue();
    assertThat(Security.getProvider(SUN_EC)).isNull();
    assertThat(Security.getProvider(BC)).isNotNull();
  }

  /**
   * EC explicit parameters'ı parse edebildiğimizi smoke-test eder; BC'nin sahnede olduğundan emin
   * olmak için {@code AlgorithmParameters.getInstance("EC")} çağrısı yapılır. Bu çağrı SunEC
   * sahnede tek kalsa atomik patlardı (sadece named curve); BC sahneye girdiğinde başarılı olur.
   */
  @Test
  void afterEnsure_ecAlgorithmParameters_resolvableViaBc() throws NoSuchAlgorithmException {
    Security.removeProvider(BC);

    BouncyCastleSetup.ensureRegistered();

    AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
    assertThat(params).isNotNull();
    assertThat(params.getProvider().getName()).isEqualTo(BC);
  }

  private static int providerPosition(String name) {
    Provider[] providers = Security.getProviders();
    for (int i = 0; i < providers.length; i++) {
      if (name.equals(providers[i].getName())) {
        return i + 1;
      }
    }
    return -1;
  }

  /**
   * JCA contract'ı yalnız {@code getName()}'e bakar; ad çakışmasını test etmek için minimal sahte
   * provider. EC servisi sunmaz, bu yüzden BC kaldırılmadan EC algoritma çözünürlüğüne karışmaz.
   */
  private static final class DummyProvider extends Provider {
    private static final long serialVersionUID = 1L;

    DummyProvider(String name) {
      super(name, 1.0d, "Test-only dummy for " + name);
    }
  }
}
