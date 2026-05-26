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
    } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
      throw new Pkcs11LibraryException(
          "sun.security.pkcs11.wrapper.PKCS11 reflection setup başarısız (JDK uyumsuzluğu): "
              + e.getMessage(),
          e);
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

  private static Throwable unwrap(Throwable t) {
    if (t instanceof InvocationTargetException && t.getCause() != null) {
      return t.getCause();
    }
    return t;
  }
}
