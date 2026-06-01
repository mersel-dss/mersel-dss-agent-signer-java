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
package io.mersel.dss.agent.api.services.diagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.config.SignerProperties;

/**
 * Bellekteki bounded ring buffer + listener pub/sub. {@link TraceRecordingFilter} her HTTP isteği
 * sonunda {@link #record(TraceRecord)} çağırır; Swing tanılama paneli {@link
 * #addListener(Consumer)} ile yeni kayıtları canlı dinler.
 *
 * <h2>Thread modeli</h2>
 *
 * Yazma yolu (filter) ile okuma yolları (REST endpoint, Swing panel) farklı thread'lerden gelir.
 * Buffer kendi {@code synchronized} kilidiyle korunur; {@link #snapshot()} kopyayı tek atışta
 * üretir, listener'lar buffer kilidi DIŞINDA çağrılır (panel yavaşlarsa filter'ı bloklamaz).
 *
 * <h2>Toggle</h2>
 *
 * <ul>
 *   <li>{@link #setEnabled(boolean)} runtime'da aç/kapa.
 *   <li>Kapatıldığında: filter {@link #record(TraceRecord)} çağırır ama buffer'a yazılmaz; listener
 *       tetiklenmez. Buffer boşaltılmaz; tekrar açılınca eski kayıtlar yerinde kalır.
 *   <li>{@link #clear()} buffer'ı manuel temizler ({@code POST /diagnostics/traces/clear}).
 * </ul>
 *
 * <h2>Bellek</h2>
 *
 * Default kapasite 200; her record ~2 KB JSON eşdeğeri → 400 KB üst sınır. Daemon ölçeğinde
 * önemsiz. Override için {@code mersel.signer.diagnostics.trace-recorder.capacity} property'si.
 */
@Component
public class TraceRecorder {

  private static final Logger log = LoggerFactory.getLogger(TraceRecorder.class);

  /** Default ring buffer kapasitesi; küçük tutmak hassas içerik tutarlılığı için tercih edilir. */
  public static final int DEFAULT_CAPACITY = 200;

  private final int capacity;
  private final AtomicBoolean enabled;
  private final Deque<TraceRecord> buffer;
  private final AtomicLong totalRecorded = new AtomicLong();
  private final AtomicLong totalDropped = new AtomicLong();
  private final List<Consumer<TraceRecord>> listeners =
      new CopyOnWriteArrayList<Consumer<TraceRecord>>();

  @Autowired
  public TraceRecorder(SignerProperties properties) {
    SignerProperties.Diagnostics.TraceRecorder cfg =
        properties == null ? null : properties.getDiagnostics().getTraceRecorder();
    boolean cfgEnabled = cfg == null ? true : cfg.isEnabled();
    int cfgCapacity = cfg == null ? DEFAULT_CAPACITY : cfg.getCapacity();
    this.capacity = cfgCapacity > 0 ? cfgCapacity : DEFAULT_CAPACITY;
    this.enabled = new AtomicBoolean(cfgEnabled);
    this.buffer = new ArrayDeque<TraceRecord>(this.capacity);
    log.info("TraceRecorder kuruldu: enabled={}, capacity={}", cfgEnabled, this.capacity);
  }

  /**
   * Test fixture / direct construction için backward-compat ctor. Üretimde Spring {@code
   * SignerProperties} enjekte eden ctor kullanılır.
   */
  public TraceRecorder(boolean enabled, int capacity) {
    this.capacity = capacity > 0 ? capacity : DEFAULT_CAPACITY;
    this.enabled = new AtomicBoolean(enabled);
    this.buffer = new ArrayDeque<TraceRecord>(this.capacity);
    log.info("TraceRecorder kuruldu: enabled={}, capacity={}", enabled, this.capacity);
  }

  /**
   * Yeni bir record ekler. Recorder kapalıysa hiçbir şey yapmaz. Buffer dolduysa en eski kayıt
   * düşürülür ({@code totalDropped} artar).
   */
  public void record(TraceRecord record) {
    if (record == null || !enabled.get()) {
      return;
    }
    synchronized (buffer) {
      if (buffer.size() >= capacity) {
        buffer.removeFirst();
        totalDropped.incrementAndGet();
      }
      buffer.addLast(record);
    }
    totalRecorded.incrementAndGet();
    fireListeners(record);
  }

  /** En yeni → en eski sırayla buffer'ın anlık kopyası. */
  public List<TraceRecord> snapshot() {
    List<TraceRecord> copy;
    synchronized (buffer) {
      copy = new ArrayList<TraceRecord>(buffer);
    }
    Collections.reverse(copy); // en yeni başta
    return Collections.unmodifiableList(copy);
  }

  /**
   * Filtre + sınır. {@code limit <= 0} → tüm liste (filtre uygulanmadan sadece errorOnly kısmı
   * çalışır). {@code errorOnly=true} → sadece {@link TraceRecord#isError()} kayıtları döner.
   */
  public List<TraceRecord> snapshot(int limit, boolean errorOnly) {
    List<TraceRecord> all = snapshot();
    boolean applyLimit = limit > 0;
    if (!errorOnly && !applyLimit) {
      return all;
    }
    int hint = applyLimit ? Math.min(all.size(), limit) : Math.max(all.size(), 16);
    List<TraceRecord> out = new ArrayList<TraceRecord>(hint);
    for (TraceRecord r : all) {
      if (errorOnly && !r.isError()) {
        continue;
      }
      out.add(r);
      if (applyLimit && out.size() >= limit) {
        break;
      }
    }
    return Collections.unmodifiableList(out);
  }

  /** Buffer'ı temizler; toplam sayaçlar korunur. */
  public void clear() {
    synchronized (buffer) {
      buffer.clear();
    }
    log.debug("TraceRecorder buffer temizlendi.");
  }

  /** Aç/kapa toggle — UI butonu ve REST endpoint için. */
  public boolean setEnabled(boolean enabled) {
    boolean previous = this.enabled.getAndSet(enabled);
    if (previous != enabled) {
      log.info("TraceRecorder enabled={} → {}", previous, enabled);
    }
    return previous;
  }

  public boolean isEnabled() {
    return enabled.get();
  }

  public int getCapacity() {
    return capacity;
  }

  public int currentSize() {
    synchronized (buffer) {
      return buffer.size();
    }
  }

  public long getTotalRecorded() {
    return totalRecorded.get();
  }

  public long getTotalDropped() {
    return totalDropped.get();
  }

  /** Yeni kayıt eklenince listener'ları tetikler (panel canlı update için). Filter'ı bloklamaz. */
  public void addListener(Consumer<TraceRecord> listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  public void removeListener(Consumer<TraceRecord> listener) {
    if (listener != null) {
      listeners.remove(listener);
    }
  }

  private void fireListeners(TraceRecord record) {
    for (Consumer<TraceRecord> l : listeners) {
      try {
        l.accept(record);
      } catch (RuntimeException re) {
        log.debug("TraceRecorder listener hata fırlattı: {}", re.toString());
      }
    }
  }
}
