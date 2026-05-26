# Güvenlik Politikası

Mersel DSS Agent Signer, **akıllı kart üzerinden imzalama** yapan bir
yerel uygulamadır; tehdit modelinde PIN korunması, PKCS#11 oturum güvenliği,
OCSP/CRL doğrulaması ve loopback üzerinde çalışan HTTP yüzeyi yer alır.
Güvenlik açıklarını ciddiye alıyoruz.

## Açık bildirme (Responsible disclosure)

Bir güvenlik açığı bulduysanız, **lütfen public issue açmayın**.

| Kanal | Adres |
|---|---|
| E-posta | `security@mersel.cloud` |
| GitHub Security Advisory | [`Report a vulnerability`](https://github.com/mersel-dss/mersel-dss-agent-signer-java/security/advisories/new) |

Lütfen ihtiva edin:

- Açığın tanımı + potansiyel etki
- Yeniden üretim adımları (PoC kabul edilir)
- Açığın bulunduğu sürüm (`git rev-parse HEAD` veya artifact versiyonu)
- Önerilen düzeltme (varsa)

Cevap süreleri (hedef):

- **Onay**: 3 iş günü içinde
- **Triyaj + ön analiz**: 7 iş günü içinde
- **Düzeltme sürümü**: ciddiyete göre 14–90 gün

## Desteklenen sürümler

| Sürüm | Güvenlik düzeltmesi alır mı? |
|---|---|
| 3.0.x (current) | ✅ Evet |
| 2.0.x (previous) | ❌ Hayır — 3.0'a yükseltin (BREAKING contract değişiklikleri için CHANGELOG'a bakın) |
| 1.x.x (legacy) | ❌ Hayır |

## Tehdit modeli ve hafifletmeler

Bu projenin güvenlik bütçesi, projenin **yerel uygulama** doğasına dayanır
(çift tıkla başlatılan, loopback üzerinde dinleyen bir kullanıcı uygulaması;
çoklu kullanıcılı sunucu / sysadmin-installed servis değil).
Tehdit/azaltma haritası:

### 1. PIN sızıntısı

- **Tehdit**: PKCS#11 PIN'i log'lara veya hata mesajlarına sızabilir.
- **Hafifletme**:
  - `SensitiveMaskingConverter` Logback layer'ında `pin=...`, `"pin":"..."`
    ve PAN benzeri rakam grupları otomatik maskeler (`%mskmsg` pattern).
  - `Pkcs11Session.close()` PIN char array'ini zero-out eder.
  - `Pkcs11AuthException` cevap mesajı kasten generic'tir — kart kilitleme
    saldırılarını kolaylaştırmamak için.

### 2. PKCS#11 native lib hijacking

- **Tehdit**: Yetkisiz bir saldırgan `MERSEL_AGENT_*` env var'ları veya
  `wrapperFile` parametresi ile sahte bir `.dll/.so/.dylib` yükletip
  PIN'i ve imza kuyruğunu yakalayabilir.
- **Hafifletme**:
  - `Pkcs11LibraryResolver` mutlak path olmadıkça yalnız izin verilen
    arama yollarını tarar.
  - Uygulama **loopback-only** yayın yapar; uzak bir saldırgan
    `wrapperFile` parametresine ulaşamaz.

### 3. PIN-blocking (DoS)

- **Tehdit**: Saldırgan ardışık yanlış PIN deneyerek kartı kilitler.
- **Hafifletme**: Kontrolcü yok — rate-limit reverse-proxy / istemci
  tarafından uygulanmalıdır. Servis tasarımı **PIN doğrulama hatasında
  detay vermez**.

### 4. Revocation bypass

- **Tehdit**: OCSP/CRL servisleri erişilemezse iptal edilmiş sertifika
  kullanılabilir.
- **Mevcut davranış**: AIA → OCSP başarısızsa CDP → CRL fallback;
  ikisi de cevap vermezse `UNKNOWN` statüsü döner (fail-soft).
- **Sınırlama**: Tam **PKIX path validation** ve **OCSP signature
  verification** henüz yapılmıyor (roadmap'te).

### 5. CORS / CSRF

- **Tehdit**: Bir web sayfası kullanıcının `http://localhost:15211`
  üzerinden imzalama isteği tetikleyebilir.
- **Hafifletme**: Default CORS politikası **yalnız loopback origin
  pattern'lerine** izin verir. Tarayıcı uzantısı veya ek origin
  `MERSEL_AGENT_CORS_ORIGINS` env var'ı ile açıkça whitelist edilmelidir.
  PIN her istekte istemci tarafında girilir; uygulama PIN saklamaz.

### 6. TLS yokluğu

- **Mevcut davranış**: Uygulama **HTTP** yayın yapar.
- **Gerekçe**: Localhost-only (`127.0.0.1`) için TLS gerekli değildir
  (paket loopback'i terk etmez).
- **Dış erişim**: Bu uygulamayı 127.0.0.1 dışında bir adrese bind ediyorsanız,
  önüne TLS sonlandırma yapan bir reverse proxy (caddy, nginx, traefik)
  koyun.

### 7. Üçüncü taraf bağımlılıklar

- `mvn -P security verify` ile OWASP dependency-check çalışır;
  CVSS ≥ 8 olan açıklar build'i kırar.
- Aynı profile **CycloneDX SBOM** (schema v1.6, XML + JSON) üretir
  (`target/bom.xml` / `target/bom.json`). Her GitHub Release asset'i olarak
  yayınlanır; downstream tüketici (KOBİ entegratörleri, kurumsal güvenlik
  tarayıcıları) BouncyCastle / iText / xades4j patch-önceliğini SBOM
  üzerinden hesaplayabilir.
- Suppressions: [`etc/owasp-suppressions.xml`](etc/owasp-suppressions.xml).
- Bağımlılıkların yıllık major review'ı yapılır.

### 8. Statik analiz (SAST)

- **GitHub CodeQL** (`security-and-quality` query suite) `main`'e push'ta,
  `main`'e PR'da ve haftalık schedule ile koşar
  ([`.github/workflows/codeql.yml`](.github/workflows/codeql.yml)).
- Bulgular GitHub Security tab → Code scanning alerts altında görünür;
  high severity bulgular PR review pipeline'ında blocking olur.

## Açık olmayan konular

Bu repo şunları **kapsama almaz**:

- Donanım token'ı üzerinde **firmware** açıkları (vendor'a bildirin)
- Sertifika otoritelerinin yanlış sertifika kesmesi
- İşletim sistemi seviyesinde PIN keylogger'ları
- Tarayıcı uzantınızın UI tarafı (ayrı bir scope)
