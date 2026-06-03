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
 *
 * Bu yazılım IAIK PKCS#11 Wrapper kod tabanından türetilen
 * org.xipki:ipkcs11wrapper kütüphanesini kullanır:
 *   "This product includes software developed by IAIK of Graz University
 *    of Technology."  (IAIK Graz 5-clause BSD attribution requirement)
 */
package io.mersel.dss.agent.api.services.keystore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.pkcs11.wrapper.AttributeVector;
import org.xipki.pkcs11.wrapper.Mechanism;
import org.xipki.pkcs11.wrapper.PKCS11Constants;
import org.xipki.pkcs11.wrapper.PKCS11Exception;
import org.xipki.pkcs11.wrapper.PKCS11Module;
import org.xipki.pkcs11.wrapper.PKCS11Token;
import org.xipki.pkcs11.wrapper.Slot;
import org.xipki.pkcs11.wrapper.TokenException;

import io.mersel.dss.agent.api.exceptions.CertificateLookupException;
import io.mersel.dss.agent.api.exceptions.Pkcs11AuthException;
import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryException;
import io.mersel.dss.agent.api.exceptions.SmartCardException;

/**
 * SunPKCS11 {@code P11KeyStore} katmanını tamamen <b>by-pass</b> eden PKCS#11 imzalama istemcisi.
 * IAIK PKCS#11 Wrapper kod tabanından türetilen {@code org.xipki:ipkcs11wrapper} (1.6.8 forku)
 * üzerinden doğrudan PKCS#11 spec çağrılarına ({@code C_OpenSession + C_Login + C_FindObjects +
 * C_SignInit + C_Sign}) iner.
 *
 * <p><b>Neden var?</b> JDK 1.8 SunPKCS11 {@code P11KeyStore.engineLoad()} → {@code
 * mapPrivateKeys()} iki private key aynı {@code CKA_ID} taşıyorsa {@code KeyStoreException("invalid
 * KeyStore state: found 2 private keys sharing CKA_ID ...")} fırlatır. NES Bulut / Kamu SM dual-key
 * setup'ında (SIGN0 imzalama + SIGN1 anahtar uzlaşımı) sürücü her iki anahtarı aynı CKA_ID ile
 * yazar; SunPKCS11 keystore'u açılamaz, cert seçim mantığı dahi devreye giremez.
 *
 * <p>IAIK / xipki Wrapper PKCS#11 spec'inin yapısal hale getirdiği bu fragility'i tanımaz: imza
 * akışında alias map kullanılmaz, doğrudan PKCS#11 object handle'larıyla çalışılır. Cert seçimi
 * token üzerinden CKA_LABEL / X.509 serial / SHA-1 thumbprint match'i ile yapılır; aynı CKA_ID
 * üzerindeki birden çok private key, {@code CKA_SIGN=TRUE} + {@code CKA_KEY_TYPE} eşlemesiyle
 * ayrıştırılır (SIGN0 seçilir, SIGN1 elenir).
 *
 * <h2>Server-agent paritesi</h2>
 *
 * <p>Kardeş proje {@code mersel-dss-server-signer-java} aynı bağımlılığı HSM akışı için kullanır
 * ({@code IaikPkcs11Module}). Agent geçişi tek bağımlılık ve aynı kalıpla yapıldı; iki proje aynı
 * PKCS#11 katmanını paylaşır.
 *
 * <h2>Yaşam döngüsü</h2>
 *
 * <pre>
 *     try (IaikPkcs11Signer s = IaikPkcs11Signer.open(libPath, pin)) {
 *         NativeSigningKey key = s.findSigningKey("a2c3dfb6572a06");
 *         byte[] rawSig = s.sign(key, IaikPkcs11Signer.CKM_ECDSA, sha384OfTbs);
 *         // ... XAdES DOM enjeksiyonu ...
 *     } // closeAllSessions (logout dahil) — PKCS11Module cached, finalize JVM shutdown'da
 * </pre>
 *
 * <p>Thread-safe değildir; her imzalama akışı kendi instance'ını açmalı. Aynı PKCS#11 lib (`{@code
 * libakisp11.dylib}`) için {@link PKCS11Module} JVM yaşam boyu tek instance — {@code
 * CKR_CRYPTOKI_ALREADY_INITIALIZED} regresyonunu önlemek için module-level cache'lenir.
 *
 * <h2>JDK 17+ uyumu</h2>
 *
 * <p>xipki wrapper'ı JDK 9+'da modüller sistemi tarafından encapsulated olan {@code
 * jdk.crypto.cryptoki} modülünden hiçbir sınıfa ihtiyaç duymaz; sadece kendi JNI bridge'ini (jar
 * içinde bundled {@code natives/{unix,windows}/...libpkcs11wrapper}) kullanır. {@code add-exports}
 * JVM argümanı gerekmez.
 */
