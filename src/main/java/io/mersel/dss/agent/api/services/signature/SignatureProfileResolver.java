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
package io.mersel.dss.agent.api.services.signature;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.xml.security.algorithms.MessageDigestAlgorithm;

import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.models.SignatureDiagnostics;
import io.mersel.dss.agent.api.services.keystore.Pkcs11MechanismProbe;
import io.mersel.dss.agent.api.services.keystore.Pkcs11Mechanisms;

import xades4j.UnsupportedAlgorithmException;
import xades4j.algorithms.Algorithm;
import xades4j.algorithms.CanonicalXMLWithoutComments;
import xades4j.algorithms.GenericAlgorithm;
import xades4j.providers.AlgorithmsProviderEx;

/**
 * Token'ın bildirdiği {@code CKM_*} mekanizma listesine göre xades4j {@link AlgorithmsProviderEx}
 * implementasyonu seçer.
 *
 * <p>Asıl üretim çözümü: "Unsupported parameters" patolojisinin köküne (xades4j default profili vs.
 * token kapasitesi uyumsuzluğu) doğrudan müdahale eder. {@link
 * io.mersel.dss.agent.api.services.signature.MechanismCapabilityService} ile aynı tablo + öncelik
 * mantığını paylaşır; frontend'in gördüğü öneri ile imzalama akışı arasında drift olmaz.
 *
 * <h2>Strateji</h2>
 *
 * <ol>
 *   <li>Mekanizma listesini {@link Pkcs11MechanismProbe#probe} ile oku (PIN'siz).
 *   <li>Anahtar tipine göre öncelik listesini ({@link Pkcs11Mechanisms#RSA_SIGN_MECHANISMS} veya
 *       {@link Pkcs11Mechanisms#ECDSA_SIGN_MECHANISMS}) sırayla dene.
 *   <li>İlk eşleşen mekanizmaya karşılık gelen XAdES algoritma URL'lerini {@link
 *       AlgorithmsProviderEx} olarak xades4j'ye ver.
 *   <li>Hiçbiri eşleşmezse {@code SIGNATURE_ALGORITHM_UNSUPPORTED} fırlat — frontend kullanıcıya
 *       firmware güncelleme yönlendirmesi yapsın.
 * </ol>
 *
 * <p>Bu sınıf <b>tanılama bağlamı</b> ({@link SignatureDiagnostics}) da üretir; başarısızlık
 * durumunda exception bu bağlamı taşır ve {@code GlobalExceptionHandler} {@code ErrorModel}'e
 * mirror'lar.
 */
public final class SignatureProfileResolver {

  private static final String SIG_RSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
  private static final String SIG_RSA_SHA384 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384";
  private static final String SIG_RSA_SHA512 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512";
  private static final String SIG_RSA_SHA1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1";

  private static final String SIG_ECDSA_SHA256 =
      "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256";
  private static final String SIG_ECDSA_SHA384 =
      "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384";
  private static final String SIG_ECDSA_SHA512 =
      "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512";
  private static final String SIG_ECDSA_SHA1 = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1";

  private static final String DIGEST_SHA256 = "http://www.w3.org/2001/04/xmlenc#sha256";
  private static final String DIGEST_SHA384 = "http://www.w3.org/2001/04/xmldsig-more#sha384";
  private static final String DIGEST_SHA512 = "http://www.w3.org/2001/04/xmlenc#sha512";
  private static final String DIGEST_SHA1 = "http://www.w3.org/2000/09/xmldsig#sha1";

  private SignatureProfileResolver() {
    /* static factory */
  }

