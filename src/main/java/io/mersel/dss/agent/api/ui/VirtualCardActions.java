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

import java.util.List;

/**
 * Masaüstü "Sanal Kart Tanımla" diyaloğunun backend ile konuştuğu port. {@link MainWindow}/{@link
 * VirtualCardDialog} Spring servislerini doğrudan tanımaz; {@code DesktopUiBootstrap} bu arayüzü
 * {@code VirtualTokenRegistry} üzerine implemente edip enjekte eder.
 *
 * <p>Tüm metotlar geçersiz girdide {@link IllegalArgumentException} fırlatabilir; diyalog mesajı
 * kullanıcıya gösterir.
 */
public interface VirtualCardActions {

  /** Tanımlı sanal kartların salt-okunur özetleri. */
  List<View> list();

  /** PKCS#11 (HSM / yüklü sürücü) sanal kart kaydeder. */
  void registerPkcs11(String name, String libraryPath);

  /** PKCS#12 (PFX) sanal kart kaydeder; parola bellekte saklanır. */
  void registerPkcs12(String name, byte[] pfxBytes, char[] password, String sourceLabel);

  /** Adı verilen sanal kartı kaldırır (PFX ise parolayı siler). */
  void remove(String name);

  /** Diyalog tablosunda gösterilen satır. */
  final class View {
    private final String name;
    private final String cardType;
    private final String source;

    public View(String name, String cardType, String source) {
      this.name = name;
      this.cardType = cardType;
      this.source = source;
    }

    public String getName() {
      return name;
    }

    public String getCardType() {
      return cardType;
    }

    public String getSource() {
      return source;
    }
  }
}
