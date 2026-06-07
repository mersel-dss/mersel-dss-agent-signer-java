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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.services.keystore.Pkcs12KeyStores;

/**
 * Kart takılı olmasa bile kullanıcının tanımladığı PKCS#11 / PKCS#12 kaynaklarını "Dummy Card"
 * olarak tutan <b>bellek-içi</b> kayıt defteri.
 *
 * <p>Kayıtlar yalnız process yaşam süresi boyunca tutulur; agent yeniden başlatıldığında silinir
 * (kullanıcı tercihi). PKCS#12 parolaları {@code char[]} olarak bellekte saklanır ve {@link
 * #remove(String)} / {@link #shutdown()} sırasında sıfırlanır.
 *
 * <p>{@code name} alanı hem benzersiz anahtar hem de imza/listeleme uçlarındaki {@code terminalName}
 * olarak kullanılır; bu yüzden gerçek bir PC/SC okuyucu adıyla çakışmaması beklenir (örn. "PFX -
 * firma.pfx" gibi açık bir ad verilir).
 */
@Component
public class VirtualTokenRegistry {

  private static final Logger log = LoggerFactory.getLogger(VirtualTokenRegistry.class);

  private final ConcurrentMap<String, VirtualToken> tokens =
      new ConcurrentHashMap<String, VirtualToken>();

  /** Kalıcı depo; {@code null} ise registry bellek-içi çalışır (unit testler). */
  private final VirtualTokenStore store;

  /** Bellek-içi (kalıcılıksız) kurucu — unit testler için. */
  public VirtualTokenRegistry() {
    this(null);
  }

  /** Spring kurucu — disk deposu enjekte edilir. */
  @Autowired
  public VirtualTokenRegistry(VirtualTokenStore store) {
    this.store = store;
  }

  /**
   * Uygulama açılışında diskteki kayıtları belleğe yükler. Persist tekrar tetiklenmez (restore
   * modunda). Tek tek hatalar (örn. silinmiş PFX, erişilemez lib) loglanıp atlanır.
   */
  @PostConstruct
  void restoreFromDisk() {
    if (store == null) {
      return;
    }
    int restored = 0;
    for (VirtualTokenStore.Loaded loaded : store.loadAll()) {
      try {
        if (loaded.getType() == VirtualTokenType.PKCS11) {
          doRegisterPkcs11(loaded.getName(), loaded.getLibraryPath(), false);
        } else {
          try {
            doRegisterPkcs12(
                loaded.getName(), loaded.getPfxBytes(), loaded.getPassword(), loaded.getSource(),
                false);
          } finally {
            if (loaded.getPassword() != null) {
              java.util.Arrays.fill(loaded.getPassword(), '\0');
            }
          }
        }
        restored++;
      } catch (RuntimeException e) {
        log.warn("Kalıcı sanal kart '{}' geri yüklenemedi: {}", loaded.getName(), e.getMessage());
      }
    }
    if (restored > 0) {
      log.info("{} kalıcı sanal kart geri yüklendi.", restored);
    }
  }

  /**
   * Bir PKCS#11 sanal token kaydeder.
   *
   * @throws IllegalArgumentException ad/yol boşsa, yol diskte yoksa ya da ad zaten kullanımdaysa
   */
  public VirtualToken registerPkcs11(String name, String libraryPath) {
    return doRegisterPkcs11(name, libraryPath, true);
  }

  private VirtualToken doRegisterPkcs11(String name, String libraryPath, boolean persist) {
    String trimmedName = requireName(name);
    if (StringUtils.isBlank(libraryPath)) {
      throw new IllegalArgumentException("PKCS#11 için 'libraryPath' zorunludur.");
    }
    Path path = Paths.get(libraryPath.trim());
    if (!Files.isReadable(path)) {
      throw new IllegalArgumentException(
          "PKCS#11 kütüphanesi bulunamadı ya da okunamıyor: " + libraryPath);
    }
    VirtualToken token = VirtualToken.pkcs11(trimmedName, path.toString());
    putUnique(token);
    if (persist && store != null) {
      store.persistPkcs11(token.getName(), token.getPkcs11LibraryPath(), token.getSource());
    }
    log.info("Sanal PKCS#11 token kaydedildi: name='{}', lib={}", trimmedName, path);
    return token;
  }

