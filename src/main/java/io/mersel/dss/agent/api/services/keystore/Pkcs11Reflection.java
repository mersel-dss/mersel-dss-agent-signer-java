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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryException;

/**
 * {@code sun.security.pkcs11.wrapper.PKCS11} low-level JNI sarmalayıcısına reflection üzerinden
 * erişim. SunPKCS11 provider'ın {@code P11KeyStore} katmanı {@code CKF_LOGIN_REQUIRED} bayraklı
 * token'larda her keystore op için C_Login zorlar (Türkçe Kamu SM kartlarının tamamı bu bayrağı
 * taşır); bu yüzden public sertifikaları PIN'siz listelemek için P11KeyStore'u atlayıp doğrudan
 * PKCS#11 spec çağrılarına inmemiz gerekir.
 *
 * <p>Reflection tercih nedenleri:
 *
 * <ul>
 *   <li>{@link io.mersel.dss.agent.api.services.keystore.Pkcs11Session#instantiateSunPkcs11} ile
 *       tutarlı: JDK 8 / 9+ arasında portable kalır (compile-time {@code sun.*} bağımlılığı yok).
 *   <li>Listeleme uçu sık çağrılmaz; reflection overhead (cert başına ~3-4 invoke) ihmal
 *       edilebilir.
 * </ul>
 *
 * <p>Thread-safe singleton: {@link #load()} double-checked locking ile bir defa initialize eder.
 */
final class Pkcs11Reflection {

  private static final Logger log = LoggerFactory.getLogger(Pkcs11Reflection.class);

  private final Class<?> pkcs11Cls;
  private final Class<?> attrCls;

  private final Method
      getInstanceM; // PKCS11.getInstance(String, String, CK_C_INITIALIZE_ARGS, boolean)
  private final Method getSlotListM; // long[] C_GetSlotList(boolean)
  private final Method openSessionM; // long C_OpenSession(long, long, Object, CK_NOTIFY)
  private final Method closeSessionM; // void C_CloseSession(long)
  private final Method findObjectsInitM; // void C_FindObjectsInit(long, CK_ATTRIBUTE[])
  private final Method findObjectsM; // long[] C_FindObjects(long, long)
  private final Method findObjectsFinalM; // void C_FindObjectsFinal(long)
  private final Method getAttributeValueM; // void C_GetAttributeValue(long, long, CK_ATTRIBUTE[])

  /** long[] C_GetMechanismList(long slotID) — bazı JDK'larda mevcut, bazılarında yok. */
  private final Method getMechanismListM;

  /** CK_MECHANISM_INFO C_GetMechanismInfo(long slotID, long type) — opsiyonel. */
  private final Method getMechanismInfoM;

  /** CK_TOKEN_INFO C_GetTokenInfo(long slotID) — opsiyonel. */
  private final Method getTokenInfoM;

  private final Constructor<?> attrCtorTypeValue; // CK_ATTRIBUTE(long type, Object pValue)
  private final Field attrPValue; // public Object pValue;

