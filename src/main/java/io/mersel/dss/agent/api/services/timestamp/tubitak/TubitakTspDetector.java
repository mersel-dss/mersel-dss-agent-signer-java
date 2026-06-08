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
package io.mersel.dss.agent.api.services.timestamp.tubitak;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * KamuSM zaman damgası endpoint'lerini host bazlı tespit eden yardımcı.
 *
 * <p>KamuSM yalnızca iki sabit zaman damgası endpoint'i yayınlar:
 *
 * <ul>
 *   <li>{@code zd.kamusm.gov.tr} — production
 *   <li>{@code tzd.kamusm.gov.tr} — test
 * </ul>
 *
 * Bu host'lara giden trafik TÜBİTAK ESYA protokolü (custom {@code identity} header, AES-şifrelenmiş
 * kimlik) ile gönderilmek zorundadır; standart RFC 3161 HTTP Basic Auth ile istek atılırsa 403
 * alınır. Dolayısıyla host pattern'ından TÜBİTAK modu çıkarımı deterministik olarak güvenlidir.
 *
 * <p>Operasyonel motivasyon: kullanıcılar sağlayıcı adresini doğru verip {@code tubitak} bayrağını
 * göndermeyi unuttuğunda servis sessizce yanlış protokole düşüyordu. Bu sınıf bayrağı host'tan
 * türeterek bu sınıf hatasını fail-safe biçimde kapatır.
 */
public final class TubitakTspDetector {

  private static final Set<String> TUBITAK_TSP_HOSTS =
      Collections.unmodifiableSet(
          new HashSet<>(Arrays.asList("zd.kamusm.gov.tr", "tzd.kamusm.gov.tr")));

  private TubitakTspDetector() {}

  /**
   * Verilen URL'in host'unun KamuSM TÜBİTAK zaman damgası endpoint'i olup olmadığını döndürür.
   *
   * @param tspServerUrl zaman damgası sunucu URL'i (boş/null olabilir)
   * @return host KamuSM zaman damgası ise {@code true}
   */
  public static boolean isTubitakTspHost(String tspServerUrl) {
    if (tspServerUrl == null) {
      return false;
    }
    String trimmed = tspServerUrl.trim();
    if (trimmed.isEmpty()) {
      return false;
    }
    try {
      String host = URI.create(trimmed).getHost();
      if (host == null) {
        return false;
      }
      return TUBITAK_TSP_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  /**
   * Etkin TÜBİTAK modunu döndürür. İstemci bayrağı {@code true} ise her zaman {@code true}; aksi
   * halde host'a göre otomatik tespit yapılır.
   *
   * @param explicitFlag istemcinin gönderdiği {@code tubitak} bayrağı
   * @param tspServerUrl sağlayıcı URL'i
   * @return etkin TÜBİTAK modu
   */
  public static boolean resolveTubitakTspMode(boolean explicitFlag, String tspServerUrl) {
    return explicitFlag || isTubitakTspHost(tspServerUrl);
  }
}