  /**
   * Verilen kart için xades4j algoritma profilini hesaplar. {@link Resolution#getDiagnostics()} ile
   * tanılama bağlamı her durumda doludur (başarı / başarısızlık).
   *
   * @param libraryPath kullanılacak PKCS#11 lib (probe için)
   * @param keyAlgorithm sertifika public key algoritma adı: {@code RSA} ya da {@code EC}
   * @param baseDiagnostics önceden bilinen kart bilgileri (terminal, ATR, cardType); resolver
   *     burada kalan alanları doldurur ({@code tokenLabel}, {@code tokenMechanisms}, {@code
   *     resolvedPkcs11Mechanism}, ...)
   * @return {@link Resolution} — başarılı durumda {@link AlgorithmsProviderEx}, hata durumunda
   *     {@link Resolution#getError()} fırlatılması gereken exception
   */
  public static Resolution resolve(
      Path libraryPath, String keyAlgorithm, SignatureDiagnostics baseDiagnostics) {
    SignatureDiagnostics diag =
        baseDiagnostics != null ? baseDiagnostics : new SignatureDiagnostics();
    diag.setKeyAlgorithm(keyAlgorithm);
    if (libraryPath != null && diag.getPkcs11Library() == null) {
      diag.setPkcs11Library(libraryPath.toString());
    }

    Pkcs11MechanismProbe.ProbeResult probe;
    try {
      probe = Pkcs11MechanismProbe.probe(libraryPath);
    } catch (RuntimeException probeFailure) {
      // Probe başarısız → default profil ile devam, ama uyarı koy.
      List<String> warnings = new ArrayList<String>();
      warnings.add(
          "Mekanizma listesi okunamadı ("
              + probeFailure.getClass().getSimpleName()
              + "): "
              + probeFailure.getMessage()
              + ". Default xades4j profili ile devam edilecek.");
      diag.setWarnings(warnings);
      return Resolution.success(defaultProvider(keyAlgorithm, diag), diag);
    }

    diag.setTokenLabel(coalesce(diag.getTokenLabel(), probe.getTokenLabel()));
    diag.setTokenManufacturerId(
        coalesce(diag.getTokenManufacturerId(), probe.getTokenManufacturerId()));
    diag.setTokenModel(coalesce(diag.getTokenModel(), probe.getTokenModel()));
    diag.setTokenFirmwareVersion(
        coalesce(diag.getTokenFirmwareVersion(), probe.getTokenFirmwareVersion()));
    diag.setTokenHardwareVersion(
        coalesce(diag.getTokenHardwareVersion(), probe.getTokenHardwareVersion()));
    diag.setTokenSerialMasked(coalesce(diag.getTokenSerialMasked(), probe.getTokenSerialMasked()));
    diag.setTokenMechanisms(probe.mechanismNames());

    if (!probe.isSupported()) {
      // Mekanizma listesi reflection'la okunamadı → default profil; uyarı zaten capabilities
      // tarafında üretildi; burada minimal bir warning ekleyelim.
      List<String> warnings = new ArrayList<String>();
      warnings.add(
          "Token mekanizma listesi bu JDK ile okunamadı; xades4j default profili kullanılacak.");
      diag.setWarnings(warnings);
      return Resolution.success(defaultProvider(keyAlgorithm, diag), diag);
    }

    boolean rsa = isRsa(keyAlgorithm);
    boolean ec = isEc(keyAlgorithm);
    if (!rsa && !ec) {
      // Bilinmeyen anahtar tipi — DSA, EdDSA, vb. Default profil yine de denenir.
      List<String> warnings = new ArrayList<String>();
      warnings.add(
          "Sertifika public key tipi ("
              + keyAlgorithm
              + ") tanınmıyor;"
              + " xades4j default profili denendi. Başarısızsa kart firmware sürümünü"
              + " kontrol edin.");
      diag.setWarnings(warnings);
      return Resolution.success(defaultProvider(keyAlgorithm, diag), diag);
    }

    List<String> candidates =
        rsa ? Pkcs11Mechanisms.RSA_SIGN_MECHANISMS : Pkcs11Mechanisms.ECDSA_SIGN_MECHANISMS;
    String chosen = probe.firstAvailable(candidates);

    if (chosen == null) {
      // Karta uygun hiçbir imzalama mekanizması yok.
      diag.setRemediation(remediationForMissing(rsa, probe.mechanismNames()));
      diag.setFallbackStrategy("none-available");
      String msg =
          (rsa ? "RSA" : "ECDSA")
              + " imzalama için token üzerinde uygun PKCS#11 mekanizması bulunamadı."
              + " Bilinen liste: "
              + probe.mechanismNames();
      SignatureOperationException ex =
          (SignatureOperationException)
              new SignatureOperationException(
                      SignatureOperationException.CODE_ALGORITHM_UNSUPPORTED, msg, null)
                  .withDiagnostics(diag);
      return Resolution.failure(ex);
    }

    Choice choice = chooseAlgorithms(chosen);
    diag.setResolvedPkcs11Mechanism(chosen);
    diag.setAttemptedSignatureAlgorithm(choice.signatureUrl);
    diag.setResolvedJcaSignature(jcaName(chosen));
    diag.setFallbackStrategy(choice.fallbackStrategy);

    if (!choice.warnings.isEmpty()) {
      List<String> existing =
          diag.getWarnings() == null
              ? new ArrayList<String>()
              : new ArrayList<String>(diag.getWarnings());
      existing.addAll(choice.warnings);
      diag.setWarnings(existing);
    }

    return Resolution.success(new MerselAlgorithmsProvider(choice), diag);
  }

