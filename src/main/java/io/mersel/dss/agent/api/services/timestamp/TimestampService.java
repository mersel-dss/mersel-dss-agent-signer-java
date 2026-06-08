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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenInfo;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.mersel.dss.agent.api.dtos.TimestampResponseDto;
import io.mersel.dss.agent.api.dtos.TimestampValidationResponseDto;
import io.mersel.dss.agent.api.exceptions.TimestampException;
import io.mersel.dss.agent.api.models.enums.TimestampHashAlgorithm;

/**
 * Zaman damgası (RFC 3161) işlemleri için servis.
 *
 * <p>Sunucu projesindeki ({@code mersel-dss-server-signer}) DSS tabanlı {@code TimestampService}'in
 * agent karşılığıdır. <b>İki temel fark vardır:</b>
 *
 * <ol>
 *   <li><b>Bağımlılık:</b> Agent Avrupa DSS kütüphanesini taşımaz; TSQ üretimi, TSR ayrıştırması ve
 *       doğrulama tamamen BouncyCastle ({@code org.bouncycastle.tsp}) ile, HTTP taşıması ise OkHttp
 *       ({@link TimestampHttpClient}) ile yapılır.
 *   <li><b>Kimlik:</b> TSA sağlayıcısı ve kimlik bilgileri ortam değişkeninden değil her istekte
 *       parametre olarak ({@link TimestampProvider}) verilir. Böylece masaüstü uygulamasında
 *       kayıtlı sağlayıcı bilgileri çağrı bazında geçirilir; TÜBİTAK ESYA dahil her TSA
 *       desteklenir.
 * </ol>
 */
