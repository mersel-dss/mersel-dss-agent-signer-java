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
 */
package io.mersel.dss.agent.api.services.update;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mersel.dss.agent.api.config.SignerProperties;

/**
 * Daemon'un "güncellenmesi gerek" durumunu tutan ve yayan paylaşımlı stateful sidecar.
 *
 * <p>Üç katman bu bean'e bağımlıdır:
 *
 * <ul>
 *   <li>{@link UpdateService}: her başarılı {@code currentStatus} hesabı sonrası {@link
 *       #publish(UpdateInfo)} çağırır. Available + mandatory ise gate "açık" olur.
 *   <li>{@code MandatoryUpdateInterceptor} (web): her HTTP isteğinde {@link #isMandatoryBlocked()}
 *       sorar; açıksa imzalama endpoint'lerini HTTP 426 ile durdurur.
 *   <li>{@code DesktopUiBootstrap}: tek bir UI listener kaydeder ({@link #setListener(Consumer)});
 *       gate her değişiklikte ana pencereye {@code applyUpdateState} mesajı geçirir.
 * </ul>
 *
 * <p>State machine basit: ya boş ({@code pending == null}), ya da "güncellemeyi bekliyoruz" ({@code
 * pending != null}). Daemon JVM'i yeniden başlatıldığında doğal olarak boş ile başlar — yeni
 * sürümle ayaklanmışsa zaten {@code currentStatus} {@code upToDate} döner ve gate boş kalır.
 *
 * <p>Tüm public metotlar thread-safe.
 */
@Component
public class UpdateGate {

  private static final Logger LOG = LoggerFactory.getLogger(UpdateGate.class);

  private final SignerProperties properties;
  private final AtomicReference<UpdateInfo> pending = new AtomicReference<>();
  private final AtomicReference<Consumer<UpdateInfo>> listener = new AtomicReference<>();

  public UpdateGate(SignerProperties properties) {
    this.properties = properties;
  }

  /**
   * {@link UpdateService} her status hesabından sonra çağırır. Gate state'i bu metotla GUNCELLENİR:
   *
   * <ul>
   *   <li>{@code info == null || !info.isUpdateAvailable()} → gate temizlenir (örn. kullanıcı yeni
   *       sürümü kurmuş ve daemon'u restart etmiş, ya da en güncel zaten kuruluymuş).
   *   <li>{@code info.isUpdateAvailable() && update.mandatory} → gate "açık"; interceptor blok
   *       atar, UI kırmızı karta geçer.
   *   <li>{@code info.isUpdateAvailable() && !update.mandatory} → gate yine açılır; UI bilgi
   *       banner'ı gösterebilsin diye state set'lenir, ama {@link #isMandatoryBlocked()} false
   *       döner — REST trafiği etkilenmez.
   * </ul>
   *
   * <p>State değişikliği gerçekten oluştuysa (eski referans ≠ yeni referans) UI listener'ı
   * tetiklenir; aynı sürüm için aynı bilgiyle tekrar çağrılırsa listener gereksiz yere tetiklenmez.
   */
  public void publish(UpdateInfo info) {
    UpdateInfo next = (info != null && info.isUpdateAvailable()) ? info : null;
    UpdateInfo prev = this.pending.getAndSet(next);
    if (!sameState(prev, next)) {
      LOG.info("UpdateGate state {} → {}", describe(prev), describe(next));
      notifyListener(next);
    }
  }

  /** {@code true} → bekleyen güncelleme var ve {@code mandatory} aktif; interceptor blok atar. */
  public boolean isMandatoryBlocked() {
    return properties.getUpdate().isMandatory() && pending.get() != null;
  }

  /** Bekleyen güncelleme bilgisini döner ({@code null} → yok). */
  public UpdateInfo getPending() {
    return pending.get();
  }

  /**
   * UI listener'ı kaydeder (tek slot — yeni call eskisinin üzerine yazar; DesktopUiBootstrap
   * dışında çağıran yok). Kayıttan hemen sonra mevcut state ile listener tetiklenir; pencere geç
   * açılıyorsa bile son state ile sync'lenir.
   */
  public void setListener(Consumer<UpdateInfo> consumer) {
    this.listener.set(consumer);
    if (consumer != null) {
      try {
        consumer.accept(pending.get());
      } catch (RuntimeException re) {
        LOG.warn("UpdateGate listener initial dispatch hatası: {}", re.toString());
      }
    }
  }

  /** Test ve manuel reset için. */
  public void clear() {
    UpdateInfo prev = pending.getAndSet(null);
    if (prev != null) {
      LOG.info("UpdateGate temizlendi (eski: {}).", describe(prev));
      notifyListener(null);
    }
  }

  private void notifyListener(UpdateInfo info) {
    Consumer<UpdateInfo> c = listener.get();
    if (c == null) {
      return;
    }
    try {
      c.accept(info);
    } catch (RuntimeException re) {
      LOG.warn("UpdateGate listener dispatch hatası: {}", re.toString());
    }
  }

  private static boolean sameState(UpdateInfo a, UpdateInfo b) {
    if (a == null && b == null) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    String av = a.getLatestVersion();
    String bv = b.getLatestVersion();
    return av != null && av.equals(bv);
  }

  private static String describe(UpdateInfo info) {
    if (info == null) {
      return "clear";
    }
    return "pending(v" + info.getLatestVersion() + ")";
  }
}
