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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.services.smartcard.CardType;
import io.mersel.dss.agent.api.services.smartcard.CardTypeRegistry;

/**
 * Kullanıcının verdiği {@code pkcs11LibraryPath} (bare lib adı veya tam yol), algılanan kart tipi
 * ve OS bilgisinden hareketle PKCS#11 paylaşımlı kütüphanesinin diskteki yolunu çözer.
 *
 * <p>Algoritma:
 *
 * <ol>
 *   <li>pkcs11LibraryPath mutlak/var olan bir yol ise direkt kullan.
 *   <li>pkcs11LibraryPath + ilgili kart tipinin libs + hints listesinden aday dosya adları üret
 *       (OS'a göre uzantı/önek ekle).
 *   <li>{@code mersel.signer.extra-lib-search-paths} + per-OS path listesi + kart tipinin OS
 *       hint'leri + sistem default'ları aranır.
 *   <li>İlk var olan dosyanın absolute path'i döner; bulunamazsa son şans olarak bare adı OS
 *       loader'a teslim eder (özellikle Linux ld.so.cache için).
 * </ol>
 */
@Component
public class Pkcs11LibraryResolver {

  /**
   * Lib çözümleme süreci tanılayıcısı: çözülen yol, denenen dosya adları ve dizinler. {@link
   * io.mersel.dss.agent.api.services.smartcard.SmartCardManager} kullanıcı dostu hata mesajı
   * üretmek için kullanır.
   */
  public static final class ResolutionResult {
    private final Path resolvedPath;
    private final List<String> bareNames;
    private final List<String> candidateFileNames;
    private final List<Path> searchedDirectories;
    private final boolean usedBareFallback;

    private ResolutionResult(
        Path resolvedPath,
        List<String> bareNames,
        List<String> candidateFileNames,
        List<Path> searchedDirectories,
        boolean usedBareFallback) {
      this.resolvedPath = resolvedPath;
      this.bareNames = unmodifiableCopy(bareNames);
      this.candidateFileNames = unmodifiableCopy(candidateFileNames);
      this.searchedDirectories = unmodifiablePathCopy(searchedDirectories);
      this.usedBareFallback = usedBareFallback;
    }

    static ResolutionResult found(
        Path path,
        List<String> bare,
        List<String> candidates,
        List<Path> dirs,
        boolean bareFallback) {
      return new ResolutionResult(path, bare, candidates, dirs, bareFallback);
    }

    static ResolutionResult notFound(List<String> bare, List<String> candidates, List<Path> dirs) {
      return new ResolutionResult(null, bare, candidates, dirs, false);
    }

    public Optional<Path> getResolvedPath() {
      return Optional.<Path>ofNullable(resolvedPath);
    }

    public boolean isResolved() {
      return resolvedPath != null;
    }

    /** {@code true} ise: dosya diskte bulunamadı, {@link #getResolvedPath()} yalnızca bare ad. */
    public boolean usedBareFallback() {
      return usedBareFallback;
    }

    public List<String> getBareNames() {
      return bareNames;
    }

    public List<String> getCandidateFileNames() {
      return candidateFileNames;
    }

    public List<Path> getSearchedDirectories() {
      return searchedDirectories;
    }

    /** Kullanıcı dostu hata mesajı için aday dosya × dizin kombinasyonlarının düz listesi. */
    public List<String> describeSearchedPaths() {
      List<String> out = new ArrayList<String>();
      for (Path d : searchedDirectories) {
        for (String n : candidateFileNames) {
          out.add(d.resolve(n).toString());
        }
      }
      return out;
    }

    private static List<String> unmodifiableCopy(List<String> src) {
      if (src == null) return java.util.Collections.<String>emptyList();
      return java.util.Collections.unmodifiableList(new ArrayList<String>(src));
    }

