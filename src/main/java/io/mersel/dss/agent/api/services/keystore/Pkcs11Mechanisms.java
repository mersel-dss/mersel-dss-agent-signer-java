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
package io.mersel.dss.agent.api.services.keystore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PKCS#11 v2.40 §A "Mechanism Types" tablosundan en yaygın {@code CKM_*} kodlarının ve {@code
 * CKF_*} bayraklarının numara ↔ sembolik isim eşleştirmesi.
 *
 * <p>Token'ın hangi mekanizmaları desteklediğini sembolik formda yansıtmak için kullanılır (örn.
 * {@code CKM_SHA256_RSA_PKCS} yok → "Unsupported parameters"in nedeni netleşir). Tabloda olmayan
 * mekanizmalar {@code "0x00000XXX"} hex formatında dönülür ki destek için yine de görünür kalsın.
 *
 * <p>Tablo Türkiye'deki yaygın smart card senaryolarında (RSA-2048 NES, ECDSA-256/384 QES) imzalama
 * için kullanılan tüm mekanizmaları kapsar; kapsamlı PKCS#11 listesi gerekmez.
 */
public final class Pkcs11Mechanisms {

  /** İmzalama başarısı için token'da en az birinin bulunması beklenen RSA mekanizmaları. */
  public static final List<String> RSA_SIGN_MECHANISMS;

  /** İmzalama başarısı için token'da en az birinin bulunması beklenen ECDSA mekanizmaları. */
  public static final List<String> ECDSA_SIGN_MECHANISMS;

  private static final Map<Long, String> CKM_BY_VALUE;
  private static final Map<String, Long> CKM_BY_NAME;

  /** PKCS#11 v2.40 §6.2 CK_MECHANISM_INFO bayrakları. */
  public static final long CKF_HW = 0x00000001L;

  public static final long CKF_ENCRYPT = 0x00000100L;
  public static final long CKF_DECRYPT = 0x00000200L;
  public static final long CKF_DIGEST = 0x00000400L;
  public static final long CKF_SIGN = 0x00000800L;
  public static final long CKF_SIGN_RECOVER = 0x00001000L;
  public static final long CKF_VERIFY = 0x00002000L;
  public static final long CKF_VERIFY_RECOVER = 0x00004000L;
  public static final long CKF_GENERATE = 0x00008000L;
  public static final long CKF_GENERATE_KEY_PAIR = 0x00010000L;
  public static final long CKF_WRAP = 0x00020000L;
  public static final long CKF_UNWRAP = 0x00040000L;
  public static final long CKF_DERIVE = 0x00080000L;
  public static final long CKF_EC_F_P = 0x00100000L;
  public static final long CKF_EC_F_2M = 0x00200000L;
  public static final long CKF_EC_NAMEDCURVE = 0x00400000L;
  public static final long CKF_EC_UNCOMPRESS = 0x01000000L;
  public static final long CKF_EXTENSION = 0x80000000L;

