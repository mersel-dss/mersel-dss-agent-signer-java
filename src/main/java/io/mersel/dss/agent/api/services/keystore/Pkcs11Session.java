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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AuthProvider;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mersel.dss.agent.api.exceptions.CertificateLookupException;
import io.mersel.dss.agent.api.exceptions.Pkcs11AuthException;
import io.mersel.dss.agent.api.exceptions.Pkcs11LibraryException;

/**
 * JDK 1.8 uyumlu PKCS#11 oturum sarmalayıcı.
 *
 * <p>{@code java.security.Provider.configure(String)} JDK 9+ olduğundan bu sınıf {@code
 * sun.security.pkcs11.SunPKCS11(String)} ctor'unu reflection ile çağırarak geçici bir config
 * dosyası üzerinden provider'ı ayağa kaldırır.
 *
 * <p><b>Kullanım:</b> Bu session yalnız <b>imzalama</b> akışları için kullanılır (PIN ile {@code
 * C_Login} yapar). Sertifika listeleme PKCS#11 spec'i gereği PIN istemez (CKO_CERTIFICATE objeleri
 * public okunabilir) — ancak SunPKCS11'in {@code P11KeyStore.engineLoad()} davranışı {@code
 * CKF_LOGIN_REQUIRED} bayraklı token'larda (Türkçe Kamu SM kartlarının tamamı) listeleme için bile
 * C_Login yapmaya çalışır. Bu nedenle listeleme için P11KeyStore katmanı atlanmalı ve low-level
 * {@link io.mersel.dss.agent.api.services.keystore.Pkcs11PublicCertificateReader} kullanılmalıdır.
 *
 * <p>Yaşam döngüsü:
 *
 * <pre>
 *     // İmzalama (PIN gerekir):
 *     try (Pkcs11Session s = Pkcs11Session.open(libPath, pin)) {
 *         String alias = s.resolveAlias(certificateIdOrSerial);
 *         PrivateKey key = s.getPrivateKey(alias);
 *         Certificate[] chain = s.getCertificateChain(alias);
 *         // ... iText / xades imzalama ...
 *     } // close → logout + removeProvider + temp dosya temizliği
 *
 *     // Sertifika listeleme (PIN'siz, P11KeyStore bypass):
 *     List&lt;TokenCertificate&gt; certs = Pkcs11PublicCertificateReader.read(libPath);
 * </pre>
 *
 * <p>Test edilebilirlik için {@link #wrapForTest(KeyStore, Provider, String)} fabrikası mevcuttur;
 * PKCS#11 hardware token gerektirmeden software keystore üzerinde tüm imzalama akışları çalışır.
 */
