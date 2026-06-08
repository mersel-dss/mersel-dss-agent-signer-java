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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.exceptions.TimestampException;
import io.mersel.dss.agent.api.services.timestamp.tubitak.TubitakAuthenticationHelper;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * RFC 3161 zaman damgası ({@code application/timestamp-query}) ve TÜBİTAK ESYA kontör isteklerinin
 * HTTP taşımasını yapan istemci.
 *
 * <p>Sunucu projesinin {@code TubitakTimestampDataLoader} / Apache HttpClient çözümünün agent'taki
 * karşılığıdır. Agent OkHttp kullandığından (Apache HttpClient bağımlılığı yok) transport burada
 * OkHttp ile gerçeklenir. Kimlik doğrulama tamamen <b>çalışma-anı parametrelerinden</b> ({@link
 * TimestampProvider}) sürülür:
 *
 * <ul>
 *   <li><b>TÜBİTAK</b>: TSQ'nun message-imprint hash'inden {@code identity} header'ı üretilir,
 *       {@code User-Agent: UEKAE TSS Client} eklenir.
 *   <li><b>Standart TSA</b>: kullanıcı/parola verilmişse HTTP Basic {@code Authorization} header'ı
 *       (preemptive) eklenir.
 * </ul>
 *
 * <p>Test edilebilirlik: ctor {@link OkHttpClient}'ı dışarıdan kabul eder; testte {@code
 * MockWebServer} hedefi geçilebilir.
 */
@Component
public class TimestampHttpClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimestampHttpClient.class);

  private static final MediaType TSQ_MEDIA_TYPE = MediaType.parse("application/timestamp-query");

  private static final String IDENTITY_HEADER = "identity";
  private static final String CREDIT_REQ_HEADER = "credit_req";
  private static final String CREDIT_REQ_TIME_HEADER = "credit_req_time";
  private static final String USER_AGENT_HEADER = "User-Agent";
  private static final String TUBITAK_USER_AGENT = "UEKAE TSS Client";

  private static final int DEFAULT_TIMEOUT_MS = 30_000;

  private final OkHttpClient httpClient;

  public TimestampHttpClient() {
    this(defaultClient());
  }

  /** Test dostu ctor. */
  public TimestampHttpClient(OkHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  private static OkHttpClient defaultClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build();
  }

  /**
   * TSQ'yu (Time Stamp Query) sağlayıcıya POST eder ve ham TSR (Time Stamp Response) byte'larını
   * döndürür.
   *
   * @param provider çalışma-anı sağlayıcı kimlik bilgileri
   * @param tsq DER-encoded TimeStampReq
   * @param messageImprintDigest TSQ'daki message-imprint hash'i (TÜBİTAK identity header'ı için)
   * @return ham TSR byte'ları
   * @throws TimestampException ağ/HTTP hatası veya 2xx olmayan yanıt durumunda
   */
  public byte[] postTimestampQuery(
      TimestampProvider provider, byte[] tsq, byte[] messageImprintDigest) {

    Request.Builder builder =
        new Request.Builder()
            .url(provider.getUrl())
            .addHeader("Accept", "application/timestamp-reply")
            .post(RequestBody.create(tsq, TSQ_MEDIA_TYPE));

    if (provider.isTubitak()) {
      int customerId = provider.requireCustomerId();
      if (StringUtils.isBlank(provider.getPassword())) {
        throw new TimestampException(
            "TÜBİTAK zaman damgası için parola (tsUserPassword) zorunludur.");
      }
      String authToken =
          TubitakAuthenticationHelper.encryptIdentity(
              customerId, provider.getPassword(), messageImprintDigest);
      builder.addHeader(IDENTITY_HEADER, authToken);
      builder.addHeader(USER_AGENT_HEADER, TUBITAK_USER_AGENT);
      LOGGER.debug("TÜBİTAK kimlik doğrulama eklendi. URL: {}", provider.getUrl());
    } else if (provider.hasCredentials()) {
      builder.addHeader(
          "Authorization",
          Credentials.basic(
              provider.getUserId(), provider.getPassword() == null ? "" : provider.getPassword()));
      LOGGER.debug("HTTP Basic Auth eklendi. Kullanıcı: {}", provider.getUserId());
    }

    return execute(builder.build(), "zaman damgası");
  }

  /**
   * TÜBİTAK ESYA kontör sorgusu yapar. {@code identity} + {@code credit_req} + {@code
   * credit_req_time} header'ları ile boş gövdeli bir istek gönderir.
   *
   * @param provider çalışma-anı sağlayıcı kimlik bilgileri (TÜBİTAK olmalı)
   * @param customerId müşteri numarası
   * @param timestampMillis epoch millis (auth string + header için)
   * @param authToken {@code SHA1(customerId + timestampMillis)} üzerinden üretilmiş identity token
   * @return ham yanıt byte'ları
   * @throws TimestampException ağ/HTTP hatası durumunda
   */
  public byte[] postCreditQuery(
      TimestampProvider provider, int customerId, long timestampMillis, String authToken) {

    Request request =
        new Request.Builder()
            .url(provider.getUrl())
            .addHeader(USER_AGENT_HEADER, TUBITAK_USER_AGENT)
            .addHeader(IDENTITY_HEADER, authToken)
            .addHeader(CREDIT_REQ_HEADER, String.valueOf(customerId))
            .addHeader(CREDIT_REQ_TIME_HEADER, String.valueOf(timestampMillis))
            .post(RequestBody.create(new byte[0], TSQ_MEDIA_TYPE))
            .build();

    return execute(request, "kontör sorgulama");
  }

  private byte[] execute(Request request, String operation) {
    try (Response response = httpClient.newCall(request).execute()) {
      int code = response.code();
      LOGGER.debug("{} HTTP yanıt kodu: {}", operation, code);

      ResponseBody body = response.body();
      byte[] bytes = body == null ? new byte[0] : body.bytes();

      if (!response.isSuccessful()) {
        String detail = bytes.length > 0 && bytes.length < 512 ? new String(bytes).trim() : "";
        throw new TimestampException(
            "TSA "
                + operation
                + " isteği başarısız: HTTP "
                + code
                + (detail.isEmpty() ? "" : " — " + detail));
      }
      return bytes;
    } catch (IOException e) {
      throw new TimestampException(
          "TSA " + operation + " isteği sırasında I/O hatası: " + e.getMessage(), e);
    }
  }
}
