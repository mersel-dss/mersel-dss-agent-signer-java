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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mersel.dss.agent.api.exceptions.CertificateLookupException;
import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryException;

/**
 * PKCS#11 token üzerindeki <b>public</b> X.509 sertifikalarını <b>PIN'siz</b> okur.
 *
 * <p><b>Neden bu sınıf?</b> SunPKCS11'in {@code P11KeyStore.engineLoad()} davranışı bir tasarım
 * seçimidir: token'ın {@code CKF_LOGIN_REQUIRED} bayrağı set ise (Türkçe Kamu SM kartlarının tamamı
 * — AKIS, e-İmza, Mali Mühür, KamuSM QES bu bayrağı taşır), {@code KeyStore.load()} her durumda
 * {@code C_Login} yapmaya çalışır ve PIN olmadan {@link javax.security.auth.login
 * .LoginException}'la patlar. Oysa PKCS#11 spec'i v2.40 §10.4 gereği {@code CKO_CERTIFICATE}
 * objeleri {@code CKA_PRIVATE=FALSE} ile saklanır ve public session ile (login'siz) okunabilir.
 * SunPKCS11'in tasarım kararı, P11KeyStore'un tüm objects'i tek geçişte listeleyebilmesi için
 * login'i koşulsuz tetiklemesidir.
 *
 * <p>Bu sınıf {@code sun.security.pkcs11.wrapper.PKCS11} low-level JNI sarmalayıcısını (reflection
 * üzerinden) çağırarak P11KeyStore katmanını tamamen atlar ve doğrudan PKCS#11 çağrılarına iner:
 *
 * <pre>
 *   C_OpenSession(slot, CKF_SERIAL_SESSION, null, null)   // public R/O session, login YOK
 *     C_FindObjectsInit(template={CKA_CLASS=CKO_CERTIFICATE, CKA_CERTIFICATE_TYPE=CKC_X_509})
 *     C_FindObjects(...)                                  // sertifika handle'larını al
 *     C_FindObjectsFinal(...)
 *     for each handle:
 *       C_GetAttributeValue(handle, {CKA_LABEL, CKA_ID, CKA_VALUE})
 *   C_CloseSession(...)
 * </pre>
 *
 * <p>Alias çözümü: önce {@code CKA_LABEL} (UTF-8 string), yoksa {@code CKA_ID} hex-encode, o da
 * yoksa {@code "cert-<handleHex>"} fallback'i. {@link Pkcs11Session#resolveAlias(String)} imzalama
 * akışında bu alias'larla eşleşir (CKA_LABEL canonical).
 */
public final class Pkcs11PublicCertificateReader {

  private static final Logger log = LoggerFactory.getLogger(Pkcs11PublicCertificateReader.class);

  // PKCS#11 v2.40 sabitleri (sun.security.pkcs11.wrapper.PKCS11Constants ile aynı değerler).
  private static final long CKA_CLASS = 0x00000000L;
  private static final long CKA_LABEL = 0x00000003L;
  private static final long CKA_ID = 0x00000102L;
  private static final long CKA_VALUE = 0x00000011L;
  private static final long CKA_CERTIFICATE_TYPE = 0x00000080L;
  private static final long CKO_CERTIFICATE = 0x00000001L;
  private static final long CKC_X_509 = 0x00000000L;
  private static final long CKF_SERIAL_SESSION = 0x00000004L;

  private static final int FIND_BATCH_SIZE = 128;

  private Pkcs11PublicCertificateReader() {
    /* static-only */
  }

  /**
   * Verilen PKCS#11 kütüphanesinin tüm token'larındaki X.509 sertifikalarını okur. Token yoksa boş
   * liste döner. Slot bazlı hatalar (bozuk token, removed card) yutulur — geri kalan slot'lar
   * denenir.
   *
   * @throws Pkcs11LibraryException kütüphane initialize edilemediğinde ya da reflection katmanı
   *     kurulamadığında
   */
  public static List<TokenCertificate> read(Path libraryPath) {
    if (libraryPath == null) {
      throw new IllegalArgumentException("libraryPath null olamaz.");
    }
    Pkcs11Reflection r = Pkcs11Reflection.load();
    Object p11 = r.getInstance(libraryPath.toString());

    long[] slotList;
    try {
      slotList = r.getSlotList(p11, true);
    } catch (ReflectiveOperationException e) {
      throw new Pkcs11LibraryException(
          "C_GetSlotList başarısız (" + libraryPath + "): " + rootMessage(e), e);
    }
    if (slotList == null || slotList.length == 0) {
      log.warn("PKCS#11 slot'unda token yok: {}", libraryPath);
      return new ArrayList<TokenCertificate>();
    }

    CertificateFactory cf;
    try {
      cf = CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      throw new CertificateLookupException(
          "X.509 CertificateFactory bulunamadı: " + e.getMessage(), e);
    }

    List<TokenCertificate> result = new ArrayList<TokenCertificate>();
    for (long slotID : slotList) {
      try {
        result.addAll(readSlot(r, p11, slotID, cf));
      } catch (ReflectiveOperationException e) {
        log.warn("Slot okuma hatası (slot={}, lib={}): {}", slotID, libraryPath, rootMessage(e));
      }
    }
    return result;
  }