  /** RSA / EC dışı senaryolar için default profil. */
  private static AlgorithmsProviderEx defaultProvider(
      String keyAlgorithm, SignatureDiagnostics diag) {
    Choice c =
        isEc(keyAlgorithm)
            ? new Choice(SIG_ECDSA_SHA384, DIGEST_SHA384, null)
            : new Choice(SIG_RSA_SHA256, DIGEST_SHA256, null);
    diag.setAttemptedSignatureAlgorithm(c.signatureUrl);
    return new MerselAlgorithmsProvider(c);
  }

  /** Seçilen {@code CKM_*}'ye karşılık gelen XAdES URL'lerini ve uyarıları üretir. */
  static Choice chooseAlgorithms(String ckm) {
    switch (ckm) {
      case "CKM_SHA256_RSA_PKCS":
        return new Choice(SIG_RSA_SHA256, DIGEST_SHA256, null);
      case "CKM_SHA384_RSA_PKCS":
        return new Choice(SIG_RSA_SHA384, DIGEST_SHA384, null);
      case "CKM_SHA512_RSA_PKCS":
        return new Choice(SIG_RSA_SHA512, DIGEST_SHA512, null);
      case "CKM_SHA1_RSA_PKCS":
        {
          Choice c = new Choice(SIG_RSA_SHA1, DIGEST_SHA1, "rsa-sha1-only");
          c.warnings.add(
              "Token yalnız SHA-1 + RSA destekliyor. ETSI EN 319 132 v1 SHA-1'i deprecate ediyor;"
                  + " imza üretilebilir ancak doğrulayıcı tarafında uyumluluk sorunu yaşanabilir.");
          return c;
        }
      case "CKM_RSA_PKCS":
        {
          // Raw RSA — xades4j SHA-256 RSA çağrısı SunPKCS11 üzerinden CKM_RSA_PKCS'ye düşürür
          // (digest yazılım tarafında, sonra padding + raw RSA). Çoğu eski AKIS firmware bu
          // yolla çalışır. Profil URL'lerini SHA-256 RSA olarak tutuyoruz; SunPKCS11 doğru
          // çevirir. Fallback flag set ediyoruz ki tanılama "neden eski default'la çalışmadı"
          // bilgisini yazsın.
          Choice c = new Choice(SIG_RSA_SHA256, DIGEST_SHA256, "raw-rsa-soft-digest");
          c.warnings.add(
              "Token yalnız raw CKM_RSA_PKCS destekliyor. Digest yazılım tarafında hesaplanıp"
                  + " imza karta padding'siz gönderilecek. Bu eski AKIS / SafeSign firmware'inde"
                  + " normaldir.");
          return c;
        }
      case "CKM_ECDSA_SHA384":
        return new Choice(SIG_ECDSA_SHA384, DIGEST_SHA384, null);
      case "CKM_ECDSA_SHA256":
        return new Choice(SIG_ECDSA_SHA256, DIGEST_SHA256, null);
      case "CKM_ECDSA_SHA512":
        return new Choice(SIG_ECDSA_SHA512, DIGEST_SHA512, null);
      case "CKM_ECDSA_SHA1":
        {
          Choice c = new Choice(SIG_ECDSA_SHA1, DIGEST_SHA1, "ecdsa-sha1-only");
          c.warnings.add(
              "Token yalnız ECDSA-SHA1 destekliyor. ETSI uyumluluğu kısıtlı; mümkünse kart"
                  + " firmware'ini güncelleyin.");
          return c;
        }
      case "CKM_ECDSA":
        {
          Choice c = new Choice(SIG_ECDSA_SHA384, DIGEST_SHA384, "raw-ecdsa-soft-digest");
          c.warnings.add(
              "Token yalnız raw CKM_ECDSA destekliyor. Digest yazılım tarafında hesaplanacak.");
          return c;
        }
      default:
        // Bilinmeyen ama listeden gelmiş bir CKM — default RSA-SHA256 profile düş.
        return new Choice(SIG_RSA_SHA256, DIGEST_SHA256, null);
    }
  }

