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
package io.mersel.dss.agent.api.services.smartcard;

import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryNotFoundException;
import io.mersel.dss.agent.api.services.keystore.Pkcs11LibraryResolver;
import io.mersel.dss.agent.api.services.keystore.Pkcs11LibraryResolver.ResolutionResult;
import io.mersel.dss.agent.api.services.keystore.Pkcs11VendorHints;

/**
 * Yüksek seviyeli "kullanıcı bana terminal adı verdi, ben de imzalamak için gereken her şeyi (kart
 * tipi + PKCS#11 lib yolu) hazırla" arayüzü.
 *
 * <h2>4-katmanlı kart tanıma stratejisi</h2>
 *
 * <p>{@link #resolveLibrary(String, String, String)} aşağıdaki sırayı izler — her katman bir
 * öncekinin başarısızlığında devreye girer:
 *
 * <ol>
 *   <li><b>Override</b>: kullanıcı {@code cardType} (manual override) verdiyse, isimle çöz.
 *   <li><b>Override</b>: kullanıcı {@code pkcs11LibraryPath} verdiyse, lib adından kart tipini
 *       çıkar.
 *   <li><b>Layer 1 + Layer 2</b>: terminal'deki kartın ATR'inden tipi ara (exact match → regex).
 *   <li><b>Layer 3</b>: vendor lib probe — diskte olan PKCS#11 lib'leri sırayla aç ve token gören
 *       lib'i kart tipi olarak işaretle. Maliyetli, sadece L1/L2 boş çıkarsa devreye girer.
 *   <li><b>Layer 5</b>: hiçbir tanıma çalışmadıysa {@link Pkcs11LibraryNotFoundException} fırlatıp
 *       frontend'e {@code cardTypeCandidates} ile birlikte 503 döner.
 * </ol>
 */
@Component
public class SmartCardManager {

  private static final Logger log = LoggerFactory.getLogger(SmartCardManager.class);

  private final SmartCardReaderService readerService;
  private final CardTypeRegistry registry;
  private final Pkcs11LibraryResolver libraryResolver;
  private final Pkcs11ModuleProbe moduleProbe;

  public SmartCardManager(
      SmartCardReaderService readerService,
      CardTypeRegistry registry,
      Pkcs11LibraryResolver libraryResolver,
      Pkcs11ModuleProbe moduleProbe) {
    this.readerService = readerService;
    this.registry = registry;
    this.libraryResolver = libraryResolver;
    this.moduleProbe = moduleProbe;
  }

  /** İki-argümanlı kısayol; manual cardType override'ı verilmediği durumlar için. */
  public Path resolveLibrary(String terminalName, String pkcs11LibraryPath) {
    return resolveLibrary(terminalName, pkcs11LibraryPath, null);
  }

