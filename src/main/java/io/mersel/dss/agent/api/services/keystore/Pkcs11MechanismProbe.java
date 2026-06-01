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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryException;

/**
 * Bir PKCS#11 token'ının desteklediği {@code CKM_*} mekanizma listesini ve token meta bilgilerini
 * <b>PIN'siz</b> okur.
 *
 * <p>"Bazı kartlarda imzalama çalışıyor, bazılarında 'Unsupported parameters' hatası alıyoruz"
 * sorusunun cevabını veren tek araç. AKIS / SafeSign / Gemalto firmware revizyonları farklı
 * mekanizma setleri açar; xades4j default profili karta uymadığında imzalama patlar. Bu probe ile
 * {@code SignatureProfileResolver} hangi mekanizmanın olduğunu/olmadığını görür ve uygun fallback
 * stratejisini seçer.
 *
 * <h2>PIN gerektirmez</h2>
 *
 * PKCS#11 spec §10.4: {@code C_GetMechanismList} ve {@code C_GetMechanismInfo} public session'da
 * (login öncesi) çağrılabilir. {@link Pkcs11PublicCertificateReader} ile aynı yaklaşım — token
 * sayacı harcanmaz.
 *
 * <h2>Reflection kısıtlamaları</h2>
 *
 * Bazı JDK 1.8 patch sürümlerinde {@code C_GetMechanismList} private ya da modüle ait olabilir;
 * lookup başarısızsa boş bir result döneriz ({@link ProbeResult#supported}=false). Bu durum
 * frontend'e açıkça yansır; tanılamaya geçmez ama imzalama akışı yine çalışır.
 */
public final class Pkcs11MechanismProbe {

  private static final Logger log = LoggerFactory.getLogger(Pkcs11MechanismProbe.class);

  private Pkcs11MechanismProbe() {
    /* static-only */
  }

  /**
   * Verilen PKCS#11 lib'inin <b>ilk</b> token bulunan slot'undan mekanizma listesini ve token
   * info'yu okur. Hiç token yoksa {@link ProbeResult#empty(Path)} döner.
   *
   * @throws Pkcs11LibraryException reflection veya {@code C_GetSlotList} başarısızsa
   */
  public static ProbeResult probe(Path libraryPath) {
    if (libraryPath == null) {
      throw new IllegalArgumentException("libraryPath null olamaz.");
    }
    Pkcs11Reflection r = Pkcs11Reflection.load();
    Object p11 = r.getInstance(libraryPath.toString());

    long[] slotList;
    try {
      slotList = r.getSlotList(p11, true);
    } catch (ReflectiveOperationException e) {
      throw new Pkcs11LibraryException(
          "C_GetSlotList başarısız (" + libraryPath + "): " + rootMessage(e), e);
    }
    if (slotList == null || slotList.length == 0) {
      log.debug("Mekanizma probe: slot/token yok ({}).", libraryPath);
      return ProbeResult.empty(libraryPath);
    }

    long firstSlot = slotList[0];
    return probeSlot(libraryPath, firstSlot);
  }

