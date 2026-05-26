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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code smartcard-config.xml}'den okunan tek bir kart tanımı.
 *
 * <p>Eski projedeki TÜBİTAK ma3api {@code CardType} obfuscated enum'unun XML tabanlı,
 * vendor-agnostic yeniden ifadesi. Bir CardType:
 *
 * <ul>
 *   <li>İnsan-okunur ada sahiptir (örn. "AKIS", "e-İmzaTR")
 *   <li>1..N adet PKCS#11 kütüphane bare adına sahiptir (lib, lib32, lib64, lib-alt — sıralı arama
 *       listesi)
 *   <li>OS başına 0..N adet hint dizinine sahiptir
 *   <li>0..N adet ATR ile eşleşir (Layer 1 — exact match)
 *   <li>0..N adet historical-bytes regex'ine sahiptir (Layer 2 — pattern match)
 * </ul>
 */
public class CardType {

  private final String name;
  private final List<String> libraries;
  private final Map<String, List<String>> hintsByOs;
  private final List<String> atrs;
  private final List<Pattern> historicalBytePatterns;

  public CardType(
      String name, List<String> libraries, Map<String, List<String>> hintsByOs, List<String> atrs) {
    this(name, libraries, hintsByOs, atrs, Collections.<Pattern>emptyList());
  }

  public CardType(
      String name,
      List<String> libraries,
      Map<String, List<String>> hintsByOs,
      List<String> atrs,
      List<Pattern> historicalBytePatterns) {
    this.name = name;
    this.libraries =
        libraries == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(libraries));
    this.hintsByOs =
        hintsByOs == null
            ? Collections.<String, List<String>>emptyMap()
            : Collections.unmodifiableMap(deepCopy(hintsByOs));
    this.atrs =
        atrs == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(atrs));
    this.historicalBytePatterns =
        historicalBytePatterns == null
            ? Collections.<Pattern>emptyList()
            : Collections.unmodifiableList(new ArrayList<Pattern>(historicalBytePatterns));
  }

  public String getName() {
    return name;
  }

  /** İlk seçenek = en yaygın ad. Diğerleri fallback. */
  public List<String> getLibraries() {
    return libraries;
  }

  /** OS = "windows" | "macos" | "linux". */
  public List<String> getHints(String os) {
    if (os == null) return Collections.emptyList();
    List<String> list = hintsByOs.get(os.toLowerCase());
    return list == null ? Collections.<String>emptyList() : list;
  }

  public List<String> getAtrs() {
    return atrs;
  }

  /**
   * Layer 2 historical-bytes regex pattern'leri. ATR historical-bytes hex'ine {@code
   * matcher().matches()} uygulanır (TÜBİTAK ma3api formatı: tam-string match).
   *
   * <p>Boş liste = bu kart tipi sadece exact ATR ile tanınabilir.
   */
  public List<Pattern> getHistoricalBytePatterns() {
    return historicalBytePatterns;
  }

  private static Map<String, List<String>> deepCopy(Map<String, List<String>> src) {
    Map<String, List<String>> copy = new HashMap<String, List<String>>();
    for (Map.Entry<String, List<String>> e : src.entrySet()) {
      copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<String>(e.getValue())));
    }
    return copy;
  }
}
