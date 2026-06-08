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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TÜBİTAK ESYA Zaman Damgası servisi için kimlik doğrulama yardımcı sınıfı.
 *
 * <p>TÜBİTAK zaman damgası sunucusunun gerektirdiği özel kimlik doğrulama mekanizmasını uygular.
 * Müşteri kimlik bilgileri ({@code customerId} + parola) ve timestamp verisinin hash'i
 * kullanılarak, PBKDF2 ile türetilen AES-256-CBC anahtarıyla şifrelenmiş bir authentication token
 * üretir. Token DER-encoded ASN.1 dizisidir ve hex string olarak {@code identity} HTTP header'ında
 * gönderilir.
 */
public final class TubitakAuthenticationHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(TubitakAuthenticationHelper.class);

  private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final String AES_ALGORITHM = "AES";
  private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";

  private static final int KEY_LENGTH = 256;
  private static final int DEFAULT_ITERATION_COUNT = 100;

  private static final int SALT_SIZE = 16;
  private static final int IV_SIZE = 16;

  private TubitakAuthenticationHelper() {}

  /**
   * TÜBİTAK kimlik doğrulama token'ı oluşturur.
   *
   * @param customerId müşteri numarası
   * @param customerPassword müşteri parolası
   * @param dataHash zaman damgası alınacak verinin (veya kontör isteği için auth string'inin) hash
   *     değeri
   * @return hex string formatında authentication token
   * @throws TubitakAuthenticationException şifreleme hatası durumunda
   */
  public static String encryptIdentity(int customerId, String customerPassword, byte[] dataHash) {
    return encryptIdentity(customerId, customerPassword, dataHash, null, DEFAULT_ITERATION_COUNT);
  }

  /**
   * TÜBİTAK kimlik doğrulama token'ı oluşturur (gelişmiş parametreler ile).
   *
   * @param customerId müşteri numarası
   * @param customerPassword müşteri parolası
   * @param dataHash hash değeri
   * @param salt kriptografik salt ({@code null} ise otomatik üretilir)
   * @param iterationCount anahtar türetme iterasyon sayısı
   * @return hex string formatında authentication token
   * @throws TubitakAuthenticationException şifreleme hatası durumunda
   */
  public static String encryptIdentity(
      int customerId, String customerPassword, byte[] dataHash, byte[] salt, int iterationCount) {

    try {
      byte[] effectiveSalt = salt;
      if (effectiveSalt == null) {
        SecureRandom random = new SecureRandom();
        effectiveSalt = new byte[SALT_SIZE];
        random.nextBytes(effectiveSalt);
      }

      SecureRandom random = new SecureRandom();
      byte[] iv = new byte[IV_SIZE];
      random.nextBytes(iv);

      SecretKey key = deriveKey(customerPassword, effectiveSalt, iterationCount);
      byte[] encryptedData = encrypt(dataHash, key, iv);
      byte[] authToken =
          buildAuthenticationToken(customerId, effectiveSalt, iterationCount, iv, encryptedData);
      String hexString = bytesToHex(authToken);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "TÜBİTAK authentication token üretildi. Müşteri ID: {}, token uzunluğu: {} bytes",
            customerId,
            authToken.length);
      }

      return hexString;

    } catch (Exception e) {
      throw new TubitakAuthenticationException("TÜBİTAK kimlik şifreleme başarısız", e);
    }
  }

  /** PBKDF2 ile anahtar türetir. */
  private static SecretKey deriveKey(String password, byte[] salt, int iterationCount)
      throws Exception {
    SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterationCount, KEY_LENGTH);
    SecretKey tmp = factory.generateSecret(spec);
    return new SecretKeySpec(tmp.getEncoded(), AES_ALGORITHM);
  }

  /** AES-256-CBC ile veriyi şifreler. */
  private static byte[] encrypt(byte[] data, SecretKey key, byte[] iv) throws Exception {
    Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
    IvParameterSpec ivSpec = new IvParameterSpec(iv);
    cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
    return cipher.doFinal(data);
  }

  /** Authentication token'ı ASN.1 yapısında oluşturur ve DER encode eder. */
  private static byte[] buildAuthenticationToken(
      int customerId, byte[] salt, int iterationCount, byte[] iv, byte[] encryptedData)
      throws IOException {

    ASN1EncodableVector v = new ASN1EncodableVector();
    v.add(new ASN1Integer(BigInteger.valueOf(customerId)));
    v.add(new DEROctetString(salt));
    v.add(new ASN1Integer(BigInteger.valueOf(iterationCount)));
    v.add(new DEROctetString(iv));
    v.add(new DEROctetString(encryptedData));

    DERSequence sequence = new DERSequence(v);

    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    ASN1OutputStream aOut = ASN1OutputStream.create(bOut, ASN1Encoding.DER);
    aOut.writeObject(sequence);
    aOut.close();

    return bOut.toByteArray();
  }

  /** Byte array'i hex string'e çevirir. */
  private static String bytesToHex(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /** TÜBİTAK kimlik doğrulama hatası. */
  public static class TubitakAuthenticationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TubitakAuthenticationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