  /**
   * Belirli bir slot için mekanizma listesini okur. {@link #probe(Path)} default olarak ilk slot'u
   * kullanır; multi-slot HSM senaryosunda doğrudan slot ID belirten bu overload'ı çağır.
   */
  public static ProbeResult probeSlot(Path libraryPath, long slotID) {
    Pkcs11Reflection r = Pkcs11Reflection.load();
    Object p11 = r.getInstance(libraryPath.toString());

    Pkcs11Reflection.TokenInfo tokenInfo = null;
    try {
      tokenInfo = r.getTokenInfo(p11, slotID);
    } catch (ReflectiveOperationException e) {
      log.debug("C_GetTokenInfo başarısız (slot={}): {}", slotID, rootMessage(e));
    } catch (Throwable t) {
      log.debug("C_GetTokenInfo throw: {}", t.getMessage());
    }

    if (!r.supportsMechanismList()) {
      log.warn(
          "PKCS11 wrapper bu JDK'da C_GetMechanismList açık değil; mekanizma listesi okunamadı.");
      return new ProbeResult(libraryPath, slotID, tokenInfo, null, false);
    }

    long[] ckmList;
    try {
      ckmList = r.getMechanismList(p11, slotID);
    } catch (ReflectiveOperationException e) {
      log.debug("C_GetMechanismList başarısız (slot={}): {}", slotID, rootMessage(e));
      return new ProbeResult(libraryPath, slotID, tokenInfo, null, false);
    } catch (Throwable t) {
      // Bazı sürücüler null check yapmadan crash eder; defansif yutalım.
      log.debug("C_GetMechanismList throw: {}", t.getMessage());
      return new ProbeResult(libraryPath, slotID, tokenInfo, null, false);
    }

    if (ckmList == null) {
      return new ProbeResult(libraryPath, slotID, tokenInfo, null, false);
    }

    List<MechanismEntry> entries = new ArrayList<MechanismEntry>(ckmList.length);
    for (long ckm : ckmList) {
      String name = Pkcs11Mechanisms.nameOf(ckm);
      Pkcs11Reflection.MechanismInfo info = null;
      try {
        info = r.getMechanismInfo(p11, slotID, ckm);
      } catch (ReflectiveOperationException ignored) {
        /* CK_MECHANISM_INFO opsiyonel, devam et */
      } catch (Throwable t) {
        // Sürücü stabilitesi belirsiz — defansif yut ama ilk seferde görünür kılmak için
        // warn seviyesinde logla. Sürücü crash'i tekrarlıyorsa logback config'i ile
        // bu sınıfın log seviyesi düşürülebilir.
        log.warn(
            "C_GetMechanismInfo beklenmedik throw (slot={}, ckm=0x{}): {}",
            slotID,
            Long.toHexString(ckm),
            t.toString());
      }
      if (info != null) {
        entries.add(
            new MechanismEntry(
                ckm,
                name,
                info.minKeySize,
                info.maxKeySize,
                info.flags,
                Pkcs11Mechanisms.decodeFlags(info.flags)));
      } else {
        entries.add(new MechanismEntry(ckm, name, -1L, -1L, 0L, Collections.<String>emptyList()));
      }
    }
    return new ProbeResult(libraryPath, slotID, tokenInfo, entries, true);
  }

