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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.mersel.dss.agent.api.config.SignerProperties;

/**
 * {@code smartcard-config.xml}'i belleğe yükler ve sorgu API'si sunar.
 *
 * <p>Eski projedeki obfuscated TÜBİTAK ma3api CardType enum'unun vendor-agnostic XML tabanlı
 * yeniden ifadesi. Lookup yolları:
 *
 * <ul>
 *   <li>{@link #findByAtr(String)} — Layer 1 (exact ATR) → Layer 2 (historical-bytes regex)
 *   <li>{@link #findByLibrary(String)} — kullanıcı lib adı girdiğinde tipi bulur
 *   <li>{@link #getByName(String)} — ad ile arama
 *   <li>{@link #candidateNames()} — Layer 5 fallback için sıralı kart tipi listesi
 * </ul>
 *
 * <h2>Layer 2 cache</h2>
 *
 * Historical-bytes regex değerlendirmesi {@code O(card_types × patterns)} maliyetlidir;
 * kullanıcının kartı sabit kaldığı sürece sonucu cache'lemek için kısa-TTL (5sn) bir bellek-içi
 * cache kullanılır. {@code SmartCardReaderService.invalidateAtrCache()} bu cache'i de temizler.
 */
@Component
public class CardTypeRegistry {

  private static final Logger log = LoggerFactory.getLogger(CardTypeRegistry.class);

  /** Layer 2 cache TTL — ATR cache ile aynı 5sn hizalı tutuluyor. */
  private static final long L2_CACHE_TTL_MS = 5_000L;

  private final SignerProperties properties;
  private final ResourceLoader resourceLoader;

  /** atr (HEX, upper, no spaces) → CardType */
  private final Map<String, CardType> byAtr = new HashMap<String, CardType>();
  /** lib bare name (lowercase) → CardType */
  private final Map<String, CardType> byLibrary = new HashMap<String, CardType>();
  /** card-type ad → CardType (insertion order korunur) */
  private final Map<String, CardType> byName = new LinkedHashMap<String, CardType>();

  /** L2 cache: normalised ATR → CardType-name (veya boş "" / sentinel "_NULL_") */
  private final ConcurrentMap<String, CachedHit> l2Cache =
      new ConcurrentHashMap<String, CachedHit>();

  private static final class CachedHit {
    final String cardTypeName; // null = miss
    final long expiresAt;

    CachedHit(String cardTypeName, long expiresAt) {
      this.cardTypeName = cardTypeName;
      this.expiresAt = expiresAt;
    }
  }

  @Autowired
  public CardTypeRegistry(SignerProperties properties, ResourceLoader resourceLoader) {
    this.properties = properties;
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  public void load() {
    String location = properties.getSmartcardConfig();
    if (StringUtils.isBlank(location)) {
      log.warn("mersel.signer.smartcard-config tanımsız; kart tanıma devre dışı.");
      return;
    }

    Resource resource = resourceLoader.getResource(location);
    if (!resource.exists()) {
      log.warn("smartcard-config bulunamadı: {}", location);
      return;
    }

    try (InputStream in = resource.getInputStream()) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(in);

      NodeList cardNodes = doc.getElementsByTagName("card-type");
      int patternCount = 0;
      for (int i = 0; i < cardNodes.getLength(); i++) {
        Element el = (Element) cardNodes.item(i);
        CardType ct = parseCardType(el);
        register(ct);
        patternCount += ct.getHistoricalBytePatterns().size();
      }
      log.info(
          "smartcard-config yüklendi: {} kart tipi, {} ATR (L1), {} regex (L2), {} lib eşleştirmesi.",
          byName.size(),
          byAtr.size(),
          patternCount,
          byLibrary.size());
    } catch (Exception e) {
      log.error("smartcard-config yüklenemedi: " + location, e);
    }
  }

  private void register(CardType ct) {
    byName.put(ct.getName().toLowerCase(Locale.ROOT), ct);
    for (String atr : ct.getAtrs()) {
      byAtr.put(normaliseAtr(atr), ct);
    }
    for (String lib : ct.getLibraries()) {
      byLibrary.put(lib.toLowerCase(Locale.ROOT), ct);
    }
  }

  private CardType parseCardType(Element el) {
    String name = el.getAttribute("name");

    List<String> libs = new ArrayList<String>();
    Map<String, List<String>> hints = new LinkedHashMap<String, List<String>>();
    List<String> atrs = new ArrayList<String>();
    List<Pattern> patterns = new ArrayList<Pattern>();

    NodeList children = el.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n.getNodeType() != Node.ELEMENT_NODE) continue;
      Element child = (Element) n;
      String tag = child.getTagName().toLowerCase(Locale.ROOT);