  /**
   * Bir PKCS#12 (PFX) sanal token kaydeder. PFX byte'ları parolayla yüklenip doğrulanır; en az bir
   * private key girişi içermelidir.
   *
   * @throws IllegalArgumentException ad boşsa, ad kullanımdaysa, parola hatalıysa, PFX bozuksa veya
   *     imzalanabilir anahtar içermiyorsa
   */
  public VirtualToken registerPkcs12(
      String name, byte[] pfxBytes, char[] password, String sourceLabel) {
    return doRegisterPkcs12(name, pfxBytes, password, sourceLabel, true);
  }

  private VirtualToken doRegisterPkcs12(
      String name, byte[] pfxBytes, char[] password, String sourceLabel, boolean persist) {
    String trimmedName = requireName(name);
    if (pfxBytes == null || pfxBytes.length == 0) {
      throw new IllegalArgumentException("PFX dosyası boş.");
    }
    char[] pw = password == null ? new char[0] : password;

    KeyStore ks = Pkcs12KeyStores.load(pfxBytes, pw);

    if (!hasSigningKey(ks, pw)) {
      throw new IllegalArgumentException(
          "PFX içinde imzalanabilir bir özel anahtar (private key) bulunamadı.");
    }

    String label = StringUtils.isBlank(sourceLabel) ? "PFX" : sourceLabel.trim();
    VirtualToken token = VirtualToken.pkcs12(trimmedName, ks, pw, label);
    putUnique(token);
    if (persist && store != null) {
      // Parola char[]'ını depo şifreleyip yazsın; bytes diske kopyalanır.
      store.persistPkcs12(token.getName(), pfxBytes, pw, label);
    }
    log.info("Sanal PKCS#12 (PFX) token kaydedildi: name='{}', source='{}'", trimmedName, label);
    return token;
  }

  /** Verilen terminalName'e karşılık gelen sanal token; yoksa {@code null}. */
  public VirtualToken find(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return tokens.get(name.trim());
  }

  /** Kayıtlı tüm sanal token'lar (kayıt sırası garanti edilmez). */
  public List<VirtualToken> list() {
    return new ArrayList<VirtualToken>(tokens.values());
  }

  /**
   * Bir sanal token'ı kaldırır ve (PKCS#12 ise) parolasını sıfırlar.
   *
   * @return kaldırılan token; bulunamazsa {@code null}
   */
  public VirtualToken remove(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    VirtualToken removed = tokens.remove(name.trim());
    if (removed != null) {
      removed.wipe();
      if (store != null) {
        store.delete(name.trim());
      }
      log.info("Sanal token kaldırıldı: name='{}'", name.trim());
    }
    return removed;
  }

  /** Tüm parolaları sıfırlayıp kaydı temizler (uygulama kapanışında). */
  @PreDestroy
  public void shutdown() {
    for (VirtualToken t : tokens.values()) {
      t.wipe();
    }
    tokens.clear();
  }

  private void putUnique(VirtualToken token) {
    VirtualToken prev = tokens.putIfAbsent(token.getName(), token);
    if (prev != null) {
      token.wipe();
      throw new IllegalArgumentException(
          "Bu isimde bir sanal kart zaten tanımlı: " + token.getName());
    }
  }

  private static String requireName(String name) {
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("Sanal kart 'name' alanı zorunludur.");
    }
    return name.trim();
  }

  /** Keystore'da parolayla açılabilen en az bir private key var mı? */
  private static boolean hasSigningKey(KeyStore ks, char[] pw) {
    try {
      Enumeration<String> aliases = ks.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        if (ks.isKeyEntry(alias)) {
          try {
            if (ks.getKey(alias, pw) != null && ks.getCertificate(alias) instanceof X509Certificate) {
              return true;
            }
          } catch (Exception perAlias) {
            log.debug("PFX alias '{}' anahtarı okunamadı: {}", alias, perAlias.getMessage());
          }
        }
      }
    } catch (Exception e) {
      log.debug("PFX alias enumerasyonu başarısız: {}", e.getMessage());
    }
    return false;
  }

  /** Test/diagnostic: kayıt sayısı. */
  public int size() {
    return tokens.size();
  }

  /** Test yardımcı: doğrudan token kaydı (yalnız in-process testler için). */
  List<String> names() {
    return Collections.unmodifiableList(new ArrayList<String>(tokens.keySet()));
  }
}
