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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /smartcard/diagnostics} JSON yanıtı. Kart algılanmadığında problem yerini hızlı tespit
 * etmek için OS, kütüphane yolu, provider adı ve her terminalin durumunu içerir.
 */
public class PcscDiagnostics {

  private final Map<String, String> environment;
  private final List<TerminalSnapshot> terminals;

  public PcscDiagnostics(Map<String, String> environment, List<TerminalSnapshot> terminals) {
    this.environment =
        environment == null
            ? Collections.<String, String>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<String, String>(environment));
    this.terminals =
        terminals == null
            ? Collections.<TerminalSnapshot>emptyList()
            : Collections.unmodifiableList(terminals);
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public List<TerminalSnapshot> getTerminals() {
    return terminals;
  }

  public int getTerminalCount() {
    return terminals.size();
  }

  public int getCardCount() {
    int n = 0;
    for (TerminalSnapshot t : terminals) {
      if (t.isCardPresent()) n++;
    }
    return n;
  }

  /** Tek bir terminal'in anlık durumu. */
  public static class TerminalSnapshot {
    private final String name;
    private final boolean cardPresent;
    private final String atr;
    private final String matchedCardType;

    public TerminalSnapshot(String name, boolean cardPresent, String atr, String matchedCardType) {
      this.name = name;
      this.cardPresent = cardPresent;
      this.atr = atr;
      this.matchedCardType = matchedCardType;
    }

    public String getName() {
      return name;
    }

    public boolean isCardPresent() {
      return cardPresent;
    }

    public String getAtr() {
      return atr;
    }

    public String getMatchedCardType() {
      return matchedCardType;
    }
  }
}