  static {
    Map<Long, String> m = new HashMap<Long, String>();

    // RSA family
    m.put(0x00000000L, "CKM_RSA_PKCS_KEY_PAIR_GEN");
    m.put(0x00000001L, "CKM_RSA_PKCS");
    m.put(0x00000002L, "CKM_RSA_9796");
    m.put(0x00000003L, "CKM_RSA_X_509");
    m.put(0x00000004L, "CKM_MD2_RSA_PKCS");
    m.put(0x00000005L, "CKM_MD5_RSA_PKCS");
    m.put(0x00000006L, "CKM_SHA1_RSA_PKCS");
    m.put(0x00000007L, "CKM_RIPEMD128_RSA_PKCS");
    m.put(0x00000008L, "CKM_RIPEMD160_RSA_PKCS");
    m.put(0x00000009L, "CKM_RSA_PKCS_OAEP");
    m.put(0x0000000AL, "CKM_RSA_X9_31_KEY_PAIR_GEN");
    m.put(0x0000000BL, "CKM_RSA_X9_31");
    m.put(0x0000000CL, "CKM_SHA1_RSA_X9_31");
    m.put(0x0000000DL, "CKM_RSA_PKCS_PSS");
    m.put(0x0000000EL, "CKM_SHA1_RSA_PKCS_PSS");

    // SHA-2 RSA PKCS
    m.put(0x00000040L, "CKM_SHA256_RSA_PKCS");
    m.put(0x00000041L, "CKM_SHA384_RSA_PKCS");
    m.put(0x00000042L, "CKM_SHA512_RSA_PKCS");
    m.put(0x00000043L, "CKM_SHA256_RSA_PKCS_PSS");
    m.put(0x00000044L, "CKM_SHA384_RSA_PKCS_PSS");
    m.put(0x00000045L, "CKM_SHA512_RSA_PKCS_PSS");
    m.put(0x00000046L, "CKM_SHA224_RSA_PKCS");
    m.put(0x00000047L, "CKM_SHA224_RSA_PKCS_PSS");
    m.put(0x0000004AL, "CKM_SHA512_224");
    m.put(0x0000004BL, "CKM_SHA512_224_HMAC");
    m.put(0x0000004CL, "CKM_SHA512_224_HMAC_GENERAL");
    m.put(0x0000004DL, "CKM_SHA512_224_KEY_DERIVATION");
    m.put(0x0000004EL, "CKM_SHA512_256");
    m.put(0x0000004FL, "CKM_SHA512_256_HMAC");

    // DSA / DH (rare but legal)
    m.put(0x00000010L, "CKM_DSA_KEY_PAIR_GEN");
    m.put(0x00000011L, "CKM_DSA");
    m.put(0x00000012L, "CKM_DSA_SHA1");
    m.put(0x00000013L, "CKM_DSA_SHA224");
    m.put(0x00000014L, "CKM_DSA_SHA256");
    m.put(0x00000015L, "CKM_DSA_SHA384");
    m.put(0x00000016L, "CKM_DSA_SHA512");

    // SHA digests
    m.put(0x00000220L, "CKM_SHA_1");
    m.put(0x00000221L, "CKM_SHA_1_HMAC");
    m.put(0x00000250L, "CKM_SHA256");
    m.put(0x00000251L, "CKM_SHA256_HMAC");
    m.put(0x00000260L, "CKM_SHA384");
    m.put(0x00000261L, "CKM_SHA384_HMAC");
    m.put(0x00000270L, "CKM_SHA512");
    m.put(0x00000271L, "CKM_SHA512_HMAC");
    m.put(0x00000255L, "CKM_SHA224");
    m.put(0x00000256L, "CKM_SHA224_HMAC");

    // ECDSA family
    m.put(0x00001040L, "CKM_EC_KEY_PAIR_GEN");
    m.put(0x00001041L, "CKM_ECDSA");
    m.put(0x00001042L, "CKM_ECDSA_SHA1");
    m.put(0x00001043L, "CKM_ECDSA_SHA224");
    m.put(0x00001044L, "CKM_ECDSA_SHA256");
    m.put(0x00001045L, "CKM_ECDSA_SHA384");
    m.put(0x00001046L, "CKM_ECDSA_SHA512");
    m.put(0x00001050L, "CKM_ECDH1_DERIVE");
    m.put(0x00001051L, "CKM_ECDH1_COFACTOR_DERIVE");
    m.put(0x00001052L, "CKM_ECMQV_DERIVE");

    // AES (yardımcı)
    m.put(0x00001080L, "CKM_AES_KEY_GEN");
    m.put(0x00001081L, "CKM_AES_ECB");
    m.put(0x00001082L, "CKM_AES_CBC");
    m.put(0x00001085L, "CKM_AES_CBC_PAD");

    CKM_BY_VALUE = Collections.unmodifiableMap(m);

    Map<String, Long> reverse = new HashMap<String, Long>(m.size() * 2);
    for (Map.Entry<Long, String> e : m.entrySet()) {
      reverse.put(e.getValue(), e.getKey());
    }
    CKM_BY_NAME = Collections.unmodifiableMap(reverse);

    RSA_SIGN_MECHANISMS =
        Collections.unmodifiableList(
            new ArrayList<String>(
                java.util.Arrays.asList(
                    "CKM_SHA256_RSA_PKCS",
                    "CKM_SHA384_RSA_PKCS",
                    "CKM_SHA512_RSA_PKCS",
                    "CKM_SHA1_RSA_PKCS",
                    "CKM_RSA_PKCS")));
    ECDSA_SIGN_MECHANISMS =
        Collections.unmodifiableList(
            new ArrayList<String>(
                java.util.Arrays.asList(
                    "CKM_ECDSA_SHA384",
                    "CKM_ECDSA_SHA256",
                    "CKM_ECDSA_SHA512",
                    "CKM_ECDSA_SHA1",
                    "CKM_ECDSA")));
  }

