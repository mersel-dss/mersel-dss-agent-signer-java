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
package io.mersel.dss.agent.api.services.virtualtoken;

import java.security.KeyStore;
import java.util.Arrays;

/**
 * Bellekte tutulan bir sanal token ("Dummy Card") tanımı. {@link VirtualTokenRegistry} tarafından
 * yönetilir ve {@code GET /smartcard} çıktısında fiziksel kartların yanında listelenir.
 *
 * <p>İki tip de aynı {@code name} alanını "terminalName" olarak kullanır; imza ve sertifika
 * listeleme uçları bu adı normal bir PC/SC okuyucu adı gibi alır, registry üzerinden sanal token'a
 * çözülür.
 *
 * <h2>Güvenlik</h2>
 *
 * PKCS#12 parolası {@code char[]} olarak bellekte tutulur ({@link #password}); {@link #wipe()}
 * çağrıldığında sıfırlanır. PFX byte'ları diske yazılmaz; yüklenen {@link KeyStore} bellekte
 * saklanır (XAdES software imza yolu {@code DirectKeyingDataProvider} kullandığı için geçici dosya
 * gerekmez).
 */
public final class VirtualToken {

  private final String name;
  private final VirtualTokenType type;

  /** PKCS#11: kütüphane yolu; PKCS#12'de {@code null}. */
  private final String pkcs11LibraryPath;

  /** PKCS#12: yüklenmiş (parolası doğrulanmış) keystore; PKCS#11'de {@code null}. */
  private final transient KeyStore keyStore;

  /** PKCS#12: keystore parolası; PKCS#11'de {@code null}. */
  private final transient char[] password;

  /** UI'da gösterilecek kaynak etiketi (PFX dosya adı ya da lib yolu). */
  private final String source;

  private VirtualToken(
      String name,
      VirtualTokenType type,
      String pkcs11LibraryPath,
      KeyStore keyStore,
      char[] password,
      String source) {
    this.name = name;
    this.type = type;
    this.pkcs11LibraryPath = pkcs11LibraryPath;
    this.keyStore = keyStore;
    this.password = password;
    this.source = source;
  }

  /** PKCS#11 (HSM / yüklü sürücü) sanal token üretir. */
  public static VirtualToken pkcs11(String name, String libraryPath) {
    return new VirtualToken(name, VirtualTokenType.PKCS11, libraryPath, null, null, libraryPath);
  }

  /** PKCS#12 (PFX) sanal token üretir; parola registry tarafından önceden doğrulanmış olmalı. */
  public static VirtualToken pkcs12(
      String name, KeyStore keyStore, char[] password, String sourceLabel) {
    char[] copy = password == null ? new char[0] : password.clone();
    return new VirtualToken(name, VirtualTokenType.PKCS12, null, keyStore, copy, sourceLabel);
  }

  public String getName() {
    return name;
  }

  public VirtualTokenType getType() {
    return type;
  }

  public boolean isPkcs11() {
    return type == VirtualTokenType.PKCS11;
  }

  public boolean isPkcs12() {
    return type == VirtualTokenType.PKCS12;
  }

  public String getPkcs11LibraryPath() {
    return pkcs11LibraryPath;
  }

  public KeyStore getKeyStore() {
    return keyStore;
  }

  public String getSource() {
    return source;
  }

  /** UI ve {@code SmartCardDetail.cardType} için insan-okunur etiket. */
  public String getDisplayCardType() {
    return isPkcs12() ? "PKCS#12 (PFX)" : "PKCS#11 (HSM)";
  }

  /** {@code SmartCardDetail.source} alanı için tip kısaltması. */
  public String getSourceType() {
    return type.name();
  }

  /** Software keystore okumaları için parolanın bir kopyası ({@code null} → boş). */
  public char[] passwordChars() {
    return password == null ? new char[0] : password.clone();
  }

  /** {@code Pkcs11Session.wrapForTest} String pin beklediği için parola String dönüşü. */
  public String passwordString() {
    return password == null ? "" : new String(password);
  }

  /** Bellekteki parolayı sıfırlar ({@code remove} / shutdown'da çağrılır). */
  public void wipe() {
    if (password != null) {
      Arrays.fill(password, '\0');
    }
  }
}