public final class IaikPkcs11Signer implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(IaikPkcs11Signer.class);

  /** PKCS#11 v2.40 — sık kullanılan key type sabitleri. */
  public static final long CKK_RSA = 0x00000000L;

  public static final long CKK_EC = 0x00000003L;

  /** Sık kullanılan mekanizmalar (parametre-siz). */
  public static final long CKM_RSA_PKCS = 0x00000001L;

  public static final long CKM_ECDSA = 0x00001041L;
  public static final long CKM_SHA256_RSA_PKCS = 0x00000040L;
  public static final long CKM_SHA384_RSA_PKCS = 0x00000041L;
  public static final long CKM_SHA512_RSA_PKCS = 0x00000042L;
  public static final long CKM_ECDSA_SHA256 = 0x00001044L;
  public static final long CKM_ECDSA_SHA384 = 0x00001045L;
  public static final long CKM_ECDSA_SHA512 = 0x00001046L;

  /** PKCS#11 v2.40 — sertifika nesnesi attribute'ları (xipki sembol değil long ID gerekir). */
  private static final long CKA_LABEL = 0x00000003L;

  private static final long CKA_ID = 0x00000102L;
  private static final long CKA_VALUE = 0x00000011L;
  private static final long CKA_KEY_TYPE = 0x00000100L;
  private static final long CKA_SIGN = 0x00000108L;

  /**
   * {@code C_FindObjects} çağrılarında single-shot upper bound. PKCS#11 spec'i {@code long}
   * dönebilirse de pratikte kart başına birkaç yüz objeyi geçmez; {@code Integer.MAX_VALUE} aşırı
   * ve bazı sürücülerde sorun çıkarabilir.
   */
  private static final int MAX_OBJECT_LIST = 1000;

  /**
   * Lib path → cached {@link ModuleEntry}. {@code C_Initialize} aynı lib için iki kez çağrılırsa
   * {@code CKR_CRYPTOKI_ALREADY_INITIALIZED} döner; bu cache'le önlenir. JVM shutdown hook sahiplik
   * bizdeyse module'leri finalize eder.
   *
   * <p>Each entry hangi modda init edildiğini de tutar — AKİS NULL-args fallback'i {@code
   * singleThreaded=true} işaretler; {@link PKCS11Token} oluştururken {@code numSessions=1} verilir
   * (PKCS#11 v2.40 §5.4 gereği NULL-args mode multi-thread garantisi vermez).
   */
  private static final Map<String, ModuleEntry> MODULE_CACHE =
      new ConcurrentHashMap<String, ModuleEntry>();

  /**
   * Operatör için escape hatch: {@code PKCS11_NULL_INIT_ARGS=true} env var (veya {@code
   * -Dpkcs11.nullInitArgs=true} system property) verildiğinde standart {@code module.initialize()}
   * denenmeden doğrudan NULL-args yoluna gidilir. Auto-detect zaten CKR_ARGUMENTS_BAD'da devreye
   * girer; bu bayrak trial-and-error'u atlatmak isteyen operatörler için.
   *
   * <p>Aynı isim ve davranışla server projesinde {@code
   * SignatureServiceConfiguration#pkcs11NullInitArgs} olarak mevcut.
   */
  private static final boolean FORCE_NULL_INIT_ARGS = readForceNullInitArgsFlag();

  static {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                new Runnable() {
                  @Override
                  public void run() {
                    for (Map.Entry<String, ModuleEntry> entry : MODULE_CACHE.entrySet()) {
                      ModuleEntry me = entry.getValue();
                      if (me.owned) {
                        try {
                          me.module.finalize(null);
                        } catch (Throwable t) {
                          // Shutdown — sessiz; logger destroy edilmiş olabilir.
                        }
                      }
                    }
                    MODULE_CACHE.clear();
                  }
                },
                "iaik-pkcs11-shutdown"));
  }

  private static boolean readForceNullInitArgsFlag() {
    String envVal = System.getenv("PKCS11_NULL_INIT_ARGS");
    if (envVal != null && (envVal.equalsIgnoreCase("true") || envVal.equals("1"))) {
      return true;
    }
    String propVal = System.getProperty("pkcs11.nullInitArgs");
    return propVal != null && (propVal.equalsIgnoreCase("true") || propVal.equals("1"));
  }

  private final PKCS11Module module;
  private final PKCS11Token token;
  private boolean closed;

  private IaikPkcs11Signer(PKCS11Module module, PKCS11Token token) {
    this.module = module;
    this.token = token;
  }

  /**
   * Token'a R/W session açar ve {@code CKU_USER} olarak {@code C_Login} yapar. Birden fazla slot
   * varsa içinde token bulunan ilk slot kullanılır. Aynı lib path için {@link PKCS11Module} JVM
   * yaşam boyu tek instance'tır (cache); {@link PKCS11Token} her açma için yeni.
   *
   * @throws Pkcs11LibraryException kütüphane initialize edilemediğinde / slot bulunamadığında
   * @throws Pkcs11AuthException PIN reddi (CKR_PIN_INCORRECT, CKR_PIN_LOCKED, ...)
   * @throws SmartCardException slot listesinde token yoksa (kart çekilmiş veya okuyucu boş)
   */
  public static IaikPkcs11Signer open(java.nio.file.Path libraryPath, String pin) {
    return open(libraryPath, pin, null);
  }

  /**
   * {@link #open(java.nio.file.Path, String)} ile aynı; ek olarak seçilen PC/SC okuyucu adını
   * ({@code terminalName}) alır ve aynı kütüphanede birden çok token-present slot varsa açıklaması
   * okuyucu adıyla eşleşen slot'u seçer (iki gerçek kart senaryosu). Eşleşme yoksa ilk
   * token-present slot'a düşülür.
   */
  public static IaikPkcs11Signer open(
      java.nio.file.Path libraryPath, String pin, String terminalName) {
    if (libraryPath == null) {
      throw new IllegalArgumentException("libraryPath null olamaz.");
    }
    // BouncyCastle pozisyonunu sabit tut — xades4j path'i bu metoddan ayrı çalışsa da JCA chain
    // tutarlı olsun (PadesService software fallback'leri BC'ye sırt verir).
    BouncyCastleSetup.ensureRegistered();

    ModuleEntry entry;
    try {
      entry = openOrGetModule(libraryPath.toString());
    } catch (IOException ioe) {
      throw new Pkcs11LibraryException(
          "PKCS#11 kütüphanesi açılamadı (" + libraryPath + "): " + ioe.getMessage(), ioe);
    } catch (PKCS11Exception p11e) {
      throw new Pkcs11LibraryException(
          "PKCS#11 modülü initialize edilemedi (" + libraryPath + "): " + p11e.getMessage(), p11e);
    }
    PKCS11Module module = entry.module;

    Slot[] slots;
    try {
      slots = module.getSlotList(true);
    } catch (PKCS11Exception p11e) {
      throw new Pkcs11LibraryException(
          "C_GetSlotList başarısız (" + libraryPath + "): " + p11e.getMessage(), p11e);
    }
    if (slots == null || slots.length == 0) {
      throw new SmartCardException("PKCS#11 kütüphanesi için takılı token yok: " + libraryPath);
    }

    char[] pinChars = pin == null ? new char[0] : pin.toCharArray();
    org.xipki.pkcs11.wrapper.Token tokenObj =
        selectSlot(slots, terminalName, libraryPath).getToken();

    PKCS11Token p11Token;
    try {
      // AKİS NULL-args modu: PKCS#11 v2.40 §5.4 gereği kütüphane thread-unsafe sayılır;
      // PKCS11Token pool'unu numSessions=1'e indir. Akıllı kart donanımı zaten paralel oturum
      // kaldırmaz → kullanıcı için görünür performans kaybı yok.
      if (entry.singleThreaded) {
        p11Token = new PKCS11Token(tokenObj, false, pinChars, Integer.valueOf(1));
      } else {
        // PKCS11Token(token, readOnly=false, pin) ctor login dahil eder.
        p11Token = new PKCS11Token(tokenObj, false, pinChars);
      }
    } catch (TokenException te) {
      throw mapLoginFailure(te);
    } finally {
      // PIN'i wipe et — xipki kendi kopyasını tutar.
      Arrays.fill(pinChars, '\0');
    }
    return new IaikPkcs11Signer(module, p11Token);
  }

  /**
   * İçinde <b>token (kart) takılı</b> olan ilk slot'un PKCS#11 {@code slotID}'sini ({@code
   * CK_SLOT_ID}) döndürür. SunPKCS11 config'ine {@code slot = <id>} satırı yazmak için kullanılır.
   *
   * <h3>Neden gerekli — "çok sürücülü firma" patolojisi</h3>
   *
   * <p>{@code SunPKCS11}, config'inde {@code slot} / {@code slotListIndex} verilmezse default
   * olarak {@code C_GetSlotList()}'in <b>0. slot'unu</b> hedefler. Bir makinede birden çok akıllı
   * kart sürücüsü kuruluyken (Aladdin VR Handler, Rainbow iKey Virtual Reader, ...) bu liste <b>boş
   * sanal okuyucuları</b> da içerir. Gerçek kart 0. slot'ta değilse SunPKCS11 token bulamaz;
   * provider hiçbir algoritma register etmez ve {@code KeyStore.getInstance("PKCS11", provider)} şu
   * hatayı verir:
   *
   * <pre>
   * NoSuchAlgorithmException: no such algorithm: PKCS11 for provider SunPKCS11-...
   *   └─ KeyStoreException: PKCS11 not found
   * </pre>
   *
   * <p>Bu metod {@code getSlotList(true)} (yalnız token-present slotlar) ile gerçek kartın slot'unu
   * tespit eder; çağıran SunPKCS11'i o slot'a kilitleyerek phantom okuyuculardan etkilenmez. {@link
   * #open}'ın izlediği "ilk token-present slot" mantığıyla birebir aynıdır; iki yol aynı kartı
   * seçer.
   *
   * <h3>Maliyet</h3>
   *
   * <p>xipki {@link PKCS11Module} lib başına JVM-ömrü tek instance olarak cache'lendiği için (bkz.
   * {@link #MODULE_CACHE}) bu çağrı ek bir {@code C_Initialize} maliyeti getirmez; modül daha sonra
   * IAIK native imza yolunda yeniden kullanılır.
   *
   * <h3>Hata toleransı</h3>
   *
   * <p>Best-effort: native lib yüklenemez ({@code UnsatisfiedLinkError}), modül init edilemez veya
   * slot okunamazsa boş döner — çağıran eski davranışa (slot satırsız config, yani SunPKCS11
   * default slot 0) düşer. Bu metod <b>hiçbir zaman</b> imza/PIN akışını devirmez.
   *
   * @param libraryPath PKCS#11 kütüphane yolu ({@code null} ise boş döner)
   * @return token-present ilk slot'un {@code slotID}'si; tespit edilemezse {@link
   *     OptionalLong#empty()}
   */
  public static OptionalLong findTokenPresentSlotId(java.nio.file.Path libraryPath) {
    return findTokenPresentSlotId(libraryPath, null);
  }

  /**
   * {@link #findTokenPresentSlotId(java.nio.file.Path)} ile aynı; ek olarak seçilen PC/SC okuyucu
   * adını ({@code terminalName}) alır. Aynı kütüphanede birden çok token-present slot varsa (iki
   * gerçek kart) açıklaması okuyucu adıyla eşleşen slot'un {@code slotID}'sini döndürür; eşleşme
   * yoksa ilk token-present slot'a düşülür. Böylece hem "boş sanal okuyucu slot 0'ı kapıyor" (tek
   * kart) hem de "iki gerçek kart" senaryosu doğru kartı seçer.
   *
   * @param libraryPath PKCS#11 kütüphane yolu ({@code null} ise boş döner)
   * @param terminalName kullanıcının seçtiği PC/SC okuyucu adı ({@code null}/boş ise ilk
   *     token-present slot)
   * @return seçilen slot'un {@code slotID}'si; tespit edilemezse {@link OptionalLong#empty()}
   */
  public static OptionalLong findTokenPresentSlotId(
      java.nio.file.Path libraryPath, String terminalName) {
    if (libraryPath == null) {
      return OptionalLong.empty();
    }
    try {
      ModuleEntry entry = openOrGetModule(libraryPath.toString());
      Slot[] slots = entry.module.getSlotList(true); // tokenPresent=true → boş okuyucular elenir
      if (slots == null || slots.length == 0) {
        log.debug(
            "Token-present slot bulunamadı (lib={}); slot satırsız config'e düşülecek.",
            libraryPath);
        return OptionalLong.empty();
      }
      long slotId = selectSlot(slots, terminalName, libraryPath).getSlotID();
      log.debug(
          "Token-present slot tespit edildi (lib={}): slotID={} ({} aday slot).",
          libraryPath,
          slotId,
          slots.length);
      return OptionalLong.of(slotId);
    } catch (Throwable t) {
      // UnsatisfiedLinkError / PKCS11Exception / IOException dahil her şeyi yut. Slot tespiti
      // yalnızca SunPKCS11'i doğru okuyucuya yönlendirmek için bir iyileştirmedir; başarısızsa
      // çağıran slot satırsız config ile (eski davranış) güvenle devam eder.
      log.debug(
          "Token-present slot tespiti başarısız (lib={}): {} — slot satırsız config'e düşülecek.",
          libraryPath,
          t.getClass().getSimpleName() + ": " + t.getMessage());
      return OptionalLong.empty();
    }
  }

  /**
   * Token-present slot listesinden imza için kullanılacak slot'u seçer.
   *
   * <ul>
   *   <li>{@code terminalName} verilmemişse veya tek slot varsa → {@code slots[0]} (eski davranış;
   *       boş sanal okuyucular {@code getSlotList(true)} tarafından zaten elenmiştir).
   *   <li>{@code terminalName} verilmiş ve birden çok token-present slot varsa (iki gerçek kart) →
   *       {@code SlotInfo.getSlotDescription()} (PKCS#11 v2.40 §3.2: PC/SC tabanlı kütüphanelerde
   *       okuyucu adıdır) seçilen okuyucu adıyla eşleşen slot. Eşleşme yoksa {@code slots[0]}'a
   *       düşülür ve uyarı loglanır (yanlış kart riski operatöre görünür olsun diye).
   * </ul>
   */
  private static Slot selectSlot(Slot[] slots, String terminalName, java.nio.file.Path libForLog) {
    if (terminalName != null && !terminalName.trim().isEmpty() && slots.length > 1) {
      Slot matched = matchSlot(slots, terminalName);
      if (matched != null) {
        log.info(
            "terminalName='{}' eşleşen slot seçildi → slotID={} (lib={}).",
            terminalName,
            matched.getSlotID(),
            libForLog);
        return matched;
      }
      log.warn(
          "terminalName='{}' hiçbir token-present slot açıklamasıyla eşleşmedi; {} aday slot var,"
              + " ilki seçiliyor (lib={}). İki gerçek kart takılıysa yanlış kart seçilebilir —"
              + " okuyucu adı ile slot açıklaması uyumunu kontrol edin.",
          terminalName,
          slots.length,
          libForLog);
    }
    return slots[0];
  }

  /**
   * Verilen kütüphanedeki token-present slot'lar arasından açıklaması ({@code
   * SlotInfo.getSlotDescription()}, PC/SC okuyucu adı) {@code terminalName} ile eşleşen slot'un
   * {@code slotID}'sini döndürür. <b>Yalnız kesin eşleşmede</b> dolu döner; {@code terminalName}
   * boşsa, eşleşme yoksa veya native katman patlarsa {@link OptionalLong#empty()} döner.
   *
   * <p>Sertifika listeleme ({@link Pkcs11PublicCertificateReader}) gibi "ya doğru karta daralt ya
   * da olduğu gibi bırak" semantiği isteyen akışlar için. (İmza yolu eşleşme yoksa ilk
   * token-present slot'a düşer — bkz. {@link #selectSlot}; listeleme ise eşleşme yoksa tüm slotları
   * okumaya devam eder, kullanıcının kartını gizlememek için.)
   */
  public static OptionalLong matchSlotIdByTerminal(
      java.nio.file.Path libraryPath, String terminalName) {
    if (libraryPath == null || terminalName == null || terminalName.trim().isEmpty()) {
      return OptionalLong.empty();
    }
    try {
      ModuleEntry entry = openOrGetModule(libraryPath.toString());
      Slot[] slots = entry.module.getSlotList(true);
      if (slots == null || slots.length == 0) {
        return OptionalLong.empty();
      }
      Slot matched = matchSlot(slots, terminalName);
      if (matched == null) {
        log.debug(
            "matchSlotIdByTerminal: terminalName='{}' hiçbir slot açıklamasıyla eşleşmedi"
                + " ({} aday slot, lib={}).",
            terminalName,
            slots.length,
            libraryPath);
        return OptionalLong.empty();
      }
      log.debug(
          "matchSlotIdByTerminal: terminalName='{}' → slotID={} (lib={}).",
          terminalName,
          matched.getSlotID(),
          libraryPath);
      return OptionalLong.of(matched.getSlotID());
    } catch (Throwable t) {
      log.debug(
          "matchSlotIdByTerminal başarısız (lib={}): {}",
          libraryPath,
          t.getClass().getSimpleName() + ": " + t.getMessage());
      return OptionalLong.empty();
    }
  }

  /**
   * Slot dizisinden açıklaması {@code terminalName} ile eşleşeni bulur (normalize: trim + tek
   * boşluk + küçük harf; tam/contains iki yönlü). Eşleşme yoksa {@code null}. Loglama yapmaz;
   * çağıran kendi bağlamına göre info/warn/debug loglar.
   */
  private static Slot matchSlot(Slot[] slots, String terminalName) {
    if (slots == null || terminalName == null || terminalName.trim().isEmpty()) {
      return null;
    }
    String wanted = normalizeReaderName(terminalName);
    for (Slot slot : slots) {
      String desc;
      try {
        desc = slot.getSlotInfo().getSlotDescription();
      } catch (Throwable t) {
        continue; // slot info okunamadı — sıradakine bak
      }
      String got = normalizeReaderName(desc);
      if (!got.isEmpty() && (got.equals(wanted) || got.contains(wanted) || wanted.contains(got))) {
        return slot;
      }
    }
    return null;
  }

  /**
   * PC/SC okuyucu adı ↔ slot açıklaması karşılaştırması için normalize (trim + tek boşluk + lc).
   */
  private static String normalizeReaderName(String s) {
    if (s == null) {
      return "";
    }
    return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  /**
   * Karttaki tüm X.509 sertifikalarını tarar; identifier ile eşleşeni döner. Identifier sırası:
   *
   * <ol>
   *   <li>PKCS#11 {@code CKA_LABEL}
   *   <li>{@code CKA_ID} (hex)
   *   <li>X.509 serial number (büyük harf hex; baştaki 0 normalleştirilir)
   *   <li>SHA-1 thumbprint
   * </ol>
   *
   * <p>Cert bulunduktan sonra aynı CKA_ID üzerindeki private key'ler {@code CKA_KEY_TYPE} (cert
   * public key tipine eşle) + {@code CKA_SIGN=TRUE} filtreleriyle ayrıştırılır.
   *
   * @throws CertificateLookupException eşleşen cert yoksa veya imza-yetkili key yoksa
   */
  public NativeSigningKey findSigningKey(String identifier) {
    if (identifier == null || identifier.trim().isEmpty()) {
      throw new IllegalArgumentException("identifier boş olamaz.");
    }
    if (closed) {
      throw new IllegalStateException("IaikPkcs11Signer kapalı.");
    }
    String trimmed = identifier.trim();
    String normalisedHex =
        trimmed.replaceAll("\\s+", "").replaceFirst("^0x", "").toUpperCase(Locale.ROOT);
    BigInteger asBigInt;
    try {
      asBigInt = new BigInteger(normalisedHex, 16);
    } catch (NumberFormatException nfe) {
      asBigInt = null;
    }

    CertificateFactory cf;
    try {
      cf = CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      throw new CertificateLookupException(
          "X.509 CertificateFactory bulunamadı: " + e.getMessage(), e);
    }

    List<CertEntry> certs = scanCertificates(cf);
    if (certs.isEmpty()) {
      throw new CertificateLookupException("Kartta CKO_CERTIFICATE nesnesi yok.");
    }

    CertEntry match = null;
    for (CertEntry c : certs) {
      if (c.label != null && c.label.equalsIgnoreCase(trimmed)) {
        match = c;
        break;
      }
      if (c.idHex != null && c.idHex.equalsIgnoreCase(normalisedHex)) {
        match = c;
        break;
      }
      String serialHex = c.certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
      if (asBigInt != null && c.certificate.getSerialNumber().equals(asBigInt)) {
        match = c;
        break;
      }
      if (serialHex.equalsIgnoreCase(normalisedHex)) {
        match = c;
        break;
      }
      String thumb = sha1HexThumbprint(c.certificate);
      if (thumb != null && thumb.equalsIgnoreCase(normalisedHex)) {
        match = c;
        break;
      }
    }
    if (match == null) {
      throw new CertificateLookupException(
          "Kartta '" + identifier + "' tanımlayıcısıyla eşleşen sertifika yok.");
    }

    long expectedKeyType = expectedKeyType(match.certificate);
    long privateKeyHandle = locatePrivateKey(match.idBytes, match.label, expectedKeyType);

    return new NativeSigningKey(
        match.certificate,
        Arrays.<X509Certificate>asList(match.certificate),
        privateKeyHandle,
        expectedKeyType);
  }

  /**
   * {@code C_SignInit(mech, keyHandle) + C_Sign(data)}. Parametre-siz mekanizmalar (CKM_RSA_PKCS,
   * CKM_ECDSA, CKM_SHA256_RSA_PKCS, ...) için doğrudan; PSS / OAEP gibi parametreli mekanizmalar
   * gerektiğinde {@link Mechanism} ctor'ının {@link org.xipki.pkcs11.wrapper.params.CkParams}
   * overload'u kullanılmalı.
   *
   * @return ham PKCS#11 imza byte'ları (RSASSA-PKCS#1 v1.5 için DER kodlanmamış, ECDSA için R||S
   *     fixed-width concat)
   */
  public byte[] sign(NativeSigningKey key, long pkcs11Mechanism, byte[] data) {
    if (closed) {
      throw new IllegalStateException("IaikPkcs11Signer kapalı.");
    }
    if (key == null) throw new IllegalArgumentException("key null olamaz.");
    if (data == null) throw new IllegalArgumentException("data null olamaz.");
    try {
      return token.sign(new Mechanism(pkcs11Mechanism), key.privateKeyHandle, data);
    } catch (TokenException te) {
      throw new Pkcs11LibraryException(
          "C_Sign başarısız (mech=0x" + Long.toHexString(pkcs11Mechanism) + "): " + te.getMessage(),
          te);
    }
  }

  private List<CertEntry> scanCertificates(CertificateFactory cf) {
    AttributeVector template = AttributeVector.newX509Certificate();
    long[] handles;
    try {
      handles = token.findObjects(template, MAX_OBJECT_LIST);
    } catch (TokenException te) {
      throw new Pkcs11LibraryException("C_FindObjects (cert) başarısız: " + te.getMessage(), te);
    }
    if (handles == null || handles.length == 0) {
      return new ArrayList<CertEntry>();
    }
    List<CertEntry> result = new ArrayList<CertEntry>(handles.length);
    for (long handle : handles) {
      CertEntry e = readCertEntry(handle, cf);
      if (e != null) {
        result.add(e);
      }
    }
    return result;
  }

  private CertEntry readCertEntry(long handle, CertificateFactory cf) {
    try {
      AttributeVector attrs = token.getAttrValues(handle, CKA_LABEL, CKA_ID, CKA_VALUE);
      String label = trimToNull(attrs.label());
      byte[] idBytes = attrs.id();
      byte[] derValue = attrs.value();
      if (derValue == null || derValue.length == 0) {
        return null;
      }
      X509Certificate cert =
          (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(derValue));
      return new CertEntry(handle, label, idBytes, toHex(idBytes), cert);
    } catch (TokenException | CertificateException e) {
      log.debug("Cert objesi okunamadı (handle={}): {}", handle, e.getMessage());
      return null;
    }
  }

  private long locatePrivateKey(byte[] certIdBytes, String certLabel, long expectedKeyType) {
    // Önce CKA_ID match'iyle private key tarayalım — IAIK template-filter ile native side daraltır.
    AttributeVector keyTemplate = AttributeVector.newPrivateKey();
    if (certIdBytes != null && certIdBytes.length > 0) {
      keyTemplate.id(certIdBytes);
    }
    long[] handles;
    try {
      handles = token.findObjects(keyTemplate, MAX_OBJECT_LIST);
    } catch (TokenException te) {
      throw new Pkcs11LibraryException(
          "C_FindObjects (private key) başarısız: " + te.getMessage(), te);
    }
    if (handles == null || handles.length == 0) {
      // Filtreleyince eşleşme olmadıysa: template'i gevşetip cert label'a düş
      try {
        handles = token.findObjects(AttributeVector.newPrivateKey(), MAX_OBJECT_LIST);
      } catch (TokenException te) {
        throw new Pkcs11LibraryException(
            "C_FindObjects (private key, fallback) başarısız: " + te.getMessage(), te);
      }
    }
    if (handles == null || handles.length == 0) {
      throw new CertificateLookupException("Kartta CKO_PRIVATE_KEY nesnesi yok.");
    }

    Long firstSignableMatch = null;
    Long firstAnyMatch = null;

    for (long handle : handles) {
      KeyAttrs ka = readPrivateKeyAttrs(handle);
      boolean idMatch = certIdBytes != null && Arrays.equals(certIdBytes, ka.id);
      boolean labelMatch = certLabel != null && certLabel.equals(ka.label);
      boolean keyTypeOk = expectedKeyType < 0 || expectedKeyType == ka.keyType;

      if (!(idMatch || labelMatch)) {
        continue;
      }
      if (firstAnyMatch == null && keyTypeOk) {
        firstAnyMatch = handle;
      }
      if (firstSignableMatch == null && keyTypeOk && ka.sign) {
        firstSignableMatch = handle;
      }
    }

    if (firstSignableMatch != null) {
      return firstSignableMatch;
    }
    if (firstAnyMatch != null) {
      log.warn(
          "CKA_SIGN=TRUE filtresi eşleşmedi; CKA_ID/CKA_LABEL eşleşen ilk private key kullanılıyor"
              + " (key_type uyumlu).");
      return firstAnyMatch;
    }
    // Cert ile eşleşmeyen hiçbir private key yok: rastgele bir handle dönmek yerine
    // hata fırlat. Dual-key (SIGN0+SIGN1) kartlarda yanlış anahtarla imza üretme riski.
    throw new CertificateLookupException(
        "Sertifika ile eşleşen (CKA_ID/CKA_LABEL) imzalanabilir private key bulunamadı."
            + " Karttaki anahtar/sertifika eşlemini (CKA_ID) kontrol edin.");
  }

  private KeyAttrs readPrivateKeyAttrs(long handle) {
    KeyAttrs ka = new KeyAttrs();
    try {
      AttributeVector attrs =
          token.getAttrValues(handle, CKA_LABEL, CKA_ID, CKA_KEY_TYPE, CKA_SIGN);
      ka.label = trimToNull(attrs.label());
      ka.id = attrs.id();
      Long kt = attrs.keyType();
      ka.keyType = kt == null ? -1L : kt;
      Boolean sign = attrs.sign();
      ka.sign = sign != null && sign;
    } catch (TokenException e) {
      log.debug("Private key attr okuma hata (handle={}): {}", handle, e.getMessage());
    }
    return ka;
  }

  private static long expectedKeyType(X509Certificate cert) {
    if (cert == null) return -1L;
    String algo = cert.getPublicKey().getAlgorithm();
    if (algo == null) return -1L;
    String up = algo.toUpperCase(Locale.ROOT);
    if (up.contains("EC")) return CKK_EC;
    if (up.contains("RSA")) return CKK_RSA;
    return -1L;
  }

  private static String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static String toHex(byte[] data) {
    if (data == null || data.length == 0) return null;
    StringBuilder sb = new StringBuilder(data.length * 2);
    for (byte b : data) sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
    return sb.toString();
  }

  private static String sha1HexThumbprint(X509Certificate cert) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      return toHex(md.digest(cert.getEncoded()));
    } catch (Exception e) {
      return null;
    }
  }

  private static synchronized ModuleEntry openOrGetModule(String libPath)
      throws IOException, PKCS11Exception {
    ModuleEntry cached = MODULE_CACHE.get(libPath);
    if (cached != null) {
      return cached;
    }
    PKCS11Module module = PKCS11Module.getInstance(libPath);
    InitOutcome outcome = initializeIdempotent(module, FORCE_NULL_INIT_ARGS, libPath);
    ModuleEntry entry = new ModuleEntry(module, outcome.owned, outcome.singleThreaded);
    MODULE_CACHE.put(libPath, entry);
    return entry;
  }

  /**
   * Aynı PKCS#11 kütüphanesi için process içinde yalnızca <b>tek bir</b> {@code C_Initialize}
   * çağrısı yapılabilir (PKCS#11 v2.40 §11.4 — process-global state, ref-count yok). Agent'ta
   * sertifika listeleme ({@link Pkcs11PublicCertificateReader}) ve xades4j imza akışı SunPKCS11
   * üzerinden, IAIK fallback yolu xipki üzerinden init eder; iki katman aynı .dylib'i paylaşır. Bu
   * enum, hangi yolun ALREADY_INITIALIZED'i no-op olarak ele alacağını anlamlandırmak için.
   *
   * <ol>
   *   <li>{@link #FRESH} — bu çağrıda Cryptoki'yi <b>biz</b> başlattık, finalize sahipliği bizde.
   *   <li>{@link #SHARED} — kütüphane başka bir bileşen (genellikle SunPKCS11) tarafından önceden
   *       init edilmiş; biz finalize çağırmamalıyız (paylaşımlı state korunur).
   * </ol>
   */
  enum InitOwnership {
    FRESH,
    SHARED
  }

  /**
   * Tek noktadan idempotent init: standart {@code module.initialize()} → ARGS_BAD'da NULL-args
   * fallback → her iki yolda da {@code CKR_CRYPTOKI_ALREADY_INITIALIZED}'i ölümcül hata değil "biz
   * init etmedik, paylaşımlı state'i kullanıyoruz" sinyali olarak işle.
   *
   * <h3>Karar matrisi</h3>
   *
   * <pre>
   *   forceNullInitArgs=true                 → NULL-args yolu     (singleThreaded=TRUE)
   *   module.initialize() OK                 → standart success   (singleThreaded=FALSE)
   *   module.initialize() ALREADY_INITIALIZED → SHARED no-op       (singleThreaded=FALSE)
   *   module.initialize() ARGS_BAD           → NULL-args fallback (singleThreaded=TRUE)
   *   NULL-args OK                           → FRESH success
   *   NULL-args ALREADY_INITIALIZED          → SHARED no-op       (sahada en sık vaka)
   *   diğer CKR_*                            → caller'a propagate
   * </pre>
   *
   * <p>Trade-off: NULL-args = PKCS#11 spec §5.4 gereği kütüphane thread-unsafe sayılır; çağıran
   * {@link PKCS11Token} pool'unu {@code numSessions=1}'e indirir. Akıllı kart donanımı zaten
   * paralel oturum kaldırmaz, görünür performans kaybı yok.
   *
   * <p>Server projesinde {@code IaikPkcs11Module#initializeIdempotent} kalıbı tek-init varsayımıyla
   * yazılmıştır (HSM tarafında SunPKCS11 yok); agent hibrit yapı için bu yolda ek olarak NULL-args
   * + ALREADY_INITIALIZED kombinasyonunu da ele almak zorundayız.
   */
  private static InitOutcome initializeIdempotent(
      PKCS11Module module, boolean forceNullInitArgs, String libPath) throws PKCS11Exception {
    if (forceNullInitArgs) {
      log.info(
          "PKCS11_NULL_INIT_ARGS=true → standart C_Initialize denenmeden doğrudan NULL-args"
              + " yoluna gidiliyor (AKİS / TÜBİTAK uyumluluk modu, lib={}).",
          libPath);
      InitOwnership ownership = initializeWithNullArgs(module, libPath);
      return new InitOutcome(ownership == InitOwnership.FRESH, true /* singleThreaded */);
    }
    try {
      module.initialize();
      return new InitOutcome(true, false);
    } catch (PKCS11Exception e) {
      long code = e.getErrorCode();
      if (code == PKCS11Constants.CKR_CRYPTOKI_ALREADY_INITIALIZED) {
        log.info(
            "PKCS#11 modülü önceden initialize edilmiş ({}); paylaşımlı Cryptoki state"
                + " kullanılıyor (genelde aynı process'te SunPKCS11 ilk init'i yaptı). close()"
                + " üzerinde finalize çağrılmayacak.",
            libPath);
        // Standart yol başarısız oldu ama Cryptoki canlı; xipki vendor behaviours / moduleInfo
        // henüz set edilmedi (initialize() patlamadan tamamlayamadı). Reflection ile populate
        // edelim — yoksa initVendor.conf eşleşmesi yapılmayacak ve EC point fix gibi
        // vendor-behaviour'lar sessizce devre dışı kalacak.
        try {
          populateModuleInfoAndVendor(module);
        } catch (Exception populateFail) {
          log.debug(
              "ALREADY_INITIALIZED sonrası moduleInfo populate başarısız (kritik değil): {}",
              populateFail.getMessage());
        }
        return new InitOutcome(false, false);
      }
      if (code == PKCS11Constants.CKR_ARGUMENTS_BAD) {
        // AKİS macOS / Linux sürücüsünün klasik bug'ı: standart
        // C_Initialize(CK_C_INITIALIZE_ARGS{flags=CKF_OS_LOCKING_OK}) reddedilir; yalnız
        // C_Initialize(NULL) kabul edilir. Kütüphane bu noktada Cryptoki'yi henüz init
        // etmemiş olabilir (yarı yolda red), VEYA SunPKCS11 önceden init etmiş olabilir
        // (AKIS args-bad'i state-ten bağımsız olarak fail-fast yapar). NULL-args yolu her
        // iki vakayı da deterministik şekilde çözer.
        log.warn(
            "Standart C_Initialize CKR_ARGUMENTS_BAD ile reddedildi (genellikle TÜBİTAK AKİS"
                + " macOS/Linux sürücüsü, lib={}). NULL-args fallback deneniyor.",
            libPath);
        InitOwnership ownership = initializeWithNullArgs(module, libPath);
        return new InitOutcome(ownership == InitOwnership.FRESH, true /* singleThreaded */);
      }
      throw e;
    }
  }

  /**
   * xipki {@link PKCS11Module} private {@code pkcs11} alanını reflection ile alır; alttaki IAIK
   * {@code PKCS11Implementation.C_Initialize(null, true)} çağrısını doğrudan yapar. Ardından {@code
   * moduleInfo} ve {@code initVendor()} adımlarını best-effort reflection ile çalıştırır.
   *
   * <p><b>{@code CKR_CRYPTOKI_ALREADY_INITIALIZED} kontratı:</b> bu method NULL-args yolunda
   * ALREADY_INITIALIZED'i bir hata olarak değil, "Cryptoki state başka bir bileşen tarafından
   * kuruldu, biz finalize çağırmamalıyız" sinyali olarak ele alır ve {@link InitOwnership#SHARED}
   * döner. Hibrit agent (SunPKCS11 + xipki aynı .dylib üzerinde) için yegane stabil davranış; aksi
   * halde sertifika listeleme ardından gelen IAIK fallback yolu AKİS macOS/Linux'ta her zaman
   * patlardı.
   *
   * @return {@link InitOwnership#FRESH} biz {@code C_Initialize}'i çağırıp başarıyla döndüğümüzde;
   *     {@link InitOwnership#SHARED} kütüphane zaten init edilmişse.
   * @throws PKCS11Exception ALREADY_INITIALIZED dışındaki herhangi bir CKR_* kodu için — bu durumda
   *     agent toplam imza akışını başlatamaz, üst katman operatöre uygun remediation mesajı verir.
   */
  private static InitOwnership initializeWithNullArgs(PKCS11Module module, String libPath)
      throws PKCS11Exception {
    Object pkcs11Impl;
    try {
      Field pkcs11Field = PKCS11Module.class.getDeclaredField("pkcs11");
      pkcs11Field.setAccessible(true);
      pkcs11Impl = pkcs11Field.get(module);
    } catch (ReflectiveOperationException reflectionFail) {
      throw new IllegalStateException(
          "ipkcs11wrapper NULL-init-args reflection setup başarısız (PKCS11Module.pkcs11"
              + " field okuma): "
              + reflectionFail.getMessage(),
          reflectionFail);
    }
    if (pkcs11Impl == null) {
      throw new IllegalStateException(
          "PKCS11Module.pkcs11 reflection alanı null döndü; ipkcs11wrapper sürümü beklenenden"
              + " farklı olabilir.");
    }

    InitOwnership ownership;
    try {
      Method cInit = pkcs11Impl.getClass().getMethod("C_Initialize", Object.class, boolean.class);
      try {
        // Object[] cast explicit — varargs ambiguity'den kaçınmak için.
        cInit.invoke(pkcs11Impl, new Object[] {null, Boolean.TRUE});
        ownership = InitOwnership.FRESH;
        log.info(
            "NULL-args C_Initialize başarılı (lib={}); Cryptoki state'i biz kurduk, finalize"
                + " sahipliği bizde, single-threaded modda çalışılacak (PKCS#11 §5.4).",
            libPath);
      } catch (InvocationTargetException ite) {
        ownership = classifyNativeInitFailure(ite.getCause(), libPath);
      } catch (ReflectiveOperationException reflectionFail) {
        throw new IllegalStateException(
            "ipkcs11wrapper C_Initialize reflection invoke başarısız: "
                + reflectionFail.getMessage(),
            reflectionFail);
      }
    } catch (PKCS11Exception | RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException(
          "ipkcs11wrapper NULL-init-args fallback yolu başarısız: " + ex.getMessage(), ex);
    }

    // ownership = FRESH veya SHARED — her iki vakada da xipki'nin moduleInfo + vendor
    // behaviours map'ini reflection ile populate ediyoruz; SHARED yolunda bu adım atlanırsa
    // findObjects EC kartlarda yanlış davranabilir (vendor.conf vendor behaviours devre dışı).
    populateModuleInfoAndVendor(module);
    return ownership;
  }

  /**
   * NULL-args yolunda alttaki IAIK {@code C_Initialize}'in fırlattığı native hatayı yorumlar. Saf
   * bir error-code classifier; package-private görünürlük unit test ({@link
   * io.mersel.dss.agent.api.services.keystore.IaikPkcs11SignerInitTest}) için.
   *
   * <ul>
   *   <li>{@code CKR_CRYPTOKI_ALREADY_INITIALIZED} → {@link InitOwnership#SHARED} (no-op başarı,
   *       Cryptoki başka bileşen tarafından init edilmiş, finalize çağrılmayacak).
   *   <li>Diğer {@code CKR_*} → high-level xipki {@link PKCS11Exception} olarak yeniden fırlat
   *       (caller {@link Pkcs11LibraryException}'a sarıp operatöre raporlar).
   *   <li>IAIK PKCS11Exception olmayan cause → {@link IllegalStateException} (programlama hatası;
   *       ipkcs11wrapper sürüm uyumsuzluğu).
   * </ul>
   */
  static InitOwnership classifyNativeInitFailure(Throwable cause, String libPath)
      throws PKCS11Exception {
    if (cause instanceof iaik.pkcs.pkcs11.wrapper.PKCS11Exception) {
      long iaikCode = ((iaik.pkcs.pkcs11.wrapper.PKCS11Exception) cause).getErrorCode();
      if (iaikCode == PKCS11Constants.CKR_CRYPTOKI_ALREADY_INITIALIZED) {
        // Sahada en sık vaka: SunPKCS11 (cert listeleme veya xades4j) önce init'i başardı.
        // Bunu hata olarak işlersek IAIK fallback hiçbir zaman çalışmaz — halbuki tam olarak
        // Cryptoki canlı olduğu için bu yola geliyoruz. SHARED ownership ile devam edip
        // downstream çağrılar (C_OpenSession, C_FindObjects, C_Sign) zaten paylaşımlı state
        // üzerinden çalışır.
        log.info(
            "NULL-args C_Initialize → CKR_CRYPTOKI_ALREADY_INITIALIZED (lib={}); paylaşımlı"
                + " Cryptoki state kullanılıyor, finalize çağrılmayacak. Hibrit"
                + " SunPKCS11+xipki yapısında beklenen davranış.",
            libPath);
        return InitOwnership.SHARED;
      }
      // Diğer CKR_* — gerçek init başarısızlığı; high-level xipki exception'a sar.
      throw new PKCS11Exception(iaikCode);
    }
    throw new IllegalStateException("Beklenmedik native C_Initialize hatası", cause);
  }

  /**
   * xipki {@link PKCS11Module#initialize()} başarılı olunca otomatik yapılan iki adımı reflection
   * ile dışarıdan tekrarlar:
   *
   * <ol>
   *   <li>{@code C_GetInfo()} → {@code moduleInfo} field'ını set et (initVendor + getInfo() her
   *       ikisi için gerekli).
   *   <li>{@code initVendor()} private metodunu çağır (vendor.conf eşleşmesi → vendor behaviours
   *       map populate).
   * </ol>
   *
   * <p>Hem fresh-NULL-args hem ALREADY_INITIALIZED yollarında çağrılır. Best-effort: hata
   * fırlatmaz, yalnız debug-loglar — vendor behaviours yokluğu RSA/ECDSA imzalama akışını bozmaz,
   * sadece SM2 / Edwards / Montgomery gibi nadir EC eğrilerinde fallback davranışı eksik kalır.
   */
  private static void populateModuleInfoAndVendor(PKCS11Module module) {
    Object pkcs11Impl;
    try {
      Field pkcs11Field = PKCS11Module.class.getDeclaredField("pkcs11");
      pkcs11Field.setAccessible(true);
      pkcs11Impl = pkcs11Field.get(module);
      if (pkcs11Impl == null) {
        return;
      }
    } catch (ReflectiveOperationException reflectionFail) {
      log.debug(
          "moduleInfo populate: PKCS11Module.pkcs11 field okunamadı: {}",
          reflectionFail.getMessage());
      return;
    }

    try {
      Method cGetInfo = pkcs11Impl.getClass().getMethod("C_GetInfo");
      Object ckInfo = cGetInfo.invoke(pkcs11Impl);
      Class<?> ckInfoClass = Class.forName("iaik.pkcs.pkcs11.wrapper.CK_INFO");
      Class<?> moduleInfoClass = Class.forName("org.xipki.pkcs11.wrapper.ModuleInfo");
      Object moduleInfo = moduleInfoClass.getConstructor(ckInfoClass).newInstance(ckInfo);
      Field moduleInfoField = PKCS11Module.class.getDeclaredField("moduleInfo");
      moduleInfoField.setAccessible(true);
      moduleInfoField.set(module, moduleInfo);
    } catch (Exception e) {
      // moduleInfo populate başarısızlığı RSA/ECDSA akışını bozmaz; vendor.conf eşleşmesi
      // için kritik ama xipki sürümleri arası kırılma ihtimaline karşı görünür kıl.
      log.warn(
          "moduleInfo best-effort populate başarısız (vendor behaviours devre dışı kalabilir):"
              + " {}",
          e.toString());
    }
    try {
      Method initVendor = PKCS11Module.class.getDeclaredMethod("initVendor");
      initVendor.setAccessible(true);
      initVendor.invoke(module);
    } catch (Exception e) {
      // initVendor başarısızlığı SM2/Edwards gibi nadir EC eğrileri için fallback'i etkiler;
      // ana RSA/ECDSA yolu çalışır ama yine bir kez warn ile görünür kılnması tanı kolaylığı
      // sağlar.
      log.warn("initVendor best-effort çağrı başarısız: {}", e.toString());
    }
  }

  private static RuntimeException mapLoginFailure(Throwable cause) {
    Pkcs11Errors.Outcome outcome = Pkcs11Errors.classify(cause);
    switch (outcome.getKind()) {
      case PIN_INCORRECT:
      case PIN_LOCKED:
      case PIN_EXPIRED:
      case PIN_INVALID_FORMAT:
        return new Pkcs11AuthException(
            outcome.getErrorCode(),
            outcome.getMessage(),
            cause,
            outcome.getPkcs11Code(),
            outcome.isLocked(),
            outcome.getAttemptsRemainingHint());
      case DEVICE_REMOVED:
        return new Pkcs11LibraryException(
            "PKCS#11 cihaz hatası (" + outcome.getPkcs11Code() + "): " + outcome.getMessage(),
            cause);
      case SESSION_BUSY:
        return new Pkcs11LibraryException("PKCS#11 oturumu meşgul: " + outcome.getMessage(), cause);
      default:
        return new Pkcs11LibraryException(
            "PKCS11Token açılamadı (C_Login dahil): "
                + (cause.getMessage() == null
                    ? cause.getClass().getSimpleName()
                    : cause.getMessage()),
            cause);
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    try {
      token.closeAllSessions();
    } catch (Throwable t) {
      log.debug("PKCS11Token.closeAllSessions uyarısı: {}", t.getMessage());
    }
    // Module finalize JVM shutdown hook'ta toplu — burada ÇAĞIRMA, başka thread'lerin aktif
    // session'larını koparırız.
  }

  /* ----------------------------- value objects ---------------------------- */

  /**
   * Native imza akışında seçilmiş cert + private key handle taşıyıcısı.
   *
   * <p>{@code Pkcs11NativeSigner} dönüş tipiyle bilerek aynı isim — XadesService gibi çağıran
   * sınıflar reflection backend'inden IAIK backend'ine geçerken sadece import değiştirir.
   */
  public static final class NativeSigningKey {
    private final X509Certificate certificate;
    private final List<X509Certificate> chain;
    private final long privateKeyHandle;
    private final long pkcs11KeyType;

    NativeSigningKey(
        X509Certificate certificate,
        List<X509Certificate> chain,
        long privateKeyHandle,
        long pkcs11KeyType) {
      this.certificate = certificate;
      this.chain = chain;
      this.privateKeyHandle = privateKeyHandle;
      this.pkcs11KeyType = pkcs11KeyType;
    }

    public X509Certificate getCertificate() {
      return certificate;
    }

    public List<X509Certificate> getCertificateChain() {
      return chain;
    }

    public long getPrivateKeyHandle() {
      return privateKeyHandle;
    }

    public long getPkcs11KeyType() {
      return pkcs11KeyType;
    }

    public boolean isEc() {
      return pkcs11KeyType == CKK_EC;
    }

    public boolean isRsa() {
      return pkcs11KeyType == CKK_RSA;
    }
  }

  private static final class CertEntry {
    final long handle;
    final String label;
    final byte[] idBytes;
    final String idHex;
    final X509Certificate certificate;

    CertEntry(
        long handle, String label, byte[] idBytes, String idHex, X509Certificate certificate) {
      this.handle = handle;
      this.label = label;
      this.idBytes = idBytes;
      this.idHex = idHex;
      this.certificate = certificate;
    }
  }

  private static final class KeyAttrs {
    String label;
    byte[] id;
    long keyType = -1L;
    boolean sign;
  }

  /**
   * Lib path başına {@link PKCS11Module} cache değeri: sahiplik + thread modu bilgisini taşır.
   * NULL-args fallback'i alındıysa {@code singleThreaded=true}; bu sayede {@link PKCS11Token}
   * pool'u {@code numSessions=1}'e iner.
   */
  private static final class ModuleEntry {
    final PKCS11Module module;
    final boolean owned;
    final boolean singleThreaded;

    ModuleEntry(PKCS11Module module, boolean owned, boolean singleThreaded) {
      this.module = module;
      this.owned = owned;
      this.singleThreaded = singleThreaded;
    }
  }

  /** {@link #initializeIdempotent} sonucu — server projesindeki adaşıyla aynı şekilde. */
  private static final class InitOutcome {
    final boolean owned;
    final boolean singleThreaded;

    InitOutcome(boolean owned, boolean singleThreaded) {
      this.owned = owned;
      this.singleThreaded = singleThreaded;
    }
  }

  /**
   * SunPKCS11'in bilinen patolojilerinden birini cause zincirinde tespit eder. Bu metot dispatch
   * katmanı (XadesService.signXmlDocument) tarafından "xades4j yolu başarısız → IAIK fallback'e
   * düş" kararı için kullanılır. Tetiklenen pattern'lar:
   *
   * <ol>
   *   <li><b>CKA_ID collision</b>: NES Bulut / Kamu SM dual-key (SIGN0 + SIGN1) kartlarında JDK 1.8
   *       {@code P11KeyStore.mapPrivateKeys()} → {@code KeyStoreException: invalid KeyStore state:
   *       found N private keys sharing CKA_ID 0x...}. JDK 9+'da relaxed; 1.8'de fix yok.
   *   <li><b>{@code CKR_USER_NOT_LOGGED_IN}</b>: AKİS / SafeSign / bazı Kamu SM sürücüleri
   *       SunPKCS11'in app-wide login state'ini session-scoped uygular. {@code KeyStore.load}
   *       açtığı P11Session login olur, {@code Signature.initSign} yeni session açtığında login
   *       state devredilmez, {@code C_SignInit} → {@code CKR_USER_NOT_LOGGED_IN}. {@code
   *       AuthProvider.login()} explicit çağrısı bile bu sürücülerde devamlı çözüm getirmiyor; tek
   *       pratik yol SunPKCS11'i atlayıp IAIK PKCS#11 wrapper'a düşmek.
   * </ol>
   *
   * <p>Cycle-safe: kendi kendine cause olan exception zincirlerinde sonsuz döngüye girmez.
   */
  public static boolean requiresIaikFallback(Throwable t) {
    if (t == null) return false;
    return io.mersel.dss.agent.api.exceptions.CauseChainExtractor.anyMatch(
        t,
        new java.util.function.Predicate<Throwable>() {
          @Override
          public boolean test(Throwable cur) {
            String msg = cur.getMessage();
            if (msg == null) return false;
            String lower = msg.toLowerCase(Locale.ROOT);
            // CKA_ID collision (JDK 1.8 P11KeyStore.mapPrivateKeys uniqueness check)
            if (lower.contains("invalid keystore state") && lower.contains("cka_id")) {
              return true;
            }
            if (lower.contains("private keys sharing") && lower.contains("cka_id")) {
              return true;
            }
            // CKR_USER_NOT_LOGGED_IN — AKİS / SafeSign session-scoped login bug'ı
            return lower.contains("ckr_user_not_logged_in");
          }
        });
  }
}
