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

/**
 * {@link GitHubReleaseClient} tarafından döndürülen, GitHub Releases JSON yanıtının parse edilmiş
 * minimal projeksiyonu.
 *
 * <p>Immutable; alan setter'ı yoktur. ETag alanı dahil edilmiştir ki client ileride {@code
 * If-None-Match} ile cache-validation yapabilsin.
 */
public final class LatestRelease {

  private final String tagName;
  private final String name;
  private final String htmlUrl;
  private final String body;
  private final String publishedAt;
  private final boolean prerelease;
  private final String assetDownloadUrl; // pattern eşleşen veya ilk .jar (yoksa null)
  private final String assetName;
  private final String etag;

  public LatestRelease(
      String tagName,
      String name,
      String htmlUrl,
      String body,
      String publishedAt,
      boolean prerelease,
      String assetDownloadUrl,
      String assetName,
      String etag) {
    this.tagName = tagName;
    this.name = name;
    this.htmlUrl = htmlUrl;
    this.body = body;
    this.publishedAt = publishedAt;
    this.prerelease = prerelease;
    this.assetDownloadUrl = assetDownloadUrl;
    this.assetName = assetName;
    this.etag = etag;
  }

  public String getTagName() {
    return tagName;
  }

  public String getName() {
    return name;
  }

  public String getHtmlUrl() {
    return htmlUrl;
  }

  public String getBody() {
    return body;
  }

  public String getPublishedAt() {
    return publishedAt;
  }

  public boolean isPrerelease() {
    return prerelease;
  }

  public String getAssetDownloadUrl() {
    return assetDownloadUrl;
  }

  public String getAssetName() {
    return assetName;
  }

  public String getEtag() {
    return etag;
  }
}
