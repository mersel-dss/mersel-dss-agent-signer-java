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
package io.mersel.dss.agent.api.services.timestamp;

import org.apache.commons.lang3.StringUtils;

import io.mersel.dss.agent.api.exceptions.TimestampException;
import io.mersel.dss.agent.api.services.timestamp.tubitak.TubitakTspDetector;

/**
 * Bir zaman damgası talebi için kullanılacak TSA sağlayıcısının çalışma-anı kimlik bilgilerini
 * taşıyan değişmez (immutable) parametre nesnesi.
 *
 * <p><b>Sunucu projesinden farkı:</b> {@code mersel-dss-server-signer} bu değerleri {@code
 * TS_SERVER_HOST} / {@code TS_USER_ID} / {@code TS_USER_PASSWORD} ortam değişkenlerinden (Spring
 * {@code @Value}) okur; ortam başına tek bir TSA sabittir. Agent ise <b>masaüstü uygulamasında
 * kayıtlı</b> sağlayıcı bilgilerini her API isteğinde parametre olarak alır. Bu sayede tek bir
 * agent kurulumu, isteğe göre farklı TSA'lara (TÜBİTAK ESYA dahil) zaman damgası talebi
 * gönderebilir; kimlik bilgileri sunucuda saklanmaz.
 *
 * <p>{@code tubitak} bayrağı açıkça verilmezse {@link TubitakTspDetector} ile KamuSM host'larından
 * otomatik türetilir.
 */
public final class TimestampProvider {

  private final String url;
  private final String userId;
  private final String password;
  private final boolean tubitak;

  public TimestampProvider(String url, String userId, String password, boolean tubitak) {
    this.url = url == null ? null : url.trim();
    this.userId = userId == null ? null : userId.trim();
    this.password = password;
    this.tubitak = tubitak;
  }

  /**
   * İstek parametrelerinden bir sağlayıcı kurar; {@code tubitakFlag} null ise host'tan otomatik
   * tespit yapılır.
   *
   * @param url zaman damgası sunucu endpoint'i (zorunlu)
   * @param userId TSA kullanıcı / müşteri numarası (TÜBİTAK için zorunlu)
   * @param password TSA parolası (TÜBİTAK için zorunlu)
   * @param tubitakFlag istemcinin gönderdiği açık bayrak ({@code null} olabilir)
   * @throws TimestampException URL boşsa
   */
  public static TimestampProvider fromRequest(
      String url, String userId, String password, Boolean tubitakFlag) {
    if (StringUtils.isBlank(url)) {
      throw new TimestampException(
          "Zaman damgası sunucu adresi (tsaUrl) zorunludur. Masaüstü uygulamasında kayıtlı "
              + "sağlayıcı adresini parametre olarak gönderin.");
    }
    boolean resolved =
        TubitakTspDetector.resolveTubitakTspMode(Boolean.TRUE.equals(tubitakFlag), url);
    return new TimestampProvider(url, userId, password, resolved);
  }

  public String getUrl() {
    return url;
  }

  public String getUserId() {
    return userId;
  }

  public String getPassword() {
    return password;
  }

  public boolean isTubitak() {
    return tubitak;
  }

  public boolean hasCredentials() {
    return StringUtils.isNotBlank(userId);
  }

  /**
   * TÜBİTAK müşteri numarasını {@code int} olarak döndürür.
   *
   * @throws TimestampException kullanıcı no boş veya sayısal değilse
   */
  public int requireCustomerId() {
    if (StringUtils.isBlank(userId)) {
      throw new TimestampException(
          "TÜBİTAK zaman damgası için müşteri numarası (tsUserId) zorunludur.");
    }
    try {
      return Integer.parseInt(userId.trim());
    } catch (NumberFormatException e) {
      throw new TimestampException("Müşteri numarası (tsUserId) sayısal olmalı: " + userId, e);
    }
  }
}