public final class Pkcs11Session implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(Pkcs11Session.class);

  private static final AtomicInteger SEQ = new AtomicInteger(0);

  private final Provider provider;
  private final KeyStore keyStore;
  private final Path configFile;
  private final String providerName;
  private final char[] pin;
  private final boolean ownsProvider;

  private Pkcs11Session(
      Provider provider,
      KeyStore keyStore,
      Path configFile,
      String providerName,
      char[] pin,
      boolean ownsProvider) {
    this.provider = provider;
    this.keyStore = keyStore;
    this.configFile = configFile;
    this.providerName = providerName;
    this.pin = pin;
    this.ownsProvider = ownsProvider;
  }

  /**
   * Gerçek PKCS#11 token'a karşı yeni bir oturum açar ve PIN ile {@code C_Login} yapar. Private key
   * erişimi gerektiren tüm akışlar (PAdES / XAdES imzalama) bu factory'i kullanır.
   *
   * <p>Sertifika listeleme için bu factory <b>kullanılmamalıdır</b> — Türkçe Kamu SM kartlarında
   * (CKF_LOGIN_REQUIRED set) PIN harcanmasına neden olur. Listeleme için {@link
   * Pkcs11PublicCertificateReader} kullanın.
   *
   * @throws Pkcs11LibraryException provider initialize edilemediğinde
   * @throws Pkcs11AuthException PIN doğrulaması başarısız olduğunda
   */
  public static Pkcs11Session open(Path libraryPath, String pin) {
    if (libraryPath == null) {
      throw new IllegalArgumentException("libraryPath null olamaz.");
    }
    String name =
        "merselSigner-"
            + SEQ.incrementAndGet()
            + "-"
            + UUID.randomUUID().toString().substring(0, 8);

    Path configFile;
    try {
      configFile = writeConfigFile(name, libraryPath.toString());
    } catch (IOException e) {
      throw new Pkcs11LibraryException("PKCS#11 config dosyası yazılamadı: " + e.getMessage(), e);
    }

    Provider provider = instantiateSunPkcs11(configFile.toString());
    Security.addProvider(provider);

    char[] pinChars = pin == null ? new char[0] : pin.toCharArray();
    KeyStore ks;
    try {
      ks = KeyStore.getInstance("PKCS11", provider);
      ks.load(null, pinChars);
    } catch (Exception loadFail) {
      Security.removeProvider(provider.getName());
      silentDelete(configFile);
      String msg =
          loadFail.getMessage() == null
              ? loadFail.getClass().getSimpleName()
              : loadFail.getMessage();
      if (msg.toLowerCase(Locale.ROOT).contains("pin")
          || msg.toLowerCase(Locale.ROOT).contains("incorrect")
          || msg.toLowerCase(Locale.ROOT).contains("auth")) {
        throw new Pkcs11AuthException("PIN doğrulaması başarısız.", loadFail);
      }
      throw new Pkcs11LibraryException("PKCS#11 keystore yüklenemedi: " + msg, loadFail);
    }
    return new Pkcs11Session(provider, ks, configFile, name, pinChars, true);
  }

  /**
   * Test akışları için: yüklü bir software KeyStore'u (örn. PKCS12 in-memory) Pkcs11Session arayüzü
   * üzerinden kullanılabilir kılar. Provider olarak sertifika oluşturma sırasında kullanılan
   * provider (örn. {@code SunRsaSign} veya {@code BC}) verilebilir.
   *
   * <p>{@link #close()} bu yolda provider'ı kaldırmaz çünkü provider'ın sahipliği test ortamına
   * aittir.
   */
  public static Pkcs11Session wrapForTest(KeyStore keyStore, Provider provider, String pin) {
    if (keyStore == null) throw new IllegalArgumentException("keyStore null olamaz.");
    if (provider == null) throw new IllegalArgumentException("provider null olamaz.");
    char[] pinChars = pin == null ? new char[0] : pin.toCharArray();
    String name = "merselSignerTest-" + SEQ.incrementAndGet();
    return new Pkcs11Session(provider, keyStore, null, name, pinChars, false);
  }

  /** JDK 1.8'in beklediği PKCS#11 config formatı (file-based; Java 9'da string-based oldu). */
  private static Path writeConfigFile(String providerName, String libraryPath) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("name = ").append(providerName).append('\n');
    sb.append("library = ").append(libraryPath).append('\n');
    sb.append("showInfo = false\n");
    Path file = Files.createTempFile("mersel-pkcs11-", ".cfg");
    Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    return file;
  }

  private static Provider instantiateSunPkcs11(String configFilePath) {
    try {
      Class<?> cls = Class.forName("sun.security.pkcs11.SunPKCS11");
      Constructor<?> ctor = cls.getConstructor(String.class);
      return (Provider) ctor.newInstance(configFilePath);
    } catch (Exception e) {
      throw new Pkcs11LibraryException(
          "SunPKCS11 provider başlatılamadı (JDK 1.8 reflection): " + e.getMessage(), e);
    }
  }

  private static void silentDelete(Path p) {
    if (p == null) return;
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignore) {
      /* noop */
    }
  }

  /* ------------------------------------------------------------------ */
  /* Accessors                                                           */
  /* ------------------------------------------------------------------ */

  public Provider getProvider() {
    return provider;
  }

  public String getProviderName() {
    return providerName;
  }

  public KeyStore getKeyStore() {
    return keyStore;
  }

  public char[] getPin() {
    return pin;
  }

  /* ------------------------------------------------------------------ */
  /* Lookup helpers                                                      */
  /* ------------------------------------------------------------------ */

  public List<TokenCertificate> listCertificates() {
    List<TokenCertificate> result = new ArrayList<TokenCertificate>();
    try {
      Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        Certificate cert = keyStore.getCertificate(alias);
        if (cert instanceof X509Certificate) {
          result.add(new TokenCertificate(alias, (X509Certificate) cert));
        }
      }
    } catch (KeyStoreException e) {
      throw new CertificateLookupException(
          "Karttaki alias'lar listelenemedi: " + e.getMessage(), e);
    }
    return result;
  }

  /**
   * {@code identifier} olarak kabul edilenler (sırayla denenir):
   *
   * <ol>
   *   <li>PKCS#11 alias adı (CKA_LABEL)
   *   <li>X.509 serial number (büyük harf hex; baştaki 0'lar normalleştirilir)
   *   <li>SHA-1 thumbprint (hex)
   * </ol>
   *
   * @throws CertificateLookupException eşleşen sertifika yoksa
   */
  public String resolveAlias(String identifier) {
    if (identifier == null || identifier.trim().isEmpty()) {
      throw new IllegalArgumentException("identifier boş olamaz.");
    }
    String trimmed = identifier.trim();

    try {
      if (keyStore.containsAlias(trimmed)) {
        return trimmed;
      }
    } catch (KeyStoreException e) {
      throw new CertificateLookupException("KeyStore.containsAlias hatası: " + e.getMessage(), e);
    }

    String normalisedHex =
        trimmed.replaceAll("\\s+", "").replaceFirst("^0x", "").toUpperCase(Locale.ROOT);
    BigInteger asBigInt;
    try {
      asBigInt = new BigInteger(normalisedHex, 16);
    } catch (NumberFormatException nfe) {
      asBigInt = null;
    }

    for (TokenCertificate tc : listCertificates()) {
      X509Certificate cert = tc.getCertificate();

      String certSerialHex = cert.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
      if (asBigInt != null && cert.getSerialNumber().equals(asBigInt)) {
        return tc.getAlias();
      }
      if (normalisedHex.equalsIgnoreCase(certSerialHex)) {
        return tc.getAlias();
      }

      String thumb = sha1Thumbprint(cert);
      if (thumb != null && thumb.equalsIgnoreCase(normalisedHex)) {
        return tc.getAlias();
      }
    }
    throw new CertificateLookupException(
        "Kartta '" + identifier + "' tanımlayıcısıyla eşleşen sertifika yok.");
  }

  private static String sha1Thumbprint(X509Certificate cert) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
      byte[] hash = md.digest(cert.getEncoded());
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
      }
      return sb.toString();
    } catch (Exception e) {
      return null;
    }
  }

  public PrivateKey getPrivateKey(String alias) {
    try {
      return (PrivateKey) keyStore.getKey(alias, pin);
    } catch (UnrecoverableKeyException e) {
      throw new Pkcs11AuthException("Private key alınamadı: PIN doğrulaması başarısız.", e);
    } catch (KeyStoreException | java.security.NoSuchAlgorithmException e) {
      throw new CertificateLookupException("Private key alınamadı: " + e.getMessage(), e);
    }
  }

  public Certificate[] getCertificateChain(String alias) {
    Certificate[] chain;
    try {
      chain = keyStore.getCertificateChain(alias);
    } catch (KeyStoreException e) {
      throw new CertificateLookupException("Sertifika zinciri alınamadı: " + e.getMessage(), e);
    }
    if (chain == null || chain.length == 0) {
      Certificate single;
      try {
        single = keyStore.getCertificate(alias);
      } catch (KeyStoreException e) {
        throw new CertificateLookupException("Sertifika alınamadı: " + e.getMessage(), e);
      }
      if (single == null) {
        throw new CertificateLookupException("Alias için sertifika yok: " + alias);
      }
      chain = new Certificate[] {single};
    }
    return chain;
  }

  @Override
  public void close() {
    if (!ownsProvider) {
      return;
    }
    if (provider instanceof AuthProvider) {
      AuthProvider ap = (AuthProvider) provider;
      try {
        ap.logout();
      } catch (LoginException e) {
        log.debug("PKCS#11 logout uyarısı: {}", e.getMessage());
      } catch (Exception e) {
        log.debug("PKCS#11 logout sırasında beklenmeyen hata", e);
      }
    }
    try {
      Security.removeProvider(provider.getName());
    } catch (Exception e) {
      log.debug("Security.removeProvider hata", e);
    }
    invokeOptionalLogout(provider);
    silentDelete(configFile);
    if (pin != null) {
      for (int i = 0; i < pin.length; i++) pin[i] = '\0';
    }
  }

  @SuppressWarnings("unused")
  private static void invokeOptionalLogout(Provider provider) {
    try {
      Method m = provider.getClass().getDeclaredMethod("logout");
      m.setAccessible(true);
      m.invoke(provider);
    } catch (NoSuchMethodException ignore) {
      /* SunPKCS11'in eski sürümlerinde yok */
    } catch (Exception e) {
      /* logout hatası yutulur */
    }
  }

  public PasswordCallback newPasswordCallback() {
    PasswordCallback cb = new PasswordCallback("PIN", false);
    cb.setPassword(pin);
    return cb;
  }
}
