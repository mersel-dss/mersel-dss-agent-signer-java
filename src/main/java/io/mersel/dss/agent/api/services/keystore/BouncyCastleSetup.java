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

import java.security.Provider;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JCA provider arama sırasını {@link BouncyCastleProvider} öncelikli hale getirip {@code SunEC}'yi
 * kaldıran tek-noktalı kurulum yardımcısı. SunPKCS11'in token'daki EC objelerini parse ederken
 * yaşadığı <em>"Only named ECParameters supported"</em> patolojisini global olarak çözer.
 *
 * <h2>Niye gerekli?</h2>
 *
 * JDK 1.8 SunEC implementasyonu SEC 1 {@code ECParameters} CHOICE'unun yalnız {@code namedCurve}
 * arm'ını destekler; explicit form {@code ecParameters} arm'ını {@code AlgorithmParameters#init}
 * sırasında {@code IOException("Only named ECParameters supported")} ile reddeder. Türkiye'deki
 * akıllı kart firmware'lerinin bir kısmı (AKIS v2.5 dahil) {@code CKA_EC_PARAMS} attribute'unu
 * explicit formda döndürür.
 *
 * <p>SunPKCS11, {@code P11KeyStore.engineLoad()} sırasında token'daki tüm objeleri enumerate eder.
 * Karta yazılı bir EC private key veya EC publicKey objesi varsa, parse aşamasında {@code
 * AlgorithmParameters.getInstance("EC")} (provider belirtilmeden) çağrılır ve JCA arama sırası
 * SunEC'ye düşer. SunEC IOException atınca tüm keystore load'u devrilir; bu durumda gerçek imzalama
 * RSA bile olsa imzalama başarısız olur.
 *
 * <p>Cause zinciri tipik olarak:
 *
 * <pre>
 *   xades4j.UnexpectedJCAException("Unsupported parameters")
 *     └─ java.security.KeyStoreException("Unsupported parameters")
 *         └─ java.io.IOException("Only named ECParameters supported")
 * </pre>
 *
 * <h2>Çözüm</h2>
 *
 * {@link BouncyCastleProvider} hem named hem explicit EC parametre formunu destekler. BC'yi
 * pozisyon 1'e yerleştirip SunEC'yi {@code Security.removeProvider}'la kaldırınca SunPKCS11'in
 * yaptığı provider belirtmemiş {@code getInstance("EC")} çağrısı BC'ye düşer ve explicit
 * parametreler düzgün decode edilir.
 *
 * <p>SunEC'yi kaldırmadan sadece BC eklemek <b>yetmez</b> — JCA service cache'i bir kere SunEC'yi
 * EC için resolve ettikten sonra ısrarla onu seçer (özellikle aynı JVM içinde önce SunEC'yi deneyip
 * sonra BC'yi eklediğimiz senaryolarda). SunEC kalktığında EC servisleri tamamen BC'ye yönlenir.
 *
 * <h2>BC'nin yan etkileri</h2>
 *
 * BC pozisyon 1'de olduğunda <em>tüm</em> JCA istekleri (hash, RSA, cipher) önce BC'ye sorulur.
 * BC'nin RSA / SHA-* implementasyonları SunRsaSign / SunJCE ile davranışsal olarak uyumlu (FIPS
 * yolları hariç); uygulamamızın diğer kısımları (xades4j, iText) zaten BC'yi yaygın kullanır (CMS,
 * CRL, OCSP). RSA imzalama akışı SunPKCS11 üzerinden geçtiği için RSA için BC'ye düşmez — kart
 * anahtarı SunPKCS11 servisleriyle erişilir.
 *
 * <h2>İdempotent + thread-safe</h2>
 *
 * Çoklu çağrı güvenli. Aynı JVM süreci içinde birden fazla {@link Pkcs11Session#open} (paralel
 * istek, retry, vb.) ve birden fazla {@code XadesService} imzalama akışı bu metodu çağırır; yalnız
 * ilk başarılı çağrı kayıt yapar, sonrakiler erken döner.
 */
public final class BouncyCastleSetup {

  private static final Logger log = LoggerFactory.getLogger(BouncyCastleSetup.class);

  /** SunEC provider'ının sembolik adı (JDK 1.8). */
  private static final String SUN_EC = "SunEC";

  private BouncyCastleSetup() {
    /* static-only */
  }

  /**
   * BouncyCastle henüz kayıtlı değilse pozisyon 1'e yerleştirir; SunEC kayıtlıysa kaldırır.
   *
   * <p>Bu metot SunPKCS11 provider'ı yaratılmadan / kullanılmadan ÖNCE çağrılmalıdır — yani {@code
   * KeyStore.load("PKCS11", ...)} öncesinde. Sonradan çağrılırsa JCA service cache'inde SunEC'nin
   * daha önce resolve edilmiş kayıtları kalmış olabilir ve EC parse'ı yine patlar.
   *
   * @return {@code true} bu çağrıda provider listesi gerçekten değiştiyse; {@code false} hiçbir şey
   *     yapılmasına gerek yoktu (önceki bir çağrı zaten BC'yi kayıt etmişti).
   */
  public static synchronized boolean ensureRegistered() {
    Provider bc = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
    Provider sunEc = Security.getProvider(SUN_EC);
    if (bc != null && sunEc == null) {
      return false;
    }

    boolean changed = false;
    if (sunEc != null) {
      Security.removeProvider(SUN_EC);
      changed = true;
    }
    if (bc == null) {
      Security.insertProviderAt(new BouncyCastleProvider(), 1);
      changed = true;
    }

    if (changed) {
      log.info(
          "BouncyCastle EC AlgorithmParameters desteği etkinleştirildi"
              + " (SunEC kaldırıldı; SunPKCS11 explicit EC parameters'ı parse edebilecek).");
    }
    return changed;
  }
}
