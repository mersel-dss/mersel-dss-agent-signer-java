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
package io.mersel.dss.agent.api.services.smartcard;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sistemdeki PCSC okuyucularını listeler ve her okuyucudaki kart için ATR → {@link CardType}
 * eşleştirmesini yapar.
 *
 * <h2>Cross-platform sağlamlık</h2>
 *
 * <ul>
 *   <li>{@link PcscEnvironment} ile {@code sun.security.smartcardio.library} system property'si
 *       önceden set edilmiş olduğu için JDK doğru native binary'yi yükler.
 *   <li><b>Fresh factory</b>: Her çağrıda {@link TerminalFactory#getInstance(String, Object)} ile
 *       yeni provider instance'ı alınır. {@link TerminalFactory#getDefault()} JVM yaşam boyunca
 *       cache'lenir ve yeni takılan okuyucuları görmez.
 *   <li><b>"No readers" tolerance</b>: PCSC servisi çalışıyor ama okuyucu yoksa Sun implementasyonu
 *       {@code SCARD_E_NO_READERS_AVAILABLE} {@link CardException} atar; bu hata <em>boş liste</em>
 *       olarak yorumlanır, exception fırlatılmaz.
 *   <li><b>"Service unavailable" tolerance</b>: pcscd çalışmıyorsa veya driver kurulu değilse
 *       sessizce boş liste döner; uygulama crash etmez.
 * </ul>
 *
 * <p>Bu servis sadece kart üzerinden bilgi <em>tanıma</em> amaçlıdır; PKCS#11 oturumu açmaz. Oturum
 * {@link io.mersel.dss.agent.api.services.keystore.Pkcs11Session} ile açılır.
 */
@Service
public class SmartCardReaderService {

  private static final Logger log = LoggerFactory.getLogger(SmartCardReaderService.class);

  /**
   * Sun PCSC implementasyonunun "okuyucu yok" hatası için kullandığı mesajlar. {@link
   * CardException#getMessage()} bunlardan birini içeriyorsa boş liste döneriz.
   */
  private static final String[] NO_READERS_MARKERS = {
    "SCARD_E_NO_READERS_AVAILABLE",
    "SCARD_E_SERVICE_STOPPED",
    "SCARD_E_NO_SERVICE",
    "list() failed", // Sun JDK'nin generic wrapper mesajı bazen bu olur
  };

  /**
   * ATR cache TTL — bazı PKCS#11 sürücüleri kartı eksklusif mod'da tutar; ATR'i her seferinde
   * yeniden okumak ikinci çağrıda {@code SCARD_E_SHARING_VIOLATION} verir. 5 saniyelik kısa cache
   * pratikte tüm UI akışlarını (diagnostics → certificate listing) tek "kart bağlama" ile çözer.
   */
  private static final long ATR_CACHE_TTL_MS = 5_000L;

  private final CardTypeRegistry registry;
  private final PcscEnvironment pcscEnvironment;
  private final ConcurrentMap<String, CachedAtr> atrCache =
      new ConcurrentHashMap<String, CachedAtr>();

  private static final class CachedAtr {
    final String atrHex;
    final long expiresAt;

    CachedAtr(String atrHex, long expiresAt) {
      this.atrHex = atrHex;
      this.expiresAt = expiresAt;
    }
  }

  public SmartCardReaderService(CardTypeRegistry registry, PcscEnvironment pcscEnvironment) {
    this.registry = registry;
    this.pcscEnvironment = pcscEnvironment;
  }

  /** Sistemdeki PCSC okuyucularını listeler. Hata olursa <em>boş liste</em> döner. */
  public List<CardTerminal> listTerminals() {
    TerminalFactory factory = freshPcscFactory();
    if (factory == null) {
      return Collections.<CardTerminal>emptyList();
    }
    try {
      List<CardTerminal> terminals = factory.terminals().list();
      if (terminals == null) {
        log.debug("PCSC terminals().list() null döndü, boş liste varsayılıyor");
        return Collections.<CardTerminal>emptyList();
      }
      log.debug(
          "PCSC ({}) {} okuyucu listeledi", factory.getProvider().getName(), terminals.size());
      return terminals;
    } catch (CardException e) {
      if (isBenignNoReadersError(e)) {
        log.debug("PCSC: okuyucu yok ({})", e.getMessage());
        return Collections.<CardTerminal>emptyList();
      }
      log.warn(
          "PCSC okuyucu listelenemedi (provider={}): {}",
          factory.getProvider().getName(),
          e.getMessage());
      return Collections.<CardTerminal>emptyList();
    } catch (Throwable t) {
      log.warn(
          "PCSC altyapısı kullanılamıyor ({}, lib={}): {}",
          pcscEnvironment.getOs(),
          pcscEnvironment.getResolvedLibraryPath(),
          t.getMessage());
      return Collections.<CardTerminal>emptyList();
    }
  }

  /**
   * Her çağrıda fresh {@code PC/SC} provider instance'ı döner. {@link TerminalFactory#getDefault()}
   * uzun-yaşam cache yaptığı için sonradan takılan okuyucular default factory ile görünmez.
   *
   * @return PC/SC provider; native lib yüklenemezse {@code null}
   */
  private TerminalFactory freshPcscFactory() {
    try {
      return TerminalFactory.getInstance("PC/SC", null);
    } catch (NoSuchAlgorithmException e) {
      log.warn(
          "PC/SC provider bu JDK'da kayıtlı değil ({}): {}",
          pcscEnvironment.getOs(),
          e.getMessage());
      return null;
    } catch (Throwable t) {
      // Native lib yüklenemezse genellikle UnsatisfiedLinkError sarmalı bir
      // ProviderException atılır. Uygulama crash etmemeli.
      log.warn(
          "PC/SC provider başlatılamadı ({}, lib={}): {} — kart algılama devre dışı."
              + " Override için ENV var MERSEL_AGENT_PCSC_LIBRARY",
          pcscEnvironment.getOs(),
          pcscEnvironment.getResolvedLibraryPath(),
          t.getMessage());
      return null;
    }
  }

  private static boolean isBenignNoReadersError(CardException e) {
    String msg = e.getMessage();
    if (msg == null) return false;
    String upper = msg.toUpperCase(Locale.ROOT);
    for (String marker : NO_READERS_MARKERS) {
      if (upper.contains(marker.toUpperCase(Locale.ROOT))) return true;
    }
    Throwable cause = e.getCause();
    if (cause != null && cause.getMessage() != null) {
      String causeUpper = cause.getMessage().toUpperCase(Locale.ROOT);
      for (String marker : NO_READERS_MARKERS) {
        if (causeUpper.contains(marker.toUpperCase(Locale.ROOT))) return true;
      }
    }
    return false;
  }

  public List<SmartCardInfo> listCardsWithMeta() {
    List<SmartCardInfo> result = new ArrayList<SmartCardInfo>();
    for (CardTerminal terminal : listTerminals()) {
      String terminalName = safeName(terminal);
      try {
        if (!terminal.isCardPresent()) {
          log.debug("Okuyucu '{}' boş", terminalName);
          atrCache.remove(terminalName);
          continue;
        }
        String atrHex = readAtrCachedOrFresh(terminal, terminalName);
        if (atrHex == null) continue;
        CardType type = registry.findByAtr(atrHex).orElse(null);
        log.debug(
            "Okuyucu '{}' kart bulundu ATR={} type={}",
            terminalName,
            atrHex,
            type != null ? type.getName() : "TANINMADI");
        result.add(new SmartCardInfo(terminalName, atrHex, type));
      } catch (CardException e) {
        log.debug("Okuyucu '{}' üzerindeki kart okunamadı: {}", terminalName, e.getMessage());
      }
    }
    return result;
  }

  public SmartCardInfo findByTerminalName(String terminalName) {
    if (terminalName == null) return null;
    for (SmartCardInfo info : listCardsWithMeta()) {
      if (terminalName.equalsIgnoreCase(info.getTerminalName())) {
        return info;
      }
    }
    return null;
  }

  /**
   * ATR'i cache'ten dönder; cache miss veya TTL aştıysa kartı bağlayıp ATR'i okur ve cache'e
   * yerleştirir.
   *
   * <p>Bazı sürücüler kartı eksklusif moda alır; aynı kartı kısa aralıklarla iki kez bağlamak
   * {@code SCARD_E_SHARING_VIOLATION} ile başarısız olur. Bu cache, "diagnostics"in ATR okuduğu
   * anda kullanıcı "certificate" çağrısını yaparsa ATR'i cache'ten okur ve PKCS#11 oturumunun kart
   * üzerinde tek hâkim olmasını sağlar.
   */
  private String readAtrCachedOrFresh(CardTerminal terminal, String terminalName)
      throws CardException {
    long now = System.currentTimeMillis();
    CachedAtr cached = atrCache.get(terminalName);
    if (cached != null && cached.expiresAt > now) {
      log.debug("ATR cache hit '{}': {}", terminalName, cached.atrHex);
      return cached.atrHex;
    }
    Card card;
    try {
      card = terminal.connect("*");
    } catch (CardException e) {
      // Eksklusif mode'a takıldıysak ve elimizde TTL aşmış cache varsa, son
      // bilineni döndürelim (kullanıcı için en yakın doğru cevap).
      if (cached != null) {
        log.debug(
            "ATR connect başarısız ({}); önceki ATR cache değeri kullanılıyor: {}",
            e.getMessage(),
            cached.atrHex);
        return cached.atrHex;
      }
      throw e;
    }
    try {
      byte[] atrBytes = card.getATR().getBytes();
      String atrHex = bytesToHex(atrBytes);
      atrCache.put(terminalName, new CachedAtr(atrHex, now + ATR_CACHE_TTL_MS));
      return atrHex;
    } finally {
      try {
        card.disconnect(false);
      } catch (CardException ignore) {
        /* noop */
      }
    }
  }

  /**
   * ATR cache'i temizler. PKCS#11 oturumu sonrası kart durumu değişmiş olabilir; bir sonraki ATR
   * okuması kartı tekrar bağlasın diye {@link
   * io.mersel.dss.agent.api.services.certificate.CertificateListingService} oturumu kapattıktan
   * sonra çağırabilir. (Şimdilik public API; otomatik invalidation'ı ileride ekleyebiliriz.)
   */
  public void invalidateAtrCache() {
    atrCache.clear();
  }

  /**
   * Diagnostic çıktı: PCSC ortam bilgisi + bulunan terminal/ATR'lar. {@code GET
   * /smartcard/diagnostics} endpoint'i tarafından kullanılır.
   */
  public PcscDiagnostics diagnose() {
    java.util.LinkedHashMap<String, String> env =
        new java.util.LinkedHashMap<String, String>(pcscEnvironment.snapshot());
    TerminalFactory factory = freshPcscFactory();
    env.put("pcscProvider", factory != null ? factory.getProvider().getName() : "(yüklenemedi)");

    List<PcscDiagnostics.TerminalSnapshot> snapshots =
        new ArrayList<PcscDiagnostics.TerminalSnapshot>();
    if (factory != null) {
      try {
        List<CardTerminal> terms = factory.terminals().list();
        if (terms != null) {
          for (CardTerminal t : terms) {
            String name = safeName(t);
            boolean present;
            try {
              present = t.isCardPresent();
            } catch (CardException e) {
              present = false;
            }
            String atrHex = null;
            String matchedType = null;
            if (present) {
              try {
                atrHex = readAtrCachedOrFresh(t, name);
                if (atrHex != null) {
                  matchedType = registry.findByAtr(atrHex).map(CardType::getName).orElse(null);
                }
              } catch (CardException e) {
                log.debug("Diagnostic: '{}' ATR okunamadı ({})", name, e.getMessage());
              }
            } else {
              atrCache.remove(name);
            }
            snapshots.add(new PcscDiagnostics.TerminalSnapshot(name, present, atrHex, matchedType));
          }
        }
      } catch (CardException e) {
        if (!isBenignNoReadersError(e)) {
          env.put("listError", e.getMessage());
        }
      } catch (Throwable t) {
        env.put("listError", t.getClass().getSimpleName() + ": " + t.getMessage());
      }
    }
    return new PcscDiagnostics(env, snapshots);
  }

  private static String safeName(CardTerminal terminal) {
    try {
      return terminal.getName();
    } catch (RuntimeException e) {
      return "(isimsiz terminal)";
    }
  }

  private static String bytesToHex(byte[] bytes) {
    if (bytes == null) return "";
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
    }
    return sb.toString();
  }
}