    private static List<Path> unmodifiablePathCopy(List<Path> src) {
      if (src == null) return java.util.Collections.<Path>emptyList();
      return java.util.Collections.unmodifiableList(new ArrayList<Path>(src));
    }
  }

  private static final Logger log = LoggerFactory.getLogger(Pkcs11LibraryResolver.class);

  public enum Os {
    WINDOWS,
    MACOS,
    LINUX,
    OTHER
  }

  private final SignerProperties properties;
  private final CardTypeRegistry registry;
  private final Os currentOs;

  public Pkcs11LibraryResolver(SignerProperties properties, CardTypeRegistry registry) {
    this.properties = properties;
    this.registry = registry;
    this.currentOs = detectOs();
  }

  public Os getCurrentOs() {
    return currentOs;
  }

  public Optional<Path> resolve(String pkcs11LibraryPath, CardType cardType) {
    return resolveDetailed(pkcs11LibraryPath, cardType).getResolvedPath();
  }

  /**
   * Lib path çözümleme süreci hakkında tam tanılayıcı bilgi döndürür: çözülen yol (varsa), aranan
   * dosya adları ve aranan dizinler.
   *
   * <p>Bu method <em>asla istisna fırlatmaz</em>; sonucu {@code !result.isResolved()} ile
   * sorgulayan çağıran taraf, kullanıcıya zenginleştirilmiş hata mesajı (örn. {@link
   * io.mersel.dss.agent.api.exceptions.Pkcs11LibraryNotFoundException}) yapısı için bilgileri
   * kullanabilir.
   */
  public ResolutionResult resolveDetailed(String pkcs11LibraryPath, CardType cardType) {
    List<String> bareNames = buildLibBareNames(pkcs11LibraryPath, cardType);
    List<String> candidates = candidateFileNames(bareNames);
    List<Path> searchPaths = buildSearchPaths(cardType);

    if (!StringUtils.isBlank(pkcs11LibraryPath)) {
      Path direct = Paths.get(pkcs11LibraryPath);
      if (direct.isAbsolute() && Files.isRegularFile(direct)) {
        return ResolutionResult.found(
            direct.toAbsolutePath(), bareNames, candidates, searchPaths, false);
      }
    }

    log.debug(
        "PKCS#11 resolve: os={}, candidates={}, paths={}", currentOs, candidates, searchPaths);

    for (Path dir : searchPaths) {
      for (String name : candidates) {
        Path candidate = dir.resolve(name);
        if (Files.isRegularFile(candidate)) {
          return ResolutionResult.found(
              candidate.toAbsolutePath(), bareNames, candidates, searchPaths, false);
        }
      }
    }

    // Son şans: OS loader'a bare adıyla teslim et (Linux'ta yaygın).
    // Bu yola "fallback" diyoruz çünkü dosyayı diskte bulduğumuzu garanti
    // etmiyoruz; SunPKCS11 init aşamasında lib gerçekten yüklenmezse
    // Pkcs11Session.open() yine hata fırlatır, ama o noktada vendor hint
    // ile zenginleştirebilmek için bu sonucu yakalamak gerek.
    if (!candidates.isEmpty()) {
      String first = candidates.get(0);
      log.debug(
          "PKCS#11 lib disk üzerinde bulunamadı; bare ad ile loader'a teslim ediliyor: {}", first);
      return ResolutionResult.found(Paths.get(first), bareNames, candidates, searchPaths, true);
    }
    return ResolutionResult.notFound(bareNames, candidates, searchPaths);
  }

  private List<String> buildLibBareNames(String pkcs11LibraryPath, CardType cardType) {
    Set<String> bare = new LinkedHashSet<String>();
    if (!StringUtils.isBlank(pkcs11LibraryPath)) {
      String name = new File(pkcs11LibraryPath).getName();
      bare.add(stripExtension(stripPrefix(name)));
    }
    if (cardType != null) {
      for (String l : cardType.getLibraries()) {
        bare.add(stripExtension(stripPrefix(l)));
      }
    }
    return new ArrayList<String>(bare);
  }

