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

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.mersel.dss.agent.api.exceptions.Pkcs11AuthException;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Session;

/**
 * Akıllı kart PIN'ini hafif bir {@code C_Login} + {@code C_Logout} ile doğrular. Frontend giriş
 * ekranında kullanıcı PIN'i girer girmez bu servisi tetikler ve yanlış PIN'i imzalama akışına
 * geçmeden tespit eder — büyük PDF'leri yüklemiş bir kullanıcının "PIN yanlış" hatası yüzünden
 * yeniden upload yapmasını engeller.
 *
 * <p>Akış:
 *
 * <ol>
 *   <li>{@link SmartCardManager#resolveLibrary(String, String, String)} 4-katmanlı strateji ile
 *       PKCS#11 lib path'ini çözer (kart tipi tespit edilemezse Layer 5 fallback exception fırlatır
 *       — controller bunu standart {@code 503 PKCS11_LIBRARY_NOT_FOUND} olarak döndürür).
 *   <li>{@link Pkcs11Session#open(Path, String)} ile public session açar; PIN doğru ise oturum
 *       login'lenir, sertifika / private key okunmaz.
 *   <li>Try-with-resources ile oturum derhal kapatılır; provider {@code Security.removeProvider}
 *       ile çıkarılır, PIN char[] sıfırlanır.
 * </ol>
 *
 * <p><b>Güvenlik notu:</b> Her başarısız doğrulama kartın PIN sayacını harcar. KamuSM kartlarında
 * tipik olarak 3 yanlış denemeden sonra PIN kilitlenir ve PUK ile reset gerekir. Frontend
 * <em>asla</em> ardışık otomatik retry yapmamalı, kullanıcıya açık uyarı göstermelidir.
 *
 * <p>Test edilebilirlik için {@link #openSession(Path, String)} package-private; bir
 * {@code @VisibleForTesting} hook'tur. Test sınıfları {@link Pkcs11Session#wrapForTest} ile
 * software keystore döndürerek gerçek PKCS#11 token'a ihtiyaç duymadan davranışı doğrulayabilir.
 */
@Service
public class SmartCardPinValidator {

  private static final Logger log = LoggerFactory.getLogger(SmartCardPinValidator.class);

  private final SmartCardManager cardManager;

  public SmartCardPinValidator(SmartCardManager cardManager) {
    this.cardManager = cardManager;
  }

  /**
   * PIN'i tek seferlik {@code C_Login}+{@code C_Logout} ile doğrular.
   *
   * @param terminalName PCSC okuyucu adı (zorunlu)
   * @param pin akıllı kart PIN'i (zorunlu)
   * @param pkcs11LibraryPath isteğe bağlı PKCS#11 lib override
   * @param cardTypeOverride isteğe bağlı kart tipi override (Layer 5 fallback round-trip)
   * @return doğrulanan oturumun resolved metadata'sı (lib path)
   * @throws IllegalArgumentException terminalName / pin boşsa
   * @throws Pkcs11AuthException PIN doğrulaması başarısız
   * @throws io.mersel.dss.agent.api.exceptions.Pkcs11LibraryException provider initialize hatası
   * @throws io.mersel.dss.agent.api.exceptions.Pkcs11LibraryNotFoundException kart tipi
   *     algılanamadı veya sürücü diskte yok
   */
  public ValidationResult validate(
      String terminalName, String pin, String pkcs11LibraryPath, String cardTypeOverride) {
    if (StringUtils.isBlank(terminalName)) {
      throw new IllegalArgumentException("'terminalName' boş olamaz.");
    }
    if (StringUtils.isBlank(pin)) {
      throw new IllegalArgumentException("'pin' boş olamaz.");
    }

    Path libraryPath =
        cardManager.resolveLibrary(terminalName, pkcs11LibraryPath, cardTypeOverride);
    log.info(
        "PIN doğrulama (C_Login): terminal={}, lib={}, cardTypeOverride={}",
        terminalName,
        libraryPath,
        cardTypeOverride);

    try (Pkcs11Session session = openSession(libraryPath, pin, terminalName)) {
      log.debug(
          "PIN doğrulama başarılı: terminal={}, provider={}",
          terminalName,
          session.getProviderName());
    }
    return new ValidationResult(libraryPath, cardTypeOverride);
  }

  /**
   * Test'in {@link Pkcs11Session#wrapForTest} ile in-memory bir keystore döndürebilmesi için ayrı
   * tutulmuş factory metod. Production'da {@link Pkcs11Session#open(Path, String, String)} çağırır;
   * {@code terminalName} birden çok gerçek kart varken doğru slot'un seçilmesini sağlar.
   */
  Pkcs11Session openSession(Path libraryPath, String pin, String terminalName) {
    return Pkcs11Session.open(libraryPath, pin, terminalName);
  }

  /** Başarılı doğrulamadan dönen küçük value tipi — controller bunu REST yanıtına çevirir. */
  public static final class ValidationResult {
    private final Path pkcs11LibraryPath;
    private final String cardType;

    public ValidationResult(Path pkcs11LibraryPath, String cardType) {
      this.pkcs11LibraryPath = pkcs11LibraryPath;
      this.cardType = cardType;
    }

    public Path getPkcs11LibraryPath() {
      return pkcs11LibraryPath;
    }

    public String getCardType() {
      return cardType;
    }
  }
}
