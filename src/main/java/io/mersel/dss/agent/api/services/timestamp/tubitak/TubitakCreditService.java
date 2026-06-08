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

import java.security.MessageDigest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.mersel.dss.agent.api.dtos.TubitakCreditResponseDto;
import io.mersel.dss.agent.api.exceptions.TimestampException;
import io.mersel.dss.agent.api.services.timestamp.TimestampHttpClient;
import io.mersel.dss.agent.api.services.timestamp.TimestampProvider;

/**
 * TÜBİTAK ESYA zaman damgası kontör sorgulama servisi.
 *
 * <p>Sunucu projesinden farkı: müşteri no / parola / sunucu adresi ortam değişkeninden değil
 * doğrudan API isteğinde {@link TimestampProvider} parametresi olarak alınır. {@code
 * SHA1(customerId + epochMillis)} hash'i TÜBİTAK kimlik token'ı ile şifrelenerek {@code identity}
 * header'ında, müşteri no ve zaman bilgisi {@code credit_req} / {@code credit_req_time}
 * header'larında gönderilir; TSA kalan kontörü düz metin döner.
 */
@Service
public class TubitakCreditService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TubitakCreditService.class);

  private final TimestampHttpClient timestampHttpClient;

  public TubitakCreditService(TimestampHttpClient timestampHttpClient) {
    this.timestampHttpClient = timestampHttpClient;
  }

  /**
   * TÜBİTAK zaman damgası kontör bilgisini sorgular.
   *
   * @param provider çalışma-anı sağlayıcı bilgileri (TÜBİTAK modu zorunlu)
   * @return kalan kontör bilgisi
   * @throws TimestampException TÜBİTAK modu aktif değilse veya sorgulama başarısız olursa
   */
  public TubitakCreditResponseDto checkCredit(TimestampProvider provider) {
    if (!provider.isTubitak()) {
      throw new TimestampException(
          "Kontör sorgulama yalnızca TÜBİTAK zaman damgası sağlayıcısı için kullanılabilir. "
              + "tubitak=true gönderin veya KamuSM (zd/tzd.kamusm.gov.tr) adresini kullanın.");
    }

    int customerId = provider.requireCustomerId();
    if (StringUtils.isBlank(provider.getPassword())) {
      throw new TimestampException(
          "TÜBİTAK kontör sorgulaması için parola (tsUserPassword) zorunludur.");
    }

    try {
      long timestamp = System.currentTimeMillis();

      String authString = customerId + String.valueOf(timestamp);
      byte[] authHash = calculateSha1(authString.getBytes());

      String authToken =
          TubitakAuthenticationHelper.encryptIdentity(customerId, provider.getPassword(), authHash);

      byte[] responseBytes =
          timestampHttpClient.postCreditQuery(provider, customerId, timestamp, authToken);
      String creditInfo = new String(responseBytes).trim();

      LOGGER.info("TÜBİTAK kontör sorgulaması başarılı. Müşteri ID: {}", customerId);

      return new TubitakCreditResponseDto(parseCredit(creditInfo), customerId, creditInfo);

    } catch (TimestampException e) {
      throw e;
    } catch (Exception e) {
      LOGGER.error("TÜBİTAK kontör sorgulaması başarısız: {}", e.getMessage());
      throw new TimestampException("Kontör sorgulaması başarısız: " + e.getMessage(), e);
    }
  }

  private byte[] calculateSha1(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-1").digest(data);
    } catch (Exception e) {
      throw new TimestampException("SHA-1 hesaplama hatası", e);
    }
  }

  private Long parseCredit(String response) {
    try {
      return Long.parseLong(response.trim());
    } catch (Exception e) {
      LOGGER.warn("Kontör bilgisi sayıya çevrilemedi: {}", response);
      return null;
    }
  }
}