  static String jcaName(String ckm) {
    switch (ckm) {
      case "CKM_SHA256_RSA_PKCS":
        return "SHA256withRSA";
      case "CKM_SHA384_RSA_PKCS":
        return "SHA384withRSA";
      case "CKM_SHA512_RSA_PKCS":
        return "SHA512withRSA";
      case "CKM_SHA1_RSA_PKCS":
        return "SHA1withRSA";
      case "CKM_RSA_PKCS":
        return "NONEwithRSA";
      case "CKM_ECDSA_SHA384":
        return "SHA384withECDSA";
      case "CKM_ECDSA_SHA256":
        return "SHA256withECDSA";
      case "CKM_ECDSA_SHA512":
        return "SHA512withECDSA";
      case "CKM_ECDSA_SHA1":
        return "SHA1withECDSA";
      case "CKM_ECDSA":
        return "NONEwithECDSA";
      default:
        return null;
    }
  }

  private static List<String> remediationForMissing(boolean rsa, List<String> have) {
    List<String> out = new ArrayList<String>();
    if (rsa) {
      out.add(
          "Kart firmware'ini AKIS 2.2.6 veya daha yeni bir sürüme güncelletin (CKM_SHA256_RSA_PKCS"
              + " desteği için).");
      out.add(
          "PKCS#11 sürücüsünü Kamu SM'in 'üretici PKCS#11 kart yazılımları' sayfasından"
              + " indirip yeniden kurun.");
      out.add(
          "Kart yenileme zamanı geldiyse ECDSA destekli yeni nesil bir kart talep edin"
              + " (CKM_ECDSA_SHA384 mevcutsa Mersel resolver onu otomatik seçer).");
    } else {
      out.add(
          "ECDSA imzalama için kartın CKM_ECDSA veya CKM_ECDSA_SHA384 mekanizmalarından birini"
              + " desteklemesi gerekir; mevcut listede ikisi de yok.");
      out.add(
          "Kart firmware sürümünü PKCS#11 sürücü dokümantasyonu ile karşılaştırıp güncelleme"
              + " yapın.");
    }
    if (have != null && !have.isEmpty()) {
      out.add("Token'da görünen mekanizmalar: " + have);
    }
    return out;
  }

  private static boolean isRsa(String alg) {
    return alg != null && alg.toUpperCase(java.util.Locale.ROOT).contains("RSA");
  }

