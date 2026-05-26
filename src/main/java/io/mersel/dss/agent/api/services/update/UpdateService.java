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
package io.mersel.dss.agent.api.services.update;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.exceptions.UpdateCheckException;

/**
 * Mevcut sürümü ({@link VersionProvider}) ile {@link GitHubReleaseClient}'ten dönen son release'i
 * karşılaştırır ve {@link UpdateInfo} olarak özetler.
 *
 * <p>Tüm hatalar — ağ timeout'u, JSON parse, HTTP 4xx/5xx, prerelease filter — yutulur ({@code
 * WARN} loglanır) ve {@link Optional#empty()} döner. Daemon ASLA güncelleme kontrolü yüzünden hata
 * fırlatmaz; üst katman ({@code DesktopUiBootstrap}, {@code UpdateController}) buna güvenir.
 */
@Service
public class UpdateService {

  private static final Logger LOG = LoggerFactory.getLogger(UpdateService.class);

  private final SignerProperties properties;
  private final VersionProvider versionProvider;
  private final GitHubReleaseClient releaseClient;
  private final UpdateGate gate;

  public UpdateService(
      SignerProperties properties,
      VersionProvider versionProvider,
      GitHubReleaseClient releaseClient,
      UpdateGate gate) {
    this.properties = properties;
    this.versionProvider = versionProvider;
    this.releaseClient = releaseClient;
    this.gate = gate;
  }

  /**
   * Şu anki güncelleme durumunu döner. Asla {@code null} dönmez.
   *
   * <p>Yeni sürüm bulunduğunda {@link UpdateGate#publish(UpdateInfo)} ile gate açılır; mandatory
   * mod'da bu çağrı interceptor'ı aktive eder ve ana pencereyi kırmızı "Güncelleme Gerekli" kartına
   * geçirir. {@link #checkForUpdate} {@code Optional.empty()} döndüğünde gate'e ASLA dokunulmaz —
   * "no update" ile "HTTP fetch failed" durumları aynı sinyali verdiği için yanlış reset riski
   * kabul edilemez. Gate temizliği daemon JVM'in restart'ına bırakılır (yeni sürümle ayaklanan
   * daemon zaten gate boş başlar).
   *
   * @param forceRefresh {@code true} ise ETag/304 cache bypass edilir.
   */
  public UpdateInfo currentStatus(boolean forceRefresh) {
    if (!properties.getUpdate().isEnabled()) {
      return UpdateInfo.disabled();
    }
    String currentRaw = versionProvider.currentVersion();
    Optional<SemanticVersion> current = SemanticVersion.parse(currentRaw);

    Optional<UpdateInfo> available = checkForUpdate(forceRefresh);
    if (available.isPresent()) {
      UpdateInfo info = available.get();
      gate.publish(info);
      return info;
    }
    String safe = current.isPresent() ? current.get().toString() : currentRaw;
    return UpdateInfo.upToDate(safe);
  }

  /**
   * Yalnız "yeni sürüm var" durumunda dolu {@link Optional} döner. Hata / eşit / küçük / kapalı
   * durumlarında {@link Optional#empty()}.
   */
  public Optional<UpdateInfo> checkForUpdate(boolean forceRefresh) {
    if (!properties.getUpdate().isEnabled()) {
      return Optional.empty();
    }
    String currentRaw = versionProvider.currentVersion();
    Optional<SemanticVersion> current = SemanticVersion.parse(currentRaw);
    if (!current.isPresent()) {
      LOG.warn("Mevcut sürüm parse edilemedi: {}", currentRaw);
      return Optional.empty();
    }

    LatestRelease release;
    try {
      release = releaseClient.fetchLatest(forceRefresh);
    } catch (UpdateCheckException uce) {
      LOG.warn("Güncelleme kontrolü başarısız: {}", uce.getMessage());
      return Optional.empty();
    } catch (RuntimeException re) {
      LOG.warn("Güncelleme kontrolü sırasında beklenmeyen hata: {}", re.toString());
      return Optional.empty();
    }
    if (release == null || release.getTagName() == null) {
      return Optional.empty();
    }
    if (release.isPrerelease() && !properties.getUpdate().isPrereleaseAllowed()) {
      LOG.debug(
          "En son release prerelease ({}); prereleaseAllowed=false, atlanıyor.",
          release.getTagName());
      return Optional.empty();
    }
    Optional<SemanticVersion> latest = SemanticVersion.parse(release.getTagName());
    if (!latest.isPresent()) {
      LOG.warn("Release tag parse edilemedi: {}", release.getTagName());
      return Optional.empty();
    }
    if (!latest.get().isNewerThan(current.get())) {
      return Optional.empty();
    }
    return Optional.of(
        UpdateInfo.available(
            current.get().toString(),
            latest.get().toString(),
            release.getTagName(),
            release.getHtmlUrl(),
            release.getAssetDownloadUrl() != null
                ? release.getAssetDownloadUrl()
                : release.getHtmlUrl(),
            release.getBody(),
            release.getPublishedAt()));
  }
}
