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
package io.mersel.dss.agent.api.testsupport;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repo kökündeki {@code resources/test-certs/} dizininde bulunan PFX dosyalarını tipli olarak ifade
 * eden enum.
 *
 * <p>{@code mersel-dss-server-signer-java} projesinden alınan yaklaşım: şifre dosya adının son
 * {@code _} segmentinden parse edilir; enum sadece dosya adını taşır. Yeni PFX eklemek için tek
 * satırlık enum constant ve dosyayı {@code resources/test-certs/} altına koymak yeterli — kod
 * değişikliği gerekmez.
 *
 * <h3>Naming convention</h3>
 *
 * <pre>
 *   {kurum}_{algo}@{domain}_{password}.pfx
 * </pre>
 *
 * <p>Alias her PFX için sabit: {@code "1"}.
 */
public enum PfxTestKey {
  KURUM01_RSA2048("testkurum01_rsa2048@test.com.tr_614573.pfx", Algorithm.RSA_2048),
  KURUM02_EC384("testkurum02_ec384@test.com.tr_825095.pfx", Algorithm.EC_P384);

  /** Repo kökü altındaki PFX dizini (Maven user.dir testlerde repo köküdür). */
  private static final String PFX_DIR = "resources/test-certs";

  /** Tüm Kamu SM test PFX'lerinde sabit alias. */
  public static final String DEFAULT_ALIAS = "1";

  public enum Algorithm {
    RSA_2048,
    EC_P384
  }

  private final String fileName;
  private final char[] password;
  private final Algorithm algorithm;

  PfxTestKey(String fileName, Algorithm algorithm) {
    this.fileName = fileName;
    this.password = parsePassword(fileName);
    this.algorithm = algorithm;
  }

  public String getFileName() {
    return fileName;
  }

  /** Mutlak dosya yolu. */
  public File getFile() {
    return new File(PFX_DIR, fileName).getAbsoluteFile();
  }

  public String getAbsolutePath() {
    return getFile().getAbsolutePath();
  }

  /** Şifrenin kopyası (JCA bazı yollarda array'i sıfırlayabiliyor; defensive copy). */
  public char[] getPassword() {
    return password.clone();
  }

  public String getAlias() {
    return DEFAULT_ALIAS;
  }

  public Algorithm algorithm() {
    return algorithm;
  }

  public boolean isAvailable() {
    return getFile().isFile();
  }

  public String displayName() {
    return name() + " (" + fileName + ")";
  }

  public static PfxTestKey[] rsa() {
    return Arrays.stream(values())
        .filter(k -> k.algorithm == Algorithm.RSA_2048)
        .toArray(PfxTestKey[]::new);
  }

  public static PfxTestKey[] ec() {
    return Arrays.stream(values())
        .filter(k -> k.algorithm == Algorithm.EC_P384)
        .toArray(PfxTestKey[]::new);
  }

  private static char[] parsePassword(String fileName) {
    Matcher m = FilenamePattern.INSTANCE.matcher(fileName);
    if (!m.matches()) {
      throw new IllegalStateException(
          "PFX dosya adı convention'a uymuyor: " + fileName + " (beklenen: ..._{password}.pfx)");
    }
    return m.group(1).toCharArray();
  }

  /** Lazy holder: enum forward-reference NPE'sini engeller. */
  private static final class FilenamePattern {
    static final Pattern INSTANCE = Pattern.compile("^.+_([A-Za-z0-9]+)\\.pfx$");
  }
}
