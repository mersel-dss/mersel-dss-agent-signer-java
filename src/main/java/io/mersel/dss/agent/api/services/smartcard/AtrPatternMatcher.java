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

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.smartcardio.ATR;

/**
 * Layer 2 kart tanıma yardımcı sınıfı: bir ATR hex string'inden historical-bytes'ı çıkarır ve
 * önceki kart tiplerinin {@code <atr-pattern>} regex'leriyle eşleştirir.
 *
 * <h2>Tasarım</h2>
 *
 * Layer 1 (tam ATR exact match) bilinen ATR'leri yakalar; ancak akıllı kart üreticileri her seri
 * için yeni bir ATR yayınlar — özellikle TÜBİTAK AKIS kartlarının sürüm numarası bayt'ları sürekli
 * artar. Yeni bir AKIS kartı için bizim {@code smartcard-config.xml}'imizde exact eşleşme
 * olmayabilir.
 *
 * <p>Layer 2 bu noktada devreye girer: ATR'in <em>historical bytes</em> bölümü vendor kimliğini
 * taşır (örn. {@code 80 67 55454B4145 ...} → "UEKAE" ASCII). Bir kez geniş bir regex (her ailenin
 * vendor parmak izi) yazıldığında, gelecekteki tüm sürüm varyantları otomatik olarak yakalanır.
 *
 * <h2>TÜBİTAK ma3api-smartcard-2.3.11 referansı</h2>
 *
 * Decompile edilmiş {@code AkisTemplate.isAkisATR()} aynı yaklaşımı kullanır. Bizim regex'lerimiz
 * de aynı kaynaktan derlenmiştir; ek olarak Mersel DSS'e özgü yaygın kart aileleri için (ALADDIN,
 * SAFESIGN, vb.) genişletilebilir.
 *
 * <h2>Tamamen saf — Spring bean değil</h2>
 *
 * Bu sınıf side-effect üretmez ve constructor parametresi almaz; {@link CardTypeRegistry}
 * tarafından dahili olarak çağrılır. Test edilebilirlik için tüm method'lar statiktir.
 */
public final class AtrPatternMatcher {

  private AtrPatternMatcher() {
    /* utility */
  }

  /**
   * Verilen ATR hex string'inin historical-bytes bölümünü {@link ATR#getHistoricalBytes()}
   * üzerinden çıkarır.
   *
   * @param atrHex ATR HEX string (boşluklu/boşluksuz, büyük/küçük harf duyarsız)
   * @return historical-bytes HEX (UPPER, boşluksuz) — ATR bozuksa boş Optional
   */
  public static Optional<String> extractHistoricalBytesHex(String atrHex) {
    byte[] raw = decodeHex(atrHex);
    if (raw == null || raw.length < 2) {
      return Optional.empty();
    }
    try {
      ATR atr = new ATR(raw);
      byte[] hb = atr.getHistoricalBytes();
      if (hb == null) return Optional.empty();
      return Optional.of(bytesToHex(hb));
    } catch (RuntimeException e) {
      // ATR ctor çoğunlukla sessizdir; T0 nibble'ı yanlış olursa NPE atabilir.
      return Optional.empty();
    }
  }

  /**
   * Verilen historical-bytes hex'i ile aday kart tipinin {@link
   * CardType#getHistoricalBytePatterns()} regex'lerinden herhangi biri eşleşiyorsa {@code true}
   * döner.
   *
   * <p>Eşleşme regex pre-anchored kabul edilir — TÜBİTAK formatı (örn. {@code
   * "806755454B4145....318073B3A180"}) tam-string-matches semantiğini varsayar, bu yüzden {@link
   * java.util.regex.Matcher#matches()} kullanılır.
   */
  public static boolean matches(String historicalBytesHex, CardType type) {
    if (historicalBytesHex == null || type == null) return false;
    for (Pattern p : type.getHistoricalBytePatterns()) {
      if (p.matcher(historicalBytesHex).matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * ATR'in historical-bytes'ını çıkarır ve bir kart tipi kümesinden uyanı bulur.
   *
   * @param atrHex ham ATR
   * @param candidates aday kart tipleri (genelde tüm registry)
   * @return ilk eşleşen kart tipi veya boş
   */
  public static Optional<CardType> matchByHistoricalBytes(
      String atrHex, Iterable<CardType> candidates) {
    Optional<String> hb = extractHistoricalBytesHex(atrHex);
    if (!hb.isPresent()) return Optional.empty();
    String hex = hb.get();
    for (CardType ct : candidates) {
      if (matches(hex, ct)) {
        return Optional.of(ct);
      }
    }
    return Optional.empty();
  }

  /* ----------------- hex helpers ----------------- */

  /**
   * Esnek hex decoder: boşlukları yutar, {@code 0x} önekini kabul eder. Bozuk input için {@code
   * null}.
   */
  static byte[] decodeHex(String hex) {
    if (hex == null) return null;
    String compact = hex.replaceAll("\\s+", "");
    if (compact.startsWith("0x") || compact.startsWith("0X")) {
      compact = compact.substring(2);
    }
    if (compact.length() == 0 || (compact.length() & 1) != 0) {
      return null;
    }
    byte[] out = new byte[compact.length() / 2];
    for (int i = 0; i < out.length; i++) {
      int hi = Character.digit(compact.charAt(i * 2), 16);
      int lo = Character.digit(compact.charAt(i * 2 + 1), 16);
      if (hi < 0 || lo < 0) return null;
      out[i] = (byte) ((hi << 4) | lo);
    }
    return out;
  }

  static String bytesToHex(byte[] bytes) {
    if (bytes == null) return "";
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
    }
    return sb.toString();
  }
}
