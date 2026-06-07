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
package io.mersel.dss.agent.api.services.virtualtoken;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Sanal kart ("Dummy Card") tanımlarının <b>işletim sistemi kullanıcı dizinindeki</b> kalıcı
 * deposu. Varsayılan konum {@code ~/.mersel-dss/virtual-cards}; agent yeniden başlatıldığında
 * tanımlar buradan yeniden yüklenir.
 *
 * <h2>Dosya düzeni</h2>
 *
 * <ul>
 *   <li>{@code virtual-cards.json} — kayıt indeksi (ad, tip, libraryPath / pfx dosya adı, şifreli
 *       parola).
 *   <li>{@code pfx/<uuid>.p12} — PKCS#12 kartların kopyalanmış ham PFX baytları.
 *   <li>{@code .secret.key} — parola şifrelemesi için makineye özel 256-bit AES anahtarı.
 * </ul>
 *
 * <h2>Güvenlik</h2>
 *
 * PFX parolaları AES-GCM ile {@code .secret.key} altında şifrelenip diske öyle yazılır; dizin ve
 * dosyalar POSIX sistemlerde yalnız sahip (owner) okuyabilecek şekilde kısıtlanır. Bu, parolayı
 * düz metin disk okumalarına ve gözle taramaya karşı korur; ancak anahtar dosyasına erişebilen bir
 * saldırgana karşı tam koruma değildir (tam koruma için OS keychain gerekirdi). Kullanıcı
 * deneyimi tercihi olarak parola saklanır ki yeniden başlatmada imza için tekrar sorulmasın.
 *
 * <p>Depo herhangi bir nedenle hazırlanamazsa ({@link #enabled} {@code false}) tüm işlemler no-op
 * olur ve registry bellek-içi moda düşer — özellik yine çalışır, sadece kalıcılık devre dışı kalır.
 */
@Component
public class VirtualTokenStore {

  private static final Logger log = LoggerFactory.getLogger(VirtualTokenStore.class);

  private static final String INDEX_FILE = "virtual-cards.json";
  private static final String KEY_FILE = ".secret.key";
  private static final String PFX_SUBDIR = "pfx";
  private static final String TYPE_PKCS11 = "PKCS11";
  private static final String TYPE_PKCS12 = "PKCS12";
  private static final int GCM_TAG_BITS = 128;
  private static final int GCM_IV_BYTES = 12;
  private static final int AES_KEY_BYTES = 32;

  private final Path baseDir;
  private final Path indexFile;
  private final Path pfxDir;
  private final ObjectMapper mapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  private final Object lock = new Object();
  private final boolean enabled;
  private SecretKey secretKey;

  /** Spring kurucu — dizin {@code mersel.signer.virtual-cards.dir} ile override edilebilir. */
  @org.springframework.beans.factory.annotation.Autowired
  public VirtualTokenStore(@Value("${mersel.signer.virtual-cards.dir:}") String configuredDir) {
    this(resolveBaseDir(configuredDir));
  }

  /** Test/iç kullanım için doğrudan dizin enjekte eden kurucu. */
  VirtualTokenStore(Path baseDir) {
    this.baseDir = baseDir;
    this.indexFile = baseDir.resolve(INDEX_FILE);
    this.pfxDir = baseDir.resolve(PFX_SUBDIR);
    boolean ok;
    try {
      Files.createDirectories(pfxDir);
      lockDownPermissions(baseDir);
      this.secretKey = loadOrCreateKey();
      ok = true;
      log.info("Sanal kart kalıcı deposu hazır: {}", baseDir);
    } catch (Exception e) {
      log.warn(
          "Sanal kart kalıcı deposu hazırlanamadı ({}); bellek-içi moda düşülüyor: {}",
          baseDir,
          e.getMessage());
      ok = false;
    }
    this.enabled = ok;
  }

  private static Path resolveBaseDir(String configuredDir) {
    if (StringUtils.isNotBlank(configuredDir)) {
      return Paths.get(configuredDir.trim());
    }
    String home = System.getProperty("user.home");
    return Paths.get(StringUtils.isBlank(home) ? "." : home, ".mersel-dss", "virtual-cards");
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** Diskten tüm kayıtları okuyup (PFX baytları + çözülmüş parola dahil) döndürür. */
  public List<Loaded> loadAll() {
    if (!enabled) {
      return Collections.emptyList();
    }
    synchronized (lock) {
      List<Entry> entries = readIndex();
      List<Loaded> out = new ArrayList<Loaded>(entries.size());
      for (Entry e : entries) {
        try {
          if (TYPE_PKCS11.equals(e.type)) {
            out.add(new Loaded(VirtualTokenType.PKCS11, e.name, e.source, e.libraryPath, null, null));
          } else if (TYPE_PKCS12.equals(e.type)) {
            byte[] bytes = Files.readAllBytes(pfxDir.resolve(e.pfxFile));
            char[] pw = decryptPassword(e.passwordEnc);
            out.add(new Loaded(VirtualTokenType.PKCS12, e.name, e.source, null, bytes, pw));
          }
        } catch (Exception ex) {
          log.warn("Kalıcı sanal kart '{}' yüklenemedi, atlanıyor: {}", e.name, ex.getMessage());
        }
      }
      return out;
    }
  }

  public void persistPkcs11(String name, String libraryPath, String source) {
    if (!enabled) {
      return;
    }
    synchronized (lock) {
      try {
        List<Entry> entries = readIndex();
        removeEntry(entries, name);
        Entry e = new Entry();
        e.name = name;
        e.type = TYPE_PKCS11;
        e.source = source;
        e.libraryPath = libraryPath;
        entries.add(e);
        writeIndex(entries);
      } catch (Exception ex) {
        log.warn("Sanal kart '{}' diske yazılamadı: {}", name, ex.getMessage());
      }
    }
  }

  public void persistPkcs12(String name, byte[] pfxBytes, char[] password, String source) {
    if (!enabled) {
      return;
    }
    synchronized (lock) {
      try {
        List<Entry> entries = readIndex();
        removeEntry(entries, name);
        String fileName = java.util.UUID.randomUUID().toString() + ".p12";
        Path target = pfxDir.resolve(fileName);
        Files.write(target, pfxBytes);
        lockDownPermissions(target);
        Entry e = new Entry();
        e.name = name;
        e.type = TYPE_PKCS12;
        e.source = source;
        e.pfxFile = fileName;
        e.passwordEnc = encryptPassword(password);
        entries.add(e);
        writeIndex(entries);
      } catch (Exception ex) {
        log.warn("Sanal kart '{}' diske yazılamadı: {}", name, ex.getMessage());
      }
    }
  }

  public void delete(String name) {
    if (!enabled) {
      return;
    }
    synchronized (lock) {
      try {
        List<Entry> entries = readIndex();
        removeEntry(entries, name);
        writeIndex(entries);
      } catch (Exception ex) {
        log.warn("Sanal kart '{}' diskten silinemedi: {}", name, ex.getMessage());
      }
    }
  }

  /* ---------------- index io ---------------- */

  private List<Entry> readIndex() {
    if (!Files.exists(indexFile)) {
      return new ArrayList<Entry>();
    }
    try {
      byte[] raw = Files.readAllBytes(indexFile);
      if (raw.length == 0) {
        return new ArrayList<Entry>();
      }
      List<Entry> list = mapper.readValue(raw, new TypeReference<List<Entry>>() {});
      return list != null ? list : new ArrayList<Entry>();
    } catch (Exception e) {
      log.warn("Sanal kart indeksi okunamadı, boş kabul ediliyor: {}", e.getMessage());
      return new ArrayList<Entry>();
    }
  }

  private void writeIndex(List<Entry> entries) throws Exception {
    Files.write(indexFile, mapper.writeValueAsBytes(entries));
    lockDownPermissions(indexFile);
  }

  /** Aynı isimli kaydı (ve PKCS#12 ise kopyalanmış PFX dosyasını) listeden+diskten kaldırır. */
  private void removeEntry(List<Entry> entries, String name) {
    Iterator<Entry> it = entries.iterator();
    while (it.hasNext()) {
      Entry e = it.next();
      if (e.name != null && e.name.equals(name)) {
        if (TYPE_PKCS12.equals(e.type) && StringUtils.isNotBlank(e.pfxFile)) {
          try {
            Files.deleteIfExists(pfxDir.resolve(e.pfxFile));
          } catch (Exception ex) {
            log.debug("Eski PFX dosyası silinemedi ({}): {}", e.pfxFile, ex.getMessage());
          }
        }
        it.remove();
      }
    }
  }

  /* ---------------- crypto ---------------- */

  private SecretKey loadOrCreateKey() throws Exception {
    Path keyPath = baseDir.resolve(KEY_FILE);
    if (Files.exists(keyPath)) {
      byte[] k = Files.readAllBytes(keyPath);
      if (k.length == AES_KEY_BYTES) {
        return new SecretKeySpec(k, "AES");
      }
      log.warn("Sanal kart anahtar dosyası beklenmeyen boyutta, yeniden üretiliyor.");
    }
    byte[] k = new byte[AES_KEY_BYTES];
    new SecureRandom().nextBytes(k);
    Files.write(keyPath, k);
    lockDownPermissions(keyPath);
    return new SecretKeySpec(k, "AES");
  }

  private String encryptPassword(char[] password) throws Exception {
    byte[] iv = new byte[GCM_IV_BYTES];
    new SecureRandom().nextBytes(iv);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
    byte[] plain = toUtf8Bytes(password);
    try {
      byte[] ct = cipher.doFinal(plain);
      byte[] all = new byte[iv.length + ct.length];
      System.arraycopy(iv, 0, all, 0, iv.length);
      System.arraycopy(ct, 0, all, iv.length, ct.length);
      return Base64.getEncoder().encodeToString(all);
    } finally {
      Arrays.fill(plain, (byte) 0);
    }
  }

  private char[] decryptPassword(String enc) throws Exception {
    if (StringUtils.isBlank(enc)) {
      return new char[0];
    }
    byte[] all = Base64.getDecoder().decode(enc);
    byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_BYTES);
    byte[] ct = Arrays.copyOfRange(all, GCM_IV_BYTES, all.length);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
    byte[] plain = cipher.doFinal(ct);
    try {
      CharBuffer cb = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain));
      char[] out = new char[cb.remaining()];
      cb.get(out);
      return out;
    } finally {
      Arrays.fill(plain, (byte) 0);
    }
  }

  private static byte[] toUtf8Bytes(char[] chars) {
    CharBuffer cb = CharBuffer.wrap(chars == null ? new char[0] : chars);
    ByteBuffer bb = StandardCharsets.UTF_8.encode(cb);
    byte[] out = new byte[bb.remaining()];
    bb.get(out);
    return out;
  }

  /** POSIX sistemlerde dizin/dosyayı yalnız sahip erişimine kısıtlar; diğer OS'larda no-op. */
  private static void lockDownPermissions(Path path) {
    try {
      if (!Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class)) {
        return;
      }
      String perms = Files.isDirectory(path) ? "rwx------" : "rw-------";
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(perms));
    } catch (Exception ignore) {
      // Best effort — izin kısıtlaması desteklenmiyorsa sessizce geç.
    }
  }

  /* ---------------- models ---------------- */

  /** JSON indeks satırı. Jackson public alanları otomatik algılar (Java 8 uyumlu POJO). */
  public static final class Entry {
    public String name;
    public String type;
    public String source;
    public String libraryPath;
    public String pfxFile;
    public String passwordEnc;
  }

  /** Registry'ye geri verilen yüklenmiş kayıt taşıyıcısı. */
  public static final class Loaded {
    private final VirtualTokenType type;
    private final String name;
    private final String source;
    private final String libraryPath;
    private final byte[] pfxBytes;
    private final char[] password;

    Loaded(
        VirtualTokenType type,
        String name,
        String source,
        String libraryPath,
        byte[] pfxBytes,
        char[] password) {
      this.type = type;
      this.name = name;
      this.source = source;
      this.libraryPath = libraryPath;
      this.pfxBytes = pfxBytes;
      this.password = password;
    }

    public VirtualTokenType getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public String getSource() {
      return source;
    }

    public String getLibraryPath() {
      return libraryPath;
    }

    public byte[] getPfxBytes() {
      return pfxBytes;
    }

    public char[] getPassword() {
      return password;
    }
  }

  /** Test yardımcı: depo dizini. */
  Path baseDirForTest() {
    return baseDir;
  }
}