  private Pkcs11Reflection() {
    try {
      pkcs11Cls = Class.forName("sun.security.pkcs11.wrapper.PKCS11");
      attrCls = Class.forName("sun.security.pkcs11.wrapper.CK_ATTRIBUTE");
      Class<?> initArgsCls = Class.forName("sun.security.pkcs11.wrapper.CK_C_INITIALIZE_ARGS");
      Class<?> notifyCls = Class.forName("sun.security.pkcs11.wrapper.CK_NOTIFY");
      Class<?> attrArrayCls = Array.newInstance(attrCls, 0).getClass();

      getInstanceM =
          pkcs11Cls.getMethod(
              "getInstance", String.class, String.class, initArgsCls, boolean.class);
      getSlotListM = pkcs11Cls.getMethod("C_GetSlotList", boolean.class);
      openSessionM =
          pkcs11Cls.getMethod("C_OpenSession", long.class, long.class, Object.class, notifyCls);
      closeSessionM = pkcs11Cls.getMethod("C_CloseSession", long.class);
      findObjectsInitM = pkcs11Cls.getMethod("C_FindObjectsInit", long.class, attrArrayCls);
      findObjectsM = pkcs11Cls.getMethod("C_FindObjects", long.class, long.class);
      findObjectsFinalM = pkcs11Cls.getMethod("C_FindObjectsFinal", long.class);
      getAttributeValueM =
          pkcs11Cls.getMethod("C_GetAttributeValue", long.class, long.class, attrArrayCls);

      attrCtorTypeValue = attrCls.getConstructor(long.class, Object.class);
      attrPValue = attrCls.getField("pValue");

      // Aşağıdakiler "best effort" — bazı JDK 1.8 patch'lerinde bu metotlar private veya
      // farklı imzalı olabilir; lookup başarısızsa null'a düşeriz, tanılama API'si "yok" der.
      getMechanismListM = findMethodSilent(pkcs11Cls, "C_GetMechanismList", long.class);
      getMechanismInfoM = findMethodSilent(pkcs11Cls, "C_GetMechanismInfo", long.class, long.class);
      getTokenInfoM = findMethodSilent(pkcs11Cls, "C_GetTokenInfo", long.class);
    } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
      throw new Pkcs11LibraryException(
          "sun.security.pkcs11.wrapper.PKCS11 reflection setup başarısız (JDK uyumsuzluğu): "
              + e.getMessage(),
          e);
    }
  }

  private static Method findMethodSilent(Class<?> cls, String name, Class<?>... params) {
    try {
      Method m = cls.getMethod(name, params);
      m.setAccessible(true);
      return m;
    } catch (NoSuchMethodException notPublic) {
      try {
        Method m = cls.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
      } catch (NoSuchMethodException notDeclared) {
        log.debug(
            "PKCS11 wrapper'ında {}({}) metodu bulunamadı — tanılama eksik dolacak.",
            name,
            params.length);
        return null;
      }
    } catch (RuntimeException re) {
      // setAccessible reddi (illegal-access) olabilir; sessizce null'a düş.
      log.debug("PKCS11 wrapper {} method erişimi reddedildi: {}", name, re.getMessage());
      return null;
    }
  }

  private static volatile Pkcs11Reflection INSTANCE;

  static Pkcs11Reflection load() {
    Pkcs11Reflection ref = INSTANCE;
    if (ref == null) {
      synchronized (Pkcs11Reflection.class) {
        ref = INSTANCE;
        if (ref == null) {
          ref = new Pkcs11Reflection();
          INSTANCE = ref;
        }
      }
    }
    return ref;
  }

  /**
   * {@code PKCS11.getInstance(libraryPath, "C_GetFunctionList", null, false)} çağrısı. SunPKCS11
   * wrapper aynı kütüphane path'i için instance cache'ler — birden fazla çağrı aynı instance'ı
   * döner; dolayısıyla SunPKCS11 provider'ın aynı kütüphane üzerindeki paralel C_Initialize
   * çağrılarıyla çakışma riski yoktur.
   */
  Object getInstance(String libraryPath) {
    try {
      return getInstanceM.invoke(null, libraryPath, "C_GetFunctionList", null, Boolean.FALSE);
    } catch (IllegalAccessException | InvocationTargetException e) {
      Throwable cause = unwrap(e);
      throw new Pkcs11LibraryException(
          "PKCS#11 kütüphanesi initialize edilemedi (" + libraryPath + "): " + cause.getMessage(),
          cause);
    }
  }

  long[] getSlotList(Object p11, boolean tokenPresent) throws ReflectiveOperationException {
    return (long[]) getSlotListM.invoke(p11, tokenPresent);
  }

  long openSession(Object p11, long slotID, long flags) throws ReflectiveOperationException {
    Object res = openSessionM.invoke(p11, slotID, flags, null, null);
    return ((Number) res).longValue();
  }

  void closeSession(Object p11, long session) throws ReflectiveOperationException {
    closeSessionM.invoke(p11, session);
  }

  /** Boyut {@code len} olan tipli {@code CK_ATTRIBUTE[]} array oluşturur. */
  Object newAttrArray(int len) {
    return Array.newInstance(attrCls, len);
  }

  /** {@code array[i] = new CK_ATTRIBUTE(type, value)}. */
  void setAttr(Object array, int i, long type, Object value) {
    try {
      Object attr = attrCtorTypeValue.newInstance(type, value);
      Array.set(array, i, attr);
    } catch (ReflectiveOperationException e) {
      throw new Pkcs11LibraryException("CK_ATTRIBUTE oluşturulamadı: " + e.getMessage(), e);
    }
  }

  /** {@code array[i].pValue} okur — {@code C_GetAttributeValue} sonrası dolan değer. */
  Object getAttrValue(Object array, int i) {
    try {
      Object attr = Array.get(array, i);
      return attr == null ? null : attrPValue.get(attr);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  void findObjectsInit(Object p11, long session, Object attrArray)
      throws ReflectiveOperationException {
    findObjectsInitM.invoke(p11, session, attrArray);
  }

  long[] findObjects(Object p11, long session, long maxCount) throws ReflectiveOperationException {
    return (long[]) findObjectsM.invoke(p11, session, maxCount);
  }

  void findObjectsFinal(Object p11, long session) throws ReflectiveOperationException {
    findObjectsFinalM.invoke(p11, session);
  }

  void getAttributeValue(Object p11, long session, long objectHandle, Object attrArray)
      throws ReflectiveOperationException {
    getAttributeValueM.invoke(p11, session, objectHandle, attrArray);
  }

  /* ------------------- mechanism / token info (opsiyonel) ------------------ */

  /** {@code true} → C_GetMechanismList reflection olarak çağrılabilir bu JDK'da. */
  boolean supportsMechanismList() {
    return getMechanismListM != null;
  }

  /**
   * {@code C_GetMechanismList(slotID)} → token'ın desteklediği {@code CKM_*} numaraları. Reflection
   * desteklenmiyorsa {@code null} döner.
   */
  long[] getMechanismList(Object p11, long slotID) throws ReflectiveOperationException {
    if (getMechanismListM == null) {
      return null;
    }
    Object res = getMechanismListM.invoke(p11, slotID);
    if (res instanceof long[]) {
      return (long[]) res;
    }
    return null;
  }

  /**
   * {@code C_GetMechanismInfo(slotID, mechanism)} → {@code CK_MECHANISM_INFO}. Bilinen alanlar
   * {@code ulMinKeySize}, {@code ulMaxKeySize}, {@code flags}.
   */
  MechanismInfo getMechanismInfo(Object p11, long slotID, long mechanism)
      throws ReflectiveOperationException {
    if (getMechanismInfoM == null) {
      return null;
    }
    Object info = getMechanismInfoM.invoke(p11, slotID, mechanism);
    if (info == null) {
      return null;
    }
    long min = readLongFieldSilent(info, "ulMinKeySize");
    long max = readLongFieldSilent(info, "ulMaxKeySize");
    long flags = readLongFieldSilent(info, "flags");
    return new MechanismInfo(min, max, flags);
  }

  /** {@code C_GetTokenInfo(slotID)} dönen yapıdan bilinen alanları okur. */
  TokenInfo getTokenInfo(Object p11, long slotID) throws ReflectiveOperationException {
    if (getTokenInfoM == null) {
      return null;
    }
    Object info = getTokenInfoM.invoke(p11, slotID);
    if (info == null) {
      return null;
    }
    String label = readPaddedString(info, "label");
    String manufacturerId = readPaddedString(info, "manufacturerID");
    String model = readPaddedString(info, "model");
    String serial = readPaddedString(info, "serialNumber");
    String firmware = readVersionField(info, "firmwareVersion");
    String hardware = readVersionField(info, "hardwareVersion");
    return new TokenInfo(label, manufacturerId, model, serial, firmware, hardware);
  }

  private static long readLongFieldSilent(Object owner, String field) {
    try {
      Field f = owner.getClass().getField(field);
      Object v = f.get(owner);
      if (v instanceof Number) {
        return ((Number) v).longValue();
      }
    } catch (ReflectiveOperationException e) {
      // alan yok / erişilemiyor — diagnostic best-effort, sessiz geç.
    }
    return -1L;
  }

  private static String readPaddedString(Object owner, String field) {
    try {
      Field f = owner.getClass().getField(field);
      Object v = f.get(owner);
      if (v instanceof char[]) {
        return new String((char[]) v).trim();
      }
      if (v instanceof byte[]) {
        return new String((byte[]) v, java.nio.charset.StandardCharsets.UTF_8).trim();
      }
      if (v != null) {
        return v.toString().trim();
      }
    } catch (ReflectiveOperationException e) {
      /* sessiz geç */
    }
    return null;
  }

  private static String readVersionField(Object owner, String field) {
    try {
      Field f = owner.getClass().getField(field);
      Object v = f.get(owner);
      if (v == null) {
        return null;
      }
      // CK_VERSION { byte major; byte minor; }
      try {
        Field major = v.getClass().getField("major");
        Field minor = v.getClass().getField("minor");
        int maj = ((Number) major.get(v)).intValue() & 0xFF;
        int min = ((Number) minor.get(v)).intValue() & 0xFF;
        return maj + "." + min;
      } catch (ReflectiveOperationException ver) {
        return v.toString().trim();
      }
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  /** {@link #getMechanismInfo} dönüş tipi. */
  static final class MechanismInfo {
    final long minKeySize;
    final long maxKeySize;
    final long flags;

    MechanismInfo(long minKeySize, long maxKeySize, long flags) {
      this.minKeySize = minKeySize;
      this.maxKeySize = maxKeySize;
      this.flags = flags;
    }
  }

  /** {@link #getTokenInfo} dönüş tipi. */
  static final class TokenInfo {
    final String label;
    final String manufacturerId;
    final String model;
    final String serial;
    final String firmwareVersion;
    final String hardwareVersion;

    TokenInfo(
        String label,
        String manufacturerId,
        String model,
        String serial,
        String firmwareVersion,
        String hardwareVersion) {
      this.label = label;
      this.manufacturerId = manufacturerId;
      this.model = model;
      this.serial = serial;
      this.firmwareVersion = firmwareVersion;
      this.hardwareVersion = hardwareVersion;
    }
  }

  private static Throwable unwrap(Throwable t) {
    if (t instanceof InvocationTargetException && t.getCause() != null) {
      return t.getCause();
    }
    return t;
  }
}
