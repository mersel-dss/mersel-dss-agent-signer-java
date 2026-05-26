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
package io.mersel.dss.agent.api.services.update;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sade semantic versioning karşılaştırması: {@code MAJOR.MINOR.PATCH[-PRERELEASE]}.
 *
 * <p>GitHub tag'lerindeki baştaki {@code "v"} kabuğu (örn. {@code v2.1.0}) {@link #parse(String)}
 * tarafından düşürülür. Sayı segmentleri eksik olursa ({@code "2.0"}) yerine 0 yazılır.
 * Karşılaştırma lexicographic değildir: her segment integer olarak kıyaslanır; segmentin biri sayı
 * değilse o konum {@link Integer#MAX_VALUE} sayılır ki "geçersiz" hâli bir sonraki segmente
 * düşmesin (test edilebilir, hata yutan davranış).
 *
 * <p>Prerelease soneki (örn. {@code "2.0.0-rc1"}) yalnız iki sürüm baz ({@code MAJOR.MINOR.PATCH})
 * eşit olduğunda kıyaslanır: prerelease, prerelease-olmayan'dan KÜÇÜK kabul edilir (semver).
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

  private final List<Integer> segments;
  private final String prerelease; // null = stable
  private final String raw;

  private SemanticVersion(List<Integer> segments, String prerelease, String raw) {
    this.segments = Collections.unmodifiableList(new ArrayList<Integer>(segments));
    this.prerelease = prerelease;
    this.raw = raw;
  }

  /** Boş/null veya parse edilemeyen değerler için {@link Optional#empty()} döner. */
  public static Optional<SemanticVersion> parse(String input) {
    if (input == null) {
      return Optional.empty();
    }
    String trimmed = input.trim();
    if (trimmed.isEmpty()) {
      return Optional.empty();
    }
    String working = trimmed;
    if (working.charAt(0) == 'v' || working.charAt(0) == 'V') {
      working = working.substring(1);
    }
    if (working.isEmpty()) {
      return Optional.empty();
    }

    String prerelease = null;
    int dashIdx = working.indexOf('-');
    if (dashIdx >= 0) {
      prerelease = working.substring(dashIdx + 1);
      working = working.substring(0, dashIdx);
    }
    int plusIdx = working.indexOf('+');
    if (plusIdx >= 0) {
      working = working.substring(0, plusIdx);
    }

    String[] parts = working.split("\\.");
    if (parts.length == 0) {
      return Optional.empty();
    }
    List<Integer> segs = new ArrayList<Integer>(parts.length);
    boolean atLeastOneNumeric = false;
    for (String p : parts) {
      if (p == null || p.isEmpty()) {
        segs.add(0);
        continue;
      }
      try {
        segs.add(Integer.parseInt(p));
        atLeastOneNumeric = true;
      } catch (NumberFormatException nfe) {
        return Optional.empty();
      }
    }
    if (!atLeastOneNumeric) {
      return Optional.empty();
    }
    return Optional.of(new SemanticVersion(segs, prerelease, trimmed));
  }

  /** {@code this > other} olup olmadığını döner. */
  public boolean isNewerThan(SemanticVersion other) {
    return other != null && this.compareTo(other) > 0;
  }

  @Override
  public int compareTo(SemanticVersion o) {
    Objects.requireNonNull(o, "other");
    int len = Math.max(segments.size(), o.segments.size());
    for (int i = 0; i < len; i++) {
      int a = i < segments.size() ? segments.get(i) : 0;
      int b = i < o.segments.size() ? o.segments.get(i) : 0;
      if (a != b) {
        return Integer.compare(a, b);
      }
    }
    // Base versions equal — prerelease comparison (stable > prerelease).
    if (Objects.equals(this.prerelease, o.prerelease)) {
      return 0;
    }
    if (this.prerelease == null) {
      return 1;
    }
    if (o.prerelease == null) {
      return -1;
    }
    return this.prerelease.compareTo(o.prerelease);
  }

  public List<Integer> segments() {
    return segments;
  }

  public String prerelease() {
    return prerelease;
  }

  /** Test/debug için orijinal input (trim'lenmiş, "v" prefix dahil). */
  public String raw() {
    return raw;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SemanticVersion)) return false;
    SemanticVersion that = (SemanticVersion) o;
    return this.compareTo(that) == 0;
  }

  @Override
  public int hashCode() {
    // Equal sürümler aynı hash'e düşmeli — base segment'leri trailing-zero trimmed.
    List<Integer> normalized = new ArrayList<Integer>(segments);
    while (normalized.size() > 1 && normalized.get(normalized.size() - 1) == 0) {
      normalized.remove(normalized.size() - 1);
    }
    return Objects.hash(normalized, prerelease);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < segments.size(); i++) {
      if (i > 0) sb.append('.');
      sb.append(segments.get(i));
    }
    if (prerelease != null) {
      sb.append('-').append(prerelease);
    }
    return sb.toString();
  }

  // Static factory: testler için kısa-yol.
  static SemanticVersion of(int... segs) {
    if (segs == null || segs.length == 0) {
      throw new IllegalArgumentException("En az bir segment gerekir");
    }
    List<Integer> list = new ArrayList<Integer>(segs.length);
    for (int s : segs) {
      list.add(s);
    }
    return new SemanticVersion(
        list, null, list.toString().replaceAll("[\\[\\] ,]", "").replace(",", "."));
  }

  static SemanticVersion of(int major, int minor, int patch, String prerelease) {
    return new SemanticVersion(Arrays.asList(major, minor, patch), prerelease, "");
  }
}
