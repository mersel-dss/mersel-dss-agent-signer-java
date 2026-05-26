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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.mersel.dss.agent.api.config.SignerProperties;
import io.mersel.dss.agent.api.exceptions.UpdateCheckException;

class UpdateServiceTest {

  private SignerProperties props;
  private VersionProvider versionProvider;
  private GitHubReleaseClient releaseClient;
  private UpdateService service;

  @BeforeEach
  void setUp() {
    props = new SignerProperties();
    versionProvider = mock(VersionProvider.class);
    releaseClient = mock(GitHubReleaseClient.class);
    service = new UpdateService(props, versionProvider, releaseClient, new UpdateGate(props));
  }

  @Test
  void returnsEmptyWhenDisabled() {
    props.getUpdate().setEnabled(false);
    Optional<UpdateInfo> r = service.checkForUpdate(false);
    assertThat(r).isEmpty();
  }

  @Test
  void currentStatusReturnsDisabledShapeWhenDisabled() {
    props.getUpdate().setEnabled(false);
    UpdateInfo s = service.currentStatus(false);
    assertThat(s.getUpdateCheckDisabled()).isTrue();
    assertThat(s.isUpdateAvailable()).isFalse();
  }

  @Test
  void returnsEmptyWhenVersionsEqual() {
    when(versionProvider.currentVersion()).thenReturn("2.0.0");
    when(releaseClient.fetchLatest(anyBoolean()))
        .thenReturn(release("v2.0.0", false, "https://x/", null));
    assertThat(service.checkForUpdate(false)).isEmpty();
  }

  @Test
  void returnsUpdateInfoWhenNewerStable() {
    when(versionProvider.currentVersion()).thenReturn("2.0.0");
    when(releaseClient.fetchLatest(anyBoolean()))
        .thenReturn(release("v2.1.0", false, "https://x/v2.1.0", "https://x/v2.1.0/jar"));
    UpdateInfo info = service.checkForUpdate(false).get();
    assertThat(info.isUpdateAvailable()).isTrue();
    assertThat(info.getCurrentVersion()).isEqualTo("2.0.0");
    assertThat(info.getLatestVersion()).isEqualTo("2.1.0");
    assertThat(info.getDownloadUrl()).isEqualTo("https://x/v2.1.0/jar");
  }

  @Test
  void returnsEmptyWhenLatestIsOlder() {
    when(versionProvider.currentVersion()).thenReturn("3.0.0");
    when(releaseClient.fetchLatest(anyBoolean()))
        .thenReturn(release("v2.9.0", false, "https://x/", null));
    assertThat(service.checkForUpdate(false)).isEmpty();
  }

  @Test
  void filtersPrereleaseWhenDisallowed() {
    props.getUpdate().setPrereleaseAllowed(false);
    when(versionProvider.currentVersion()).thenReturn("2.0.0");
    when(releaseClient.fetchLatest(anyBoolean()))
        .thenReturn(release("v2.1.0-rc1", true, "https://x/", null));
    assertThat(service.checkForUpdate(false)).isEmpty();
  }

  @Test
  void allowsPrereleaseWhenEnabled() {
    props.getUpdate().setPrereleaseAllowed(true);
    when(versionProvider.currentVersion()).thenReturn("2.0.0");
    when(releaseClient.fetchLatest(anyBoolean()))
        .thenReturn(release("v2.1.0-rc1", true, "https://x/", "https://x/jar"));
    assertThat(service.checkForUpdate(false)).isPresent();
  }

  @Test
  void swallowsUpdateCheckExceptionAndReturnsEmpty() {
    when(versionProvider.currentVersion()).thenReturn("2.0.0");
    when(releaseClient.fetchLatest(anyBoolean())).thenThrow(new UpdateCheckException("HTTP 503"));
    assertThat(service.checkForUpdate(false)).isEmpty();
  }

  @Test
  void swallowsGenericRuntimeAndReturnsEmpty() {
    when(versionProvider.currentVersion()).thenReturn("2.0.0");
    when(releaseClient.fetchLatest(anyBoolean())).thenThrow(new RuntimeException("boom"));
    assertThat(service.checkForUpdate(false)).isEmpty();
  }

  @Test
  void downloadUrlFallsBackToHtmlUrlWhenAssetMissing() {
    when(versionProvider.currentVersion()).thenReturn("2.0.0");
    when(releaseClient.fetchLatest(anyBoolean()))
        .thenReturn(release("v2.1.0", false, "https://x/release/v2.1.0", null));
    UpdateInfo info = service.checkForUpdate(false).get();
    assertThat(info.getDownloadUrl()).isEqualTo("https://x/release/v2.1.0");
  }

  @Test
  void unparseableCurrentVersionReturnsEmpty() {
    when(versionProvider.currentVersion()).thenReturn("not-a-version");
    assertThat(service.checkForUpdate(false)).isEmpty();
  }

  private static LatestRelease release(
      String tag, boolean prerelease, String htmlUrl, String assetUrl) {
    return new LatestRelease(
        tag,
        tag,
        htmlUrl,
        "body",
        "2026-06-15T10:00:00Z",
        prerelease,
        assetUrl,
        assetUrl == null ? null : "mersel-dss-agent-signer-api-x.jar",
        null);
  }
}
