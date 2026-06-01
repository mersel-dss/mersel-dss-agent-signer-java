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
package io.mersel.dss.agent.api.exceptions;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Bir {@link Throwable} cause zincirini düz, JSON dostu bir liste hâline getirir.
 *
 * <p>{@code SIGNATURE_FAILED} gibi opak hataların asıl kökenini ({@code
 * InvalidAlgorithmParameterException → Mechanism not supported → CKR_MECHANISM_INVALID}) frontend'e
 * ve destek operatörüne tek atışta görünür kılmak için kullanılır.
 *
 * <p>{@link io.mersel.dss.agent.api.services.keystore.Pkcs11Errors#extractCkrCode} ile aynı
 * cycle-koruma desenini ({@link IdentityHashMap}) kullanır; karşılıklı sebep zinciri olan
 * exception'larda sonsuz döngüye düşmez.
 */
public final class CauseChainExtractor {

  /** Genel yanıtlar için pratik üst sınır. PKCS#11 → JSR-105 → xades4j zinciri tipik 4-6. */
  public static final int DEFAULT_MAX_DEPTH = 8;

  private CauseChainExtractor() {
    /* utility */
  }

  /**
   * {@code throwable}'ın kendisi dahil cause zincirini sıralı listeye serer. {@code null} input boş
   * liste döner; mesaj null ise {@code Frame.message} de null bırakılır (JSON'da {@code NON_NULL}
   * ile düşer).
   */
  public static List<Frame> flatten(Throwable throwable) {
    return flatten(throwable, DEFAULT_MAX_DEPTH);
  }

  public static List<Frame> flatten(Throwable throwable, int maxDepth) {
    List<Frame> frames = new ArrayList<Frame>();
    if (throwable == null) {
      return frames;
    }
    Map<Throwable, Boolean> seen = new IdentityHashMap<Throwable, Boolean>();
    Throwable cur = throwable;
    int depth = 0;
    while (cur != null && depth < maxDepth) {
      if (seen.containsKey(cur)) {
        return frames;
      }
      seen.put(cur, Boolean.TRUE);
      frames.add(new Frame(cur.getClass().getName(), cur.getMessage()));
      cur = cur.getCause();
      depth++;
    }
    return frames;
  }

  /**
   * Cause zincirini cycle korumalı şekilde gezer ve her {@link Throwable} için {@code visitor}'ı
   * çağırır. Durdurma koşulu: visitor {@code false} dönerse iterasyon sonlanır.
   *
   * <p>Bu metot, codebase boyunca duplike edilen {@code while (cur != null) { ... cur =
   * cur.getCause(); }} cycle-walker template'inin tek kaynak noktasıdır:
   *
   * <ul>
   *   <li>{@code IaikPkcs11Signer.requiresIaikFallback}
   *   <li>{@code XadesService.describePathology}
   *   <li>{@code Pkcs11Errors.extractCkrCode}
   * </ul>
   *
   * @param visitor her throwable için çağrılır; {@code false} dönerse yürüyüş durur
   * @return visitor {@code true} döndüğü ilk Throwable (eşleşme yoksa {@code null})
   */
  public static Throwable walk(Throwable throwable, Predicate<Throwable> visitor) {
    return walk(throwable, visitor, DEFAULT_MAX_DEPTH);
  }

  /** {@link #walk(Throwable, Predicate)} ile aynı, ek olarak maksimum derinlik parametresi alır. */
  public static Throwable walk(Throwable throwable, Predicate<Throwable> visitor, int maxDepth) {
    if (throwable == null || visitor == null) {
      return null;
    }
    Map<Throwable, Boolean> seen = new IdentityHashMap<Throwable, Boolean>();
    Throwable cur = throwable;
    int depth = 0;
    while (cur != null && depth < maxDepth) {
      if (seen.containsKey(cur)) {
        return null;
      }
      seen.put(cur, Boolean.TRUE);
      if (visitor.test(cur)) {
        return cur;
      }
      cur = cur.getCause();
      depth++;
    }
    return null;
  }

  /**
   * Cause zincirinde {@code visitor.test(cur) == true} döndüğü ilk frame'i arar; varsa {@code true}
   * döner. {@link #walk} overloading'lerin boolean adapter'i.
   */
  public static boolean anyMatch(Throwable throwable, Predicate<Throwable> visitor) {
    return walk(throwable, visitor) != null;
  }

  /**
   * Cause zincirinin en alttaki (root) frame'inin {@code message}'i. Tipik olarak gerçek hatayı
   * söyleyen string'tir ({@code CKR_MECHANISM_INVALID}, {@code Unsupported parameters} gibi). Mesaj
   * null ise sınıfın simple adı döner.
   */
  public static String rootMessage(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    List<Frame> frames = flatten(throwable);
    if (frames.isEmpty()) {
      return null;
    }
    Frame root = frames.get(frames.size() - 1);
    if (root.getMessage() != null && !root.getMessage().isEmpty()) {
      return root.getMessage();
    }
    int lastDot = root.getType().lastIndexOf('.');
    return lastDot >= 0 ? root.getType().substring(lastDot + 1) : root.getType();
  }

  /**
   * Cause zincirinde {@code substring} (case-insensitive) içeren ilk frame'i döner. Bilinen hata
   * paternlerini ({@code Unsupported parameters}, {@code CKR_MECHANISM_INVALID}, {@code Mechanism
   * not supported}) tespit etmek için kullanılır.
   */
  public static Frame findContaining(Throwable throwable, String substring) {
    if (throwable == null || substring == null || substring.isEmpty()) {
      return null;
    }
    String needle = substring.toLowerCase(java.util.Locale.ROOT);
    for (Frame f : flatten(throwable)) {
      String msg = f.getMessage();
      if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
        return f;
      }
      String type = f.getType();
      if (type != null && type.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
        return f;
      }
    }
    return null;
  }

  /**
   * Tek bir cause zincir adımı. {@code @JsonInclude.NON_NULL} {@code ErrorModel} ile birlikte
   * çalışır; {@code message} null ise serialize'da görünmez.
   */
  public static final class Frame {
    private final String type;
    private final String message;

    public Frame(String type, String message) {
      this.type = type;
      this.message = message;
    }

    public String getType() {
      return type;
    }

    public String getMessage() {
      return message;
    }
  }
}