  private static String rootMessage(Throwable t) {
    if (t == null) return "";
    Throwable cause = t.getCause() != null ? t.getCause() : t;
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  /* ------------------- result types ------------------- */

  /**
   * Bir slot'un mekanizma listesi + token meta'sı. JSON dostu (controller doğrudan döner). Tüm
   * alanlar opsiyonel — okunamayanlar null'dir.
   */
  public static final class ProbeResult {
    private final Path libraryPath;
    private final long slotID;
    private final Pkcs11Reflection.TokenInfo tokenInfo;
    private final List<MechanismEntry> mechanisms;
    private final boolean supported;

    ProbeResult(
        Path libraryPath,
        long slotID,
        Pkcs11Reflection.TokenInfo tokenInfo,
        List<MechanismEntry> mechanisms,
        boolean supported) {
      this.libraryPath = libraryPath;
      this.slotID = slotID;
      this.tokenInfo = tokenInfo;
      this.mechanisms = mechanisms == null ? Collections.<MechanismEntry>emptyList() : mechanisms;
      this.supported = supported;
    }

    /** "Token yok" / "okunamadı" sonucu. Test fixture'ı için de kullanılır. */
    public static ProbeResult empty(Path libraryPath) {
      return new ProbeResult(libraryPath, -1L, null, null, false);
    }

    /**
     * Test fixture factory: gerçek PKCS#11 lib olmadan {@link
     * io.mersel.dss.agent.api.services.signature.SignatureProfileResolver} ve {@link
     * io.mersel.dss.agent.api.services.signature.MechanismCapabilityService} davranışını test etmek
     * için kullanılır.
     */
    public static ProbeResult forTest(List<String> mechanismNames) {
      List<MechanismEntry> entries =
          new java.util.ArrayList<MechanismEntry>(
              mechanismNames == null ? 0 : mechanismNames.size());
      if (mechanismNames != null) {
        for (String n : mechanismNames) {
          long ckm = Pkcs11Mechanisms.valueOf(n);
          entries.add(
              new MechanismEntry(ckm, n, -1L, -1L, 0L, java.util.Collections.<String>emptyList()));
        }
      }
      return new ProbeResult(null, -1L, null, entries, true);
    }

    public String getLibraryPath() {
      return libraryPath != null ? libraryPath.toString() : null;
    }

    public long getSlotID() {
      return slotID;
    }

    public boolean isSupported() {
      return supported;
    }

    public List<MechanismEntry> getMechanisms() {
      return mechanisms;
    }

    public String getTokenLabel() {
      return tokenInfo != null ? tokenInfo.label : null;
    }

    public String getTokenManufacturerId() {
      return tokenInfo != null ? tokenInfo.manufacturerId : null;
    }

    public String getTokenModel() {
      return tokenInfo != null ? tokenInfo.model : null;
    }

    public String getTokenFirmwareVersion() {
      return tokenInfo != null ? tokenInfo.firmwareVersion : null;
    }

    public String getTokenHardwareVersion() {
      return tokenInfo != null ? tokenInfo.hardwareVersion : null;
    }

    /**
     * Kart serisi gizlilik için maskelenir (ilk 4 + son 2 karakter) — destek için yeterli, kart tam
     * serisi log'a / response'a düşmez.
     */
    public String getTokenSerialMasked() {
      String s = tokenInfo == null ? null : tokenInfo.serial;
      if (s == null || s.isEmpty()) return null;
      String trimmed = s.trim();
      if (trimmed.length() <= 6) {
        // Çok kısa — tamamı gözüksün de eşleştirme mümkün olsun.
        return trimmed;
      }
      return trimmed.substring(0, 4) + "***" + trimmed.substring(trimmed.length() - 2);
    }

    /** Mekanizma sembolik isimlerinin tek-satır seti (tanılama JSON'u için kısa form). */
    public List<String> mechanismNames() {
      Set<String> names = new LinkedHashSet<String>(mechanisms.size());
      for (MechanismEntry e : mechanisms) {
        names.add(e.getName());
      }
      return new ArrayList<String>(names);
    }

    /** Token'ın listede yer alan ilk RSA imzalama mekanizmasını döner (yoksa null). */
    public String firstAvailable(List<String> candidates) {
      if (candidates == null || candidates.isEmpty()) return null;
      Set<String> have = new java.util.HashSet<String>(mechanismNames());
      for (String c : candidates) {
        if (have.contains(c)) return c;
      }
      return null;
    }
  }

  /** Tek bir mekanizma satırı. JSON dostu. */
  public static final class MechanismEntry {
    private final long ckm;
    private final String name;
    private final long minKeySize;
    private final long maxKeySize;
    private final long rawFlags;
    private final List<String> flags;

    MechanismEntry(
        long ckm,
        String name,
        long minKeySize,
        long maxKeySize,
        long rawFlags,
        List<String> flags) {
      this.ckm = ckm;
      this.name = name;
      this.minKeySize = minKeySize;
      this.maxKeySize = maxKeySize;
      this.rawFlags = rawFlags;
      this.flags = flags == null ? Collections.<String>emptyList() : flags;
    }

    public long getCkm() {
      return ckm;
    }

    public String getName() {
      return name;
    }

    public long getMinKeySize() {
      return minKeySize;
    }

    public long getMaxKeySize() {
      return maxKeySize;
    }

    public long getRawFlags() {
      return rawFlags;
    }

    public List<String> getFlags() {
      return flags;
    }
  }
}