  private List<String> candidateFileNames(List<String> bareNames) {
    List<String> out = new ArrayList<String>();
    for (String name : bareNames) {
      if (StringUtils.isBlank(name)) continue;
      switch (currentOs) {
        case WINDOWS:
          out.add(name + ".dll");
          out.add(name + ".DLL");
          break;
        case MACOS:
          out.add("lib" + name + ".dylib");
          out.add(name + ".dylib");
          out.add("lib" + name + ".so");
          out.add(name + ".so");
          break;
        case LINUX:
          out.add("lib" + name + ".so");
          out.add(name + ".so");
          out.add("lib" + name + ".so.1");
          out.add("lib" + name + ".so.2");
          out.add("lib" + name + ".so.3");
          break;
        default:
          out.add(name);
          break;
      }
    }
    return out;
  }

  private List<Path> buildSearchPaths(CardType cardType) {
    Set<Path> seen = new LinkedHashSet<Path>();

    // 1) Kullanıcının eklediği extra yollar (en yüksek öncelik)
    addAll(seen, properties.getExtraLibSearchPaths());

    // 2) application.yml per-OS path listesi
    switch (currentOs) {
      case WINDOWS:
        addAll(seen, properties.getPkcs11SearchPathsWindows());
        break;
      case MACOS:
        addAll(seen, properties.getPkcs11SearchPathsMacos());
        break;
      case LINUX:
        addAll(seen, properties.getPkcs11SearchPathsLinux());
        break;
      default:
        break;
    }

    // 3) CardType'ın smartcard-config.xml'deki hint'leri
    if (cardType != null) {
      addAll(seen, cardType.getHints(osTag()));
    }

    // 4) Sistem default'ları (her ihtimale karşı)
    switch (currentOs) {
      case WINDOWS:
        seen.add(Paths.get("C:\\Windows\\System32"));
        seen.add(Paths.get("C:\\Windows\\SysWOW64"));
        break;
      case MACOS:
        seen.add(Paths.get("/usr/local/lib"));
        seen.add(Paths.get("/opt/homebrew/lib"));
        break;
      case LINUX:
        seen.add(Paths.get("/usr/lib"));
        seen.add(Paths.get("/usr/lib64"));
        seen.add(Paths.get("/usr/lib/x86_64-linux-gnu"));
        seen.add(Paths.get("/usr/local/lib"));
        break;
      default:
        break;
    }
    return new ArrayList<Path>(seen);
  }

  private static void addAll(Set<Path> seen, List<String> paths) {
    if (paths == null) return;
    for (String s : paths) {
      if (StringUtils.isBlank(s)) continue;
      seen.add(Paths.get(s));
    }
  }

  private String osTag() {
    switch (currentOs) {
      case WINDOWS:
        return "windows";
      case MACOS:
        return "macos";
      case LINUX:
        return "linux";
      default:
        return "other";
    }
  }

  private static String stripPrefix(String name) {
    if (name == null) return "";
    if (name.startsWith("lib")) return name.substring(3);
    return name;
  }

  private static String stripExtension(String name) {
    if (name == null) return "";
    int idx = name.lastIndexOf('.');
    if (idx <= 0) return name;
    String ext = name.substring(idx + 1).toLowerCase(Locale.ROOT);
    if ("dll".equals(ext) || "so".equals(ext) || "dylib".equals(ext)) {
      return name.substring(0, idx);
    }
    // libfoo.so.1 gibi sürümlü adlar
    if (ext.matches("\\d+")) {
      return stripExtension(name.substring(0, idx));
    }
    return name;
  }

  private static Os detectOs() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) return Os.WINDOWS;
    if (os.contains("mac") || os.contains("darwin")) return Os.MACOS;
    if (os.contains("nix") || os.contains("nux") || os.contains("aix")) return Os.LINUX;
    return Os.OTHER;
  }
}