  private Pkcs11Mechanisms() {
    /* utility */
  }

  /**
   * {@code CKM_*} numarası → sembolik isim. Tabloda yoksa {@code "0xXXXXXXXX"} hex string döner.
   */
  public static String nameOf(long ckm) {
    String known = CKM_BY_VALUE.get(ckm);
    if (known != null) {
      return known;
    }
    return String.format(Locale.ROOT, "0x%08X", ckm);
  }

  /** Sembolik isim → numara. Tanınmayan isim için {@code -1} döner. */
  public static long valueOf(String symbolicName) {
    if (symbolicName == null) {
      return -1L;
    }
    Long v = CKM_BY_NAME.get(symbolicName.trim().toUpperCase(Locale.ROOT));
    return v == null ? -1L : v;
  }

  /** {@code CK_MECHANISM_INFO.flags} → okunabilir sembolik isim listesi. */
  public static List<String> decodeFlags(long flags) {
    List<String> out = new ArrayList<String>(8);
    if ((flags & CKF_HW) != 0) out.add("CKF_HW");
    if ((flags & CKF_ENCRYPT) != 0) out.add("CKF_ENCRYPT");
    if ((flags & CKF_DECRYPT) != 0) out.add("CKF_DECRYPT");
    if ((flags & CKF_DIGEST) != 0) out.add("CKF_DIGEST");
    if ((flags & CKF_SIGN) != 0) out.add("CKF_SIGN");
    if ((flags & CKF_SIGN_RECOVER) != 0) out.add("CKF_SIGN_RECOVER");
    if ((flags & CKF_VERIFY) != 0) out.add("CKF_VERIFY");
    if ((flags & CKF_VERIFY_RECOVER) != 0) out.add("CKF_VERIFY_RECOVER");
    if ((flags & CKF_GENERATE) != 0) out.add("CKF_GENERATE");
    if ((flags & CKF_GENERATE_KEY_PAIR) != 0) out.add("CKF_GENERATE_KEY_PAIR");
    if ((flags & CKF_WRAP) != 0) out.add("CKF_WRAP");
    if ((flags & CKF_UNWRAP) != 0) out.add("CKF_UNWRAP");
    if ((flags & CKF_DERIVE) != 0) out.add("CKF_DERIVE");
    if ((flags & CKF_EC_F_P) != 0) out.add("CKF_EC_F_P");
    if ((flags & CKF_EC_F_2M) != 0) out.add("CKF_EC_F_2M");
    if ((flags & CKF_EC_NAMEDCURVE) != 0) out.add("CKF_EC_NAMEDCURVE");
    if ((flags & CKF_EC_UNCOMPRESS) != 0) out.add("CKF_EC_UNCOMPRESS");
    if ((flags & CKF_EXTENSION) != 0) out.add("CKF_EXTENSION");
    return out;
  }
}
