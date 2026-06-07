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

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.util.Locale;

/**
 * PKCS#12 (PFX) keystore yükleme yardımcısı. Yanlış parolayı kullanıcı-dostu {@link
 * IllegalArgumentException}'a çevirir (GlobalExceptionHandler bunu 400'e eşler).
 *
 * <p>{@link BouncyCastleSetup#ensureRegistered()} çağrılır: SunEC kaldırılıp BC pozisyon 1'e
 * yerleşince explicit EC parametreleri taşıyan PFX'ler de parse edilir.
 */
public final class Pkcs12KeyStores {

  private Pkcs12KeyStores() {
    /* static-only */
  }

  /**
   * PFX byte'larını verilen parolayla yükler.
   *
   * @throws IllegalArgumentException parola hatalıysa veya dosya bozuk/PKCS#12 değilse
   */
  public static KeyStore load(byte[] pfxBytes, char[] password) {
    if (pfxBytes == null || pfxBytes.length == 0) {
      throw new IllegalArgumentException("PFX dosyası boş.");
    }
    BouncyCastleSetup.ensureRegistered();
    char[] pw = password == null ? new char[0] : password;
    try {
      KeyStore ks = KeyStore.getInstance("PKCS12");
      ks.load(new ByteArrayInputStream(pfxBytes), pw);
      return ks;
    } catch (Exception e) {
      String msg = rootMessage(e);
      if (msg != null && msg.toLowerCase(Locale.ROOT).contains("password")) {
        throw new IllegalArgumentException("PFX parolası hatalı.", e);
      }
      throw new IllegalArgumentException(
          "PFX dosyası okunamadı (PKCS#12 değil ya da bozuk): " + msg, e);
    }
  }

  private static String rootMessage(Throwable t) {
    Throwable cur = t;
    String msg = null;
    int guard = 0;
    while (cur != null && guard++ < 20) {
      if (cur.getMessage() != null) {
        msg = cur.getMessage();
      }
      if (cur.getCause() == cur) {
        break;
      }
      cur = cur.getCause();
    }
    return msg;
  }
}