  private static List<TokenCertificate> readSlot(
      Pkcs11Reflection r, Object p11, long slotID, CertificateFactory cf)
      throws ReflectiveOperationException {

    long session = r.openSession(p11, slotID, CKF_SERIAL_SESSION);
    try {
      Object template = r.newAttrArray(2);
      r.setAttr(template, 0, CKA_CLASS, Long.valueOf(CKO_CERTIFICATE));
      r.setAttr(template, 1, CKA_CERTIFICATE_TYPE, Long.valueOf(CKC_X_509));
      r.findObjectsInit(p11, session, template);

      List<Long> handles = new ArrayList<Long>();
      try {
        long[] batch;
        do {
          batch = r.findObjects(p11, session, FIND_BATCH_SIZE);
          if (batch == null) {
            break;
          }
          for (long h : batch) {
            handles.add(h);
          }
        } while (batch.length > 0);
      } finally {
        try {
          r.findObjectsFinal(p11, session);
        } catch (ReflectiveOperationException ignore) {
          /* noop — close anyway */
        }
      }

      List<TokenCertificate> certs = new ArrayList<TokenCertificate>(handles.size());
      for (Long handle : handles) {
        String alias = readAlias(r, p11, session, handle);
        X509Certificate cert = readCertificate(r, p11, session, handle, cf);
        if (cert != null) {
          certs.add(new TokenCertificate(alias, cert));
        }
      }
      return certs;
    } finally {
      try {
        r.closeSession(p11, session);
      } catch (ReflectiveOperationException ignore) {
        /* noop */
      }
    }
  }

  /**
   * Alias çözümü: CKA_LABEL (UTF-8) → CKA_ID (hex) → {@code "cert-<handle>"} fallback. {@link
   * Pkcs11Session#resolveAlias(String)} CKA_LABEL üzerinden eşleşir; bu yüzden CKA_LABEL en güçlü
   * tercih.
   */
  private static String readAlias(Pkcs11Reflection r, Object p11, long session, long handle) {
    try {
      Object attrs = r.newAttrArray(2);
      r.setAttr(attrs, 0, CKA_LABEL, null);
      r.setAttr(attrs, 1, CKA_ID, null);
      r.getAttributeValue(p11, session, handle, attrs);

      byte[] label = asByteArray(r.getAttrValue(attrs, 0));
      if (label != null && label.length > 0) {
        String s = new String(label, StandardCharsets.UTF_8).trim();
        if (!s.isEmpty()) {
          return s;
        }
      }
      byte[] id = asByteArray(r.getAttrValue(attrs, 1));
      if (id != null && id.length > 0) {
        StringBuilder sb = new StringBuilder(id.length * 2);
        for (byte b : id) {
          sb.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
        }
        return sb.toString();
      }
    } catch (ReflectiveOperationException e) {
      log.debug("Alias okunamadı (handle={}): {}", handle, rootMessage(e));
    }
    return "cert-" + Long.toHexString(handle);
  }

  private static X509Certificate readCertificate(
      Pkcs11Reflection r, Object p11, long session, long handle, CertificateFactory cf) {
    try {
      Object attrs = r.newAttrArray(1);
      r.setAttr(attrs, 0, CKA_VALUE, null);
      r.getAttributeValue(p11, session, handle, attrs);

      byte[] der = asByteArray(r.getAttrValue(attrs, 0));
      if (der == null || der.length == 0) {
        return null;
      }
      return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
    } catch (ReflectiveOperationException | CertificateException e) {
      log.debug("Sertifika DER okunamadı (handle={}): {}", handle, rootMessage(e));
      return null;
    }
  }

  /**
   * CK_ATTRIBUTE.pValue {@code byte[]} ya da (nadiren) {@code char[]} olabilir; ikisini de
   * byte[]'ya normalize ederiz.
   */
  private static byte[] asByteArray(Object pValue) {
    if (pValue == null) {
      return null;
    }
    if (pValue instanceof byte[]) {
      return (byte[]) pValue;
    }
    if (pValue instanceof char[]) {
      char[] ca = (char[]) pValue;
      byte[] ba = new byte[ca.length];
      for (int i = 0; i < ca.length; i++) {
        ba[i] = (byte) ca[i];
      }
      return ba;
    }
    return null;
  }

  private static String rootMessage(Throwable t) {
    if (t == null) {
      return "";
    }
    Throwable cause = t.getCause() != null ? t.getCause() : t;
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }
}