  private static boolean isEc(String alg) {
    if (alg == null) return false;
    String u = alg.toUpperCase(java.util.Locale.ROOT);
    return u.contains("EC") && !u.contains("RSA");
  }

  private static String coalesce(String a, String b) {
    return (a == null || a.isEmpty()) ? b : a;
  }

  /** {@link AlgorithmsProviderEx}'in iç implementasyonu; sabit URL'lere clamp eder. */
  static final class MerselAlgorithmsProvider implements AlgorithmsProviderEx {
    private final Choice choice;

    MerselAlgorithmsProvider(Choice choice) {
      this.choice = choice;
    }

    @Override
    public Algorithm getSignatureAlgorithm(String keyAlgorithmName)
        throws UnsupportedAlgorithmException {
      // Default behavior: ne istiyorsan değiştir. Bizimki sabit choice.
      return new GenericAlgorithm(choice.signatureUrl);
    }

    @Override
    public Algorithm getCanonicalizationAlgorithmForSignature() {
      return new CanonicalXMLWithoutComments();
    }

    @Override
    public Algorithm getCanonicalizationAlgorithmForTimeStampProperties() {
      return new CanonicalXMLWithoutComments();
    }

    @Override
    public String getDigestAlgorithmForDataObjsReferences() {
      return choice.digestUrl;
    }

    @Override
    public String getDigestAlgorithmForReferenceProperties() {
      return choice.digestUrl;
    }

    @Override
    public String getDigestAlgorithmForTimeStampProperties() {
      // TimeStamp varsayılanı SHA-1; biz aynı digest'i kullanmak yerine apache xml-sec sabitiyle
      // explicit SHA-1 verelim ki TSA uyumluluğu legacy davranışta kalsın. xades4j default
      // davranışı zaten SHA-1; biz B-LT/B-LTA upgrade'inde değişiriz.
      return MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1;
    }

    /** Test edilebilirlik için choice okuma. */
    Choice getChoice() {
      return choice;
    }
  }

  /** Resolver çıktısı paketi. */
  public static final class Choice {
    final String signatureUrl;
    final String digestUrl;
    final String fallbackStrategy;
    final List<String> warnings;

    Choice(String signatureUrl, String digestUrl, String fallbackStrategy) {
      this.signatureUrl = signatureUrl;
      this.digestUrl = digestUrl;
      this.fallbackStrategy = fallbackStrategy;
      this.warnings = new ArrayList<String>();
    }

    public String getSignatureUrl() {
      return signatureUrl;
    }

    public String getDigestUrl() {
      return digestUrl;
    }

    public String getFallbackStrategy() {
      return fallbackStrategy;
    }

    public List<String> getWarnings() {
      return warnings;
    }
  }

  /** Resolver dönüşü: ya başarılı bir provider ya da fırlatılması gereken exception. */
  public static final class Resolution {
    private final AlgorithmsProviderEx provider;
    private final SignatureOperationException error;
    private final SignatureDiagnostics diagnostics;

    private Resolution(
        AlgorithmsProviderEx provider,
        SignatureOperationException error,
        SignatureDiagnostics diagnostics) {
      this.provider = provider;
      this.error = error;
      this.diagnostics = diagnostics;
    }

    static Resolution success(AlgorithmsProviderEx p, SignatureDiagnostics diag) {
      return new Resolution(p, null, diag);
    }

    static Resolution failure(SignatureOperationException ex) {
      // exception içine zaten diag eklendi; ayrıca expose edelim ki test/probe yolu da görsün.
      return new Resolution(null, ex, ex.getDiagnostics());
    }

    public boolean isSuccess() {
      return provider != null;
    }

    public AlgorithmsProviderEx getProvider() {
      return provider;
    }

    public SignatureOperationException getError() {
      return error;
    }

    public SignatureDiagnostics getDiagnostics() {
      return diagnostics;
    }
  }
}