  /**
   * 4-katmanlı strateji ile imzaya hazır lib yolunu çözer.
   *
   * @param terminalName PCSC okuyucu adı
   * @param pkcs11LibraryPath kullanıcının manuel verdiği bare lib adı veya yol (override)
   * @param cardTypeOverride kullanıcının "kart tipini ben seçtim" modal'ından gelen ad (override)
   * @return diskte var olan absolute path
   * @throws Pkcs11LibraryNotFoundException kart algılansa bile sürücü diskte bulunamadığında
   *     <em>veya</em> hiçbir katman kart tipini tespit edemediğinde (Layer 5 fallback — yanıt
   *     {@code cardTypeCandidates} alanı ile zenginleştirilir)
   */
  public Path resolveLibrary(
      String terminalName, String pkcs11LibraryPath, String cardTypeOverride) {
    CardType resolvedType = null;
    String how = null;

    // Override 0) Kullanıcı kart tipini açıkça seçtiyse (Layer 5 fallback round-trip)
    if (!StringUtils.isBlank(cardTypeOverride)) {
      Optional<CardType> byOverride = registry.getByName(cardTypeOverride);
      if (byOverride.isPresent()) {
        resolvedType = byOverride.get();
        how = "override-cardType";
        log.debug("CardType manual override: {}", resolvedType.getName());
      } else {
        log.warn(
            "CardType override '{}' registry'de yok; ATR algılamaya geçiliyor.", cardTypeOverride);
      }
    }

    // Override 1) Verilen lib path'inden tip ipucu
    if (resolvedType == null) {
      Optional<CardType> byLib = registry.findByLibrary(pkcs11LibraryPath);
      if (byLib.isPresent()) {
        resolvedType = byLib.get();
        how = "pkcs11LibraryPath";
        log.debug("CardType lib path'inden çözüldü: {}", resolvedType.getName());
      }
    }

    // Layer 1 + Layer 2) Terminal'deki kartın ATR'inden tipi tespit etmeyi dene
    SmartCardInfo info = null;
    if (resolvedType == null && !StringUtils.isBlank(terminalName)) {
      info = readerService.findByTerminalName(terminalName);
      if (info != null && info.isRecognised()) {
        resolvedType = info.getCardType();
        how = "atr-l1-or-l2";
        log.debug(
            "CardType ATR'den çözüldü: {} (ATR={})", resolvedType.getName(), info.getAtrHex());
      }
    }

    // Layer 3) Module probe — diskteki vendor lib'lerini sırayla aç.
    if (resolvedType == null && info != null && info.getAtrHex() != null) {
      Optional<CardType> probed = moduleProbe.probeCardType(terminalName, info.getAtrHex());
      if (probed.isPresent()) {
        resolvedType = probed.get();
        how = "module-probe-l3";
        log.info(
            "CardType L3 probe ile çözüldü: {} (terminal={}, ATR={})",
            resolvedType.getName(),
            terminalName,
            info.getAtrHex());
      }
    }

    ResolutionResult result = libraryResolver.resolveDetailed(pkcs11LibraryPath, resolvedType);

    if (!result.isResolved()) {
      // Kart tipi tespit edilemedi VE pkcs11LibraryPath da verilmedi → Layer 5 fallback.
      throw libraryNotFound(resolvedType, terminalName, result, /*macStrict=*/ false);
    }

    // Bare-name fallback: dosya diskte bulunamadı, sadece bare ad döndü.
    // macOS ve Windows'ta bare ad ile yükleme genellikle BAŞARISIZ olur (dyld
    // / DLL search path varsayılan olarak işlemez); kullanıcıyı boşuna PKCS#11
    // init exception'ı görmek yerine zenginleştirilmiş hata ile bilgilendir.
    // Linux'ta ld.so.cache + LD_LIBRARY_PATH genelde işe yarar; orada loader'a
    // bırakırız.
    if (result.usedBareFallback()) {
      Pkcs11LibraryResolver.Os os = libraryResolver.getCurrentOs();
      if (os == Pkcs11LibraryResolver.Os.MACOS || os == Pkcs11LibraryResolver.Os.WINDOWS) {
        throw libraryNotFound(resolvedType, terminalName, result, /*macStrict=*/ true);
      }
      log.warn(
          "PKCS#11 lib disk üzerinde bulunamadı; OS loader'a bare ad ile teslim ediliyor: {}",
          result.getResolvedPath().get());
    }

    log.debug("Lib path çözümü: {} (via {})", result.getResolvedPath().get(), how);
    return result.getResolvedPath().get();
  }

  private Pkcs11LibraryNotFoundException libraryNotFound(
      CardType cardType, String terminalName, ResolutionResult result, boolean strict) {
    String cardTypeName = cardType != null ? cardType.getName() : null;
    String requiredLib = result.getBareNames().isEmpty() ? null : result.getBareNames().get(0);
    String downloadHint = Pkcs11VendorHints.downloadUrlFor(cardTypeName);

    StringBuilder msg = new StringBuilder();
    if (cardTypeName != null) {
      msg.append(cardTypeName).append(" kartı algılandı");
      if (requiredLib != null) {
        msg.append(" ama ").append(requiredLib).append(" PKCS#11 sürücüsü bulunamadı");
      } else {
        msg.append(" ama uygun PKCS#11 sürücüsü bulunamadı");
      }
      msg.append(". ");
    } else {
      msg.append("Kart otomatik tanınamadı. ");
      if (!StringUtils.isBlank(terminalName)) {
        msg.append("Terminal: ").append(terminalName).append(". ");
      }
      msg.append(
          "Lütfen kart tipini seçin ya da 'pkcs11LibraryPath' parametresi ile bilinen bir PKCS#11"
              + " lib adı (örn. 'akisp11') verin. ");
    }

    if (strict) {
      msg.append("Sürücüyü kurduktan sonra uygulamayı yeniden başlatın. ");
    }

    String hint = Pkcs11VendorHints.hintFor(cardTypeName);
    if (hint != null) {
      msg.append(hint);
    } else if (cardTypeName != null) {
      msg.append("Kart üreticisinin resmi sitesinden PKCS#11 sürücüsünü yükleyin.");
    }

    return new Pkcs11LibraryNotFoundException(
        cardTypeName,
        requiredLib,
        result.describeSearchedPaths(),
        downloadHint,
        msg.toString(),
        // Layer 5: kart tipi null ise frontend'in modal açabilmesi için aday liste taşı.
        cardTypeName == null ? registry.candidateNames() : null);
  }
}
