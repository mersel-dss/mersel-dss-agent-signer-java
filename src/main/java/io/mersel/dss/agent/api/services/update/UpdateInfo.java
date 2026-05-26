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

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /update/status} ve {@code POST /update/check} REST endpoint'lerinin döndüğü zarf.
 *
 * <p>Üç ayrı kullanım modu vardır:
 *
 * <ul>
 *   <li>{@link #disabled()} — {@code mersel.signer.update.enabled=false} ise döner; sadece {@code
 *       updateCheckDisabled=true} flag'i taşır.
 *   <li>{@link #upToDate(String)} — kontrol başarılı, mevcut sürüm en güncel.
 *   <li>{@link #available(String, String, String, String, String, String)} — yeni sürüm bulundu.
 * </ul>
 *
 * <p>{@code @JsonInclude(NON_NULL)} sayesinde yalnız anlamlı alanlar JSON'a yazılır.
 */
@Schema(description = "Güncelleme kontrolü sonucu")
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class UpdateInfo {

  @Schema(description = "Daemon'un çalışan sürümü.", example = "3.0.0")
  private final String currentVersion;

  @Schema(description = "GitHub Releases'ten okunan en son kararlı sürüm.", example = "2.1.0")
  private final String latestVersion;

  @Schema(description = "Yeni bir sürüm mevcut mu?")
  private final boolean updateAvailable;

  @Schema(description = "GitHub release tag adı (örn. v2.1.0).")
  private final String releaseTagName;

  @Schema(description = "GitHub release HTML sayfası URL'i.")
  private final String releaseUrl;

  @Schema(description = "Doğrudan indirilebilir asset (jar) URL'i; asset yoksa null.")
  private final String downloadUrl;

  @Schema(description = "Release notları (markdown).")
  private final String releaseNotes;

  @Schema(description = "Release yayınlanma zamanı (ISO-8601).", example = "2026-06-15T10:00:00Z")
  private final String publishedAt;

  @Schema(description = "Güncelleme kontrolü kapalı mı? Açıkken alan üretilmez.", example = "true")
  private final Boolean updateCheckDisabled;

  private UpdateInfo(
      String currentVersion,
      String latestVersion,
      boolean updateAvailable,
      String releaseTagName,
      String releaseUrl,
      String downloadUrl,
      String releaseNotes,
      String publishedAt,
      Boolean updateCheckDisabled) {
    this.currentVersion = currentVersion;
    this.latestVersion = latestVersion;
    this.updateAvailable = updateAvailable;
    this.releaseTagName = releaseTagName;
    this.releaseUrl = releaseUrl;
    this.downloadUrl = downloadUrl;
    this.releaseNotes = releaseNotes;
    this.publishedAt = publishedAt;
    this.updateCheckDisabled = updateCheckDisabled;
  }

  public static UpdateInfo disabled() {
    return new UpdateInfo(null, null, false, null, null, null, null, null, Boolean.TRUE);
  }

  public static UpdateInfo upToDate(String currentVersion) {
    return new UpdateInfo(
        currentVersion, currentVersion, false, null, null, null, null, null, null);
  }

  public static UpdateInfo available(
      String currentVersion,
      String latestVersion,
      String releaseTagName,
      String releaseUrl,
      String downloadUrl,
      String releaseNotes,
      String publishedAt) {
    return new UpdateInfo(
        currentVersion,
        latestVersion,
        true,
        releaseTagName,
        releaseUrl,
        downloadUrl,
        releaseNotes,
        publishedAt,
        null);
  }

  public String getCurrentVersion() {
    return currentVersion;
  }

  public String getLatestVersion() {
    return latestVersion;
  }

  public boolean isUpdateAvailable() {
    return updateAvailable;
  }

  public String getReleaseTagName() {
    return releaseTagName;
  }

  public String getReleaseUrl() {
    return releaseUrl;
  }

  public String getDownloadUrl() {
    return downloadUrl;
  }

  public String getReleaseNotes() {
    return releaseNotes;
  }

  public String getPublishedAt() {
    return publishedAt;
  }

  public Boolean getUpdateCheckDisabled() {
    return updateCheckDisabled;
  }
}