@Service
public class TimestampService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimestampService.class);

  /** İmza algoritması: birleşik (digest+encryption) OID → insan-okunur ad. */
  private static final Map<String, String> COMBINED_SIG_ALG_NAMES = new HashMap<>();

  /** İmza algoritması: anahtar tipi (encryption) OID → temel ad (RSA / ECDSA / DSA). */
  private static final Map<String, String> KEY_TYPE_NAMES = new HashMap<>();

  static {
    COMBINED_SIG_ALG_NAMES.put("1.2.840.113549.1.1.5", "SHA-1 with RSA");
    COMBINED_SIG_ALG_NAMES.put("1.2.840.113549.1.1.11", "SHA-256 with RSA");
    COMBINED_SIG_ALG_NAMES.put("1.2.840.113549.1.1.12", "SHA-384 with RSA");
    COMBINED_SIG_ALG_NAMES.put("1.2.840.113549.1.1.13", "SHA-512 with RSA");
    COMBINED_SIG_ALG_NAMES.put("1.2.840.113549.1.1.10", "RSASSA-PSS");
    COMBINED_SIG_ALG_NAMES.put("1.2.840.10045.4.1", "SHA-1 with ECDSA");
    COMBINED_SIG_ALG_NAMES.put("1.2.840.10045.4.3.2", "SHA-256 with ECDSA");
    COMBINED_SIG_ALG_NAMES.put("1.2.840.10045.4.3.3", "SHA-384 with ECDSA");
    COMBINED_SIG_ALG_NAMES.put("1.2.840.10045.4.3.4", "SHA-512 with ECDSA");

    KEY_TYPE_NAMES.put("1.2.840.113549.1.1.1", "RSA");
    KEY_TYPE_NAMES.put("1.2.840.10045.2.1", "ECDSA");
    KEY_TYPE_NAMES.put("1.2.840.10040.4.1", "DSA");
  }

  private final TimestampHttpClient timestampHttpClient;

  public TimestampService(TimestampHttpClient timestampHttpClient) {
    this.timestampHttpClient = timestampHttpClient;
  }

  /**
   * Binary belge için zaman damgası alır.
   *
   * @param documentData belge verisi
   * @param hashAlgorithm hash algoritması adı ({@code null} ise SHA256)
   * @param provider çalışma-anı TSA sağlayıcı bilgileri (parametre tabanlı)
   * @param certReq TSA sertifikasının yanıta eklenmesi istensin mi
   * @param useNonce nonce kullanılsın mı (tekrar saldırısına karşı önerilir)
   * @return zaman damgası yanıtı
   * @throws TimestampException zaman damgası alınamadığında
   */
  public TimestampResponseDto getTimestamp(
      byte[] documentData,
      String hashAlgorithm,
      TimestampProvider provider,
      boolean certReq,
      boolean useNonce) {

    TimestampHashAlgorithm hashAlg = TimestampHashAlgorithm.fromName(hashAlgorithm);
    LOGGER.info(
        "Zaman damgası talebi. Hash: {}, TSA: {}, TÜBİTAK: {}",
        hashAlg.getDisplayName(),
        provider.getUrl(),
        provider.isTubitak());

    try {
      byte[] digest = computeDigest(documentData, hashAlg);

      TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
      reqGen.setCertReq(certReq);

      BigInteger nonce = useNonce ? new BigInteger(64, new SecureRandom()) : null;
      TimeStampRequest tsRequest =
          nonce != null
              ? reqGen.generate(hashAlg.getTspOid(), digest, nonce)
              : reqGen.generate(hashAlg.getTspOid(), digest);

      byte[] tsq = tsRequest.getEncoded();

      byte[] tsr = timestampHttpClient.postTimestampQuery(provider, tsq, digest);

      TimeStampToken token = parseTimestampToken(tsr);
      if (token == null) {
        throw new TimestampException("TSA yanıtı geçerli bir zaman damgası içermiyor.");
      }

      // Nonce gönderildiyse ve TSA onu döndürdüyse eşleşmeyi doğrula (replay koruması). Bazı
      // TSA'lar nonce'ı yansıtmaz; bu durumda fail etmek yerine uyarı log'larız (interop).
      if (nonce != null) {
        BigInteger respNonce = token.getTimeStampInfo().getNonce();
        if (respNonce != null && !nonce.equals(respNonce)) {
          throw new TimestampException(
              "TSA yanıtındaki nonce talep ile eşleşmiyor — yanıt güvenilir değil.");
        }
        if (respNonce == null) {
          LOGGER.warn("TSA nonce'ı yanıta yansıtmadı; replay doğrulaması atlandı.");
        }
      }

      byte[] tokenBytes = token.getEncoded();
      TimeStampTokenInfo info = token.getTimeStampInfo();

      TimestampResponseDto response = new TimestampResponseDto();
      response.setTimestampToken(Base64.getEncoder().encodeToString(tokenBytes));
      response.setTimestamp(formatInstant(info.getGenTime()));
      response.setHashAlgorithm(hashAlg.getDisplayName());
      response.setSerialNumber(info.getSerialNumber().toString());
      if (info.getNonce() != null) {
        response.setNonce(info.getNonce().toString());
      }

      X509CertificateHolder signerCert = getSignerCertificate(token);
      if (signerCert != null) {
        response.setTsaName(signerCert.getSubject().toString());
      }

      LOGGER.info("Zaman damgası alındı. Zaman: {}", response.getTimestamp());
      return response;

    } catch (TimestampException e) {
      throw e;
    } catch (Exception e) {
      LOGGER.error("Zaman damgası alınırken hata oluştu", e);
      throw new TimestampException("Zaman damgası alınamadı: " + e.getMessage(), e);
    }
  }

  /**
   * Zaman damgasını doğrular.
   *
   * @param timestampBytes timestamp token byte'ları (.tst veya TimeStampResponse)
   * @param originalData orijinal belge (opsiyonel; hash doğrulaması için)
   * @return doğrulama sonucu
   */
  public TimestampValidationResponseDto validateTimestamp(
      byte[] timestampBytes, byte[] originalData) {
    TimestampValidationResponseDto response = new TimestampValidationResponseDto();
    List<String> errors = new ArrayList<>();

    try {
      LOGGER.info("Zaman damgası doğrulama. Token boyutu: {} bytes", timestampBytes.length);

      TimeStampToken token = parseTimestampToken(timestampBytes);
      if (token == null) {
        errors.add("Timestamp token ayrıştırılamadı (geçersiz format)");
        response.setValid(false);
        response.setErrors(errors);
        response.setMessage("Zaman damgası token'ı geçersiz format");
        return response;
      }

      TimeStampTokenInfo info = token.getTimeStampInfo();
      response.setTimestamp(formatInstant(info.getGenTime()));

      String hashOid = info.getMessageImprintAlgOID().getId();
      response.setHashAlgorithmOid(hashOid);
      TimestampHashAlgorithm hashAlg = TimestampHashAlgorithm.forOid(hashOid);
      response.setHashAlgorithm(hashAlg != null ? hashAlg.getDisplayName() : hashOid);

      response.setSerialNumber(info.getSerialNumber().toString());
      if (info.getNonce() != null) {
        response.setNonce(info.getNonce().toString());
      }

      fillSignatureAlgorithm(token, response);

      X509CertificateHolder signerCert = getSignerCertificate(token);
      if (signerCert != null) {
        response.setTsaName(signerCert.getSubject().toString());
        response.setTsaCertificate(Base64.getEncoder().encodeToString(signerCert.getEncoded()));

        Date now = new Date();
        response.setCertificateNotBefore(formatInstant(signerCert.getNotBefore()));
        response.setCertificateNotAfter(formatInstant(signerCert.getNotAfter()));

        boolean certValid =
            now.after(signerCert.getNotBefore()) && now.before(signerCert.getNotAfter());
        response.setCertificateValid(certValid);
        if (!certValid) {
          errors.add("TSA sertifikası geçerlilik tarihleri dışında");
        }
      } else {
        errors.add("TSA sertifikası bulunamadı");
      }

      if (originalData != null) {
        try {
          byte[] messageImprint = info.getMessageImprintDigest();
          TimestampHashAlgorithm imprintAlg = TimestampHashAlgorithm.forOid(hashOid);
          if (imprintAlg == null) {
            imprintAlg = TimestampHashAlgorithm.SHA256;
          }
          byte[] computedHash = computeDigest(originalData, imprintAlg);
          boolean hashMatch = Arrays.equals(messageImprint, computedHash);
          response.setHashVerified(hashMatch);
          if (!hashMatch) {
            errors.add("Belge hash'i eşleşmiyor — belge değiştirilmiş olabilir");
          }
        } catch (Exception e) {
          errors.add("Hash doğrulaması yapılamadı: " + e.getMessage());
          LOGGER.warn("Hash doğrulaması başarısız", e);
        }
      }

      boolean valid = errors.isEmpty();
      response.setValid(valid);
      response.setErrors(errors);
      response.setMessage(
          valid ? "Zaman damgası geçerli ve doğrulandı" : "Zaman damgası doğrulaması başarısız");
      return response;

    } catch (Exception e) {
      LOGGER.error("Zaman damgası doğrulama hatası", e);
      errors.add("Genel doğrulama hatası: " + e.getMessage());
      response.setValid(false);
      response.setErrors(errors);
      response.setMessage("Zaman damgası doğrulanamadı");
      return response;
    }
  }

  /**
   * TSA yanıtını {@link TimeStampToken}'a ayrıştırır. Hem çıplak token (CMSSignedData / .tst) hem de
   * tam {@code TimeStampResponse} formatını destekler. Yanıt bir red (rejection) ise statü
   * string'iyle {@link TimestampException} fırlatır.
   */
  private TimeStampToken parseTimestampToken(byte[] bytes) {
    // 1) Çıplak token (CMSSignedData) — TÜBİTAK ve çoğu .tst dosyası bu formatta.
    try {
      CMSSignedData signedData = new CMSSignedData(bytes);
      return new TimeStampToken(signedData);
    } catch (Exception ignore) {
      LOGGER.debug("Yanıt çıplak token formatında değil, TimeStampResponse deneniyor.");
    }

    // 2) Tam TimeStampResponse (PKIStatus + token).
    try {
      TimeStampResponse tsResponse = new TimeStampResponse(bytes);
      TimeStampToken token = tsResponse.getTimeStampToken();
      if (token != null) {
        return token;
      }
      String statusString = tsResponse.getStatusString();
      throw new TimestampException(
          "TSA zaman damgası talebini reddetti (status="
              + tsResponse.getStatus()
              + (statusString != null ? ", " + statusString : "")
              + ")");
    } catch (TimestampException te) {
      throw te;
    } catch (Exception ignore) {
      LOGGER.debug("Yanıt TimeStampResponse formatında da değil.");
    }

    return null;
  }

  /** Timestamp token'dan imzalayan TSA sertifikasını alır. */
  private X509CertificateHolder getSignerCertificate(TimeStampToken token) {
    try {
      Store<X509CertificateHolder> certStore = token.getCertificates();
      Collection<X509CertificateHolder> certs = certStore.getMatches(null);
      if (!certs.isEmpty()) {
        return certs.iterator().next();
      }
    } catch (Exception e) {
      LOGGER.warn("TSA sertifikası alınamadı: {}", e.getMessage());
    }
    return null;
  }

  /** İmza algoritması bilgisini (isim + OID) token'ın imza bilgisinden doldurur. */
  private void fillSignatureAlgorithm(TimeStampToken token, TimestampValidationResponseDto response) {
    try {
      CMSSignedData signedData = token.toCMSSignedData();
      Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
      if (signers.isEmpty()) {
        return;
      }
      SignerInformation signerInfo = signers.iterator().next();
      String digestOid = signerInfo.getDigestAlgOID();
      String encryptionOid = signerInfo.getEncryptionAlgOID();
      response.setSignatureAlgorithmOid(encryptionOid);
      response.setSignatureAlgorithm(resolveSignatureAlgorithmName(digestOid, encryptionOid));
    } catch (Exception e) {
      LOGGER.debug("İmza algoritması bilgisi alınamadı: {}", e.getMessage());
    }
  }

  private String resolveSignatureAlgorithmName(String digestOid, String encryptionOid) {
    if (encryptionOid == null) {
      return null;
    }
    String direct = COMBINED_SIG_ALG_NAMES.get(encryptionOid);
    if (direct != null) {
      return direct;
    }
    String base = KEY_TYPE_NAMES.get(encryptionOid);
    if (base != null) {
      TimestampHashAlgorithm digest = TimestampHashAlgorithm.forOid(digestOid);
      String digestName = digest != null ? digest.getDisplayName() : digestOid;
      return digestName + " with " + base;
    }
    return encryptionOid;
  }

  private byte[] computeDigest(byte[] data, TimestampHashAlgorithm algorithm) {
    try {
      MessageDigest digest = MessageDigest.getInstance(algorithm.getJavaName());
      return digest.digest(data);
    } catch (Exception e) {
      throw new TimestampException("Hash hesaplanamadı: " + e.getMessage(), e);
    }
  }

  /** {@link Date}'i ISO 8601 UTC ({@code yyyy-MM-dd'T'HH:mm:ss'Z'}) formatına çevirir. */
  private static String formatInstant(Date date) {
    if (date == null) {
      return null;
    }
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    return fmt.format(date);
  }
}