      if ("lib".equals(tag)
          || "lib32".equals(tag)
          || "lib64".equals(tag)
          || "lib-alt".equals(tag)) {
        String val = textOrAttr(child);
        if (!StringUtils.isBlank(val)) libs.add(val.trim());
      } else if ("hint".equals(tag)) {
        String os = child.getAttribute("os");
        if (StringUtils.isBlank(os)) continue;
        String key = os.toLowerCase(Locale.ROOT);
        List<String> bucket = hints.get(key);
        if (bucket == null) {
          bucket = new ArrayList<String>();
          hints.put(key, bucket);
        }
        String val = textOrAttr(child);
        if (!StringUtils.isBlank(val)) bucket.add(val.trim());
      } else if ("atr".equals(tag)) {
        String val = textOrAttr(child);
        if (!StringUtils.isBlank(val)) atrs.add(normaliseAtr(val));
      } else if ("atr-pattern".equals(tag)) {
        String regex = child.getAttribute("regex");
        if (StringUtils.isBlank(regex)) regex = textOrAttr(child);
        if (StringUtils.isBlank(regex)) continue;
        try {
          // Historical-bytes hex'i UPPER normalize ediliyor → case-insensitive flag güvenli.
          patterns.add(Pattern.compile(regex.trim(), Pattern.CASE_INSENSITIVE));
        } catch (PatternSyntaxException pse) {
          log.warn(
              "smartcard-config '{}' atr-pattern derlenemedi (regex=`{}`): {}",
              name,
              regex,
              pse.getMessage());
        }
      }
    }
    return new CardType(name, libs, hints, atrs, patterns);
  }

  private String textOrAttr(Element el) {
    String text = el.getTextContent();
    if (text != null && !text.trim().isEmpty()) return text.trim();
    String val = el.getAttribute("value");
    return val == null ? "" : val.trim();
  }

  private static String normaliseAtr(String atr) {
    if (atr == null) return "";
    return atr.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
  }

  /* ----------------- Public lookups ----------------- */

  /**
   * Layer 1 (exact ATR) → Layer 2 (historical-bytes regex) sıralı arama.
   *
   * <p>L1 cache zaten {@code Map}; L2 sonucu kısa-TTL (5sn) cache'lenir.
   *
   * @return ilk eşleşen CardType, hiç eşleşmediyse boş Optional
   */
  public Optional<CardType> findByAtr(String atrHex) {
    if (StringUtils.isBlank(atrHex)) return Optional.empty();
    String key = normaliseAtr(atrHex);
    CardType hit = byAtr.get(key);
    if (hit != null) return Optional.of(hit);

    // L2 path: cache kontrol → pattern match → cache yaz.
    long now = System.currentTimeMillis();
    CachedHit cached = l2Cache.get(key);
    if (cached != null && cached.expiresAt > now) {
      if (cached.cardTypeName == null) return Optional.empty();
      return Optional.ofNullable(byName.get(cached.cardTypeName));
    }

    Optional<CardType> match = AtrPatternMatcher.matchByHistoricalBytes(key, byName.values());
    String matchedName = match.isPresent() ? match.get().getName().toLowerCase(Locale.ROOT) : null;
    l2Cache.put(key, new CachedHit(matchedName, now + L2_CACHE_TTL_MS));
    return match;
  }

  /** Layer 1 yalnız — exact ATR match, regex denenmez. Diagnostics ve testler için. */
  public Optional<CardType> findByAtrExact(String atrHex) {
    if (StringUtils.isBlank(atrHex)) return Optional.empty();
    return Optional.ofNullable(byAtr.get(normaliseAtr(atrHex)));
  }

  /** Layer 2 yalnız — historical-bytes regex match; L1 atlanır. Tanı ve testler için. */
  public Optional<CardType> findByAtrPattern(String atrHex) {
    if (StringUtils.isBlank(atrHex)) return Optional.empty();
    return AtrPatternMatcher.matchByHistoricalBytes(normaliseAtr(atrHex), byName.values());
  }

  public Optional<CardType> findByLibrary(String libNameOrPath) {
    if (StringUtils.isBlank(libNameOrPath)) return Optional.empty();
    String key = libNameOrPath.toLowerCase(Locale.ROOT);
    for (Map.Entry<String, CardType> e : byLibrary.entrySet()) {
      if (key.contains(e.getKey())) return Optional.of(e.getValue());
    }
    return Optional.empty();
  }

  public Optional<CardType> getByName(String name) {
    if (StringUtils.isBlank(name)) return Optional.empty();
    return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
  }

  public List<CardType> all() {
    return Collections.unmodifiableList(new ArrayList<CardType>(byName.values()));
  }

  /**
   * Layer 5 fallback için: sistemde tanımlı tüm kart tipi adlarının alfabetik sıralı listesi.
   * Frontend "kart tipini seçin" modal'ında kullanılır.
   */
  public List<String> candidateNames() {
    TreeSet<String> sorted = new TreeSet<String>();
    for (CardType ct : byName.values()) {
      sorted.add(ct.getName());
    }
    return Collections.unmodifiableList(new ArrayList<String>(sorted));
  }

  /** Layer 2 cache'ini temizler. {@code SmartCardReaderService.invalidateAtrCache()} ile zincir. */
  public void invalidatePatternCache() {
    l2Cache.clear();
  }
}
