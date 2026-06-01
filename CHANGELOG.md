# Changelog

Bu dosyanın formatı [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
standardına dayanır; sürüm numaralandırması
[Semantic Versioning](https://semver.org/) kurallarına uyar.

## [Unreleased]

## [1.0.6] — 2026-06-01

## [1.0.5] — 2026-06-01

### Fixed

- **CORS — Varsayılan politika "her origin'e açık" olarak değiştirildi
  (regression + sektör beklentisi düzeltmesi)**: Eski default sadece loopback
  origin'lerini (`http(s)://localhost:*`, `http(s)://127.0.0.1:*`) kabul
  ediyordu; farklı domain'lerden (örn. `https://imzaci.example.com.tr` gibi
  müşteri portalleri) yapılan herhangi bir cross-origin isteğe Spring CORS
  işleyicisi `403 "Invalid CORS request"` döndürüyordu — yanıtın gövdesi de
  boş olduğundan kullanıcılar 403'ü CORS ile ilişkilendirmekte zorlanıyordu.
  Çözüm: yeni varsayılan `allowedOriginPatterns = "*"` (tüm origin'ler) +
  `allowCredentials = true`. Hem `WebConfig.DEFAULT_OPEN_PATTERNS` hem
  `application.yml` içindeki `cors-allowed-origins: ${MERSEL_AGENT_CORS_ORIGINS:*}`
  default'u eş zamanlı güncellendi — yaml ENV var verilmediği zaman boş değil
  `*` enjekte ettiği için Java katmanındaki "blank → açık" fallback'i
  ıskalıyordu; bu nedenle ilk patch yetmemişti, yaml default'u da düzeltildi.
  Spring 5.3+ pattern bazlı wildcard'la credential kombinasyonunu destekler —
  gelen `Origin` header'ı response'a birebir yansıtılır (literal `*` değil),
  bu sayede browser cookie / `Authorization` gönderen ileri akışlar da bozulmaz. Davranış sektörün masaüstü imzalayıcı
  standardıyla uyumlu (ön muhasebe / e-fatura / bordro entegrasyonları farklı
  domain'lerden bağlanır). Kurumsal sıkılaştırma yolu aynen korundu:
  `mersel.signer.cors-allowed-origins` property'sine virgülle ayrılmış
  pattern listesi (örn. `https://*.musteri.com,https://imza.kurum.gov.tr`)
  verilirse **sadece** o pattern'lar kabul edilir, diğerleri reddedilir.
  **Güvenlik kapsamı**: hassas işlemler (`POST /pades/sign`,
  `POST /xades/sign`, `POST /smartcard/pin/validate`) kullanıcının PIN'ini
  gerektirir ve PIN her istekte client'tan gelir; kötü niyetli site PIN'i
  bilemez. `GET /smartcard/certificate` PIN'siz çalışır ve TC kimlik /
  VKN / ad-soyad içerir — açık CORS bunları cross-origin okutabilir.
  Bu vektörü tamamen kapatmak için ileride **Host header allowlist filter**
  (DNS rebinding mitigation; `Host` header'ı `localhost` / `127.0.0.1` /
  `[::1]` değilse 403) eklenebilir; planlanan ek hardening adımı.

## [1.0.4] — 2026-06-01

### Added

- **`GET /smartcard/certificate` — `CertificateResponse.valid` alanı
  eklendi (REST wire contract genişlemesi, additive)**: Yanıt modeline
  her sertifika için `valid: boolean` çıktısı eklendi. Tanım: yalnız
  **zamansal geçerlilik penceresi** kontrolü (`notBefore <= now <=
  notAfter`); network'siz, deterministik. OCSP/CRL revocation
  durumundan **bağımsızdır** — KamuSM kartlarında token'a yazılı
  zincirde issuer cert eksik kaldığında `RevocationChecker` `UNKNOWN`
  döndürür ve bu, tamamen geçerli bir sertifikanın UI'da yanlışlıkla
  "geçersiz" görünmesine yol açıyordu; yeni alan revocation'a
  bakmadığı için bu sorunu çözer. Süresi dolmuş veya henüz geçerli
  olmayan sertifika `false` döner; aksi `true`. Revocation görünümü
  hâlâ `status` enum'unda (ACTIVE / EXPIRED / REVOKED / UNKNOWN);
  imzaya uygunluk için `eligibleForSignature` (validity + purpose
  composite) bakın. Frontend kart seçim ekranında "süresi dolmuş"
  sertifikaları disable etmek için bu alanı tek başına kullanabilir.
  Geriye uyumluluk: yeni alan eklendi, hiçbir alan adı/semantiği
  değişmedi.
- **`POST /smartcard/pin/validate` — PIN'i imzalamadan önce doğrulayan
  yeni uç (REST wire contract genişlemesi)**: Frontend giriş ekranı
  kullanıcının PIN'ini büyük dosya yüklemeden ve imzalama akışına
  geçmeden önce ucuz bir `C_Login` + `C_Logout` ile doğrulayabilir.
  Sözleşme: JSON body `{ terminalName, pin, pkcs11LibraryPath?,
  cardType? }`; başarılıysa `200 + { valid: true, terminalName,
  cardType, pkcs11LibraryPath }`, PIN yanlışsa standart
  `401 PKCS11_AUTH_FAILED` (`ErrorModel`), kart sürücüsü yoksa
  `503 PKCS11_LIBRARY_NOT_FOUND` döner — yani aynı hata sözleşmesini
  diğer imzalama uçlarıyla paylaşır. Yeni servis
  `services.smartcard.SmartCardPinValidator` `SmartCardManager` 4-katmanlı
  kart-tipi çözümünü tekrar kullanır; oturum sertifika / private key
  okumadan derhal kapatılır, PIN char[] sıfırlanır. **Güvenlik notu**:
  Her başarısız doğrulama kartın PIN sayacını harcar (KamuSM kartlarında
  tipik 3 deneme sonrası kilitlenir, PUK reset gerektirir) — frontend
  ASLA otomatik retry yapmamalı, kullanıcıya açık uyarı göstermelidir.

### Changed

- **`GET /smartcard/certificate` — `id` semantiği X.509 serial'e geçti, yeni
  `label` alanı eklendi (BREAKING — REST wire contract)**: Yanıt
  modelindeki (`CertificateResponse`) `id` alanı artık sertifikanın **X.509
  serial number'ı** (hex, alt çizgi/boşluk yok) ile dolar — daha önce
  PKCS#11 alias'ı (CKA_LABEL) dönüyordu. Eski alias değeri yeni eklenen
  `label` alanında dönmeye devam eder. Aynı serial değeri, geriye uyumluluk
  için var olan `x509SerialNumber` alanında da görünür (frontend bu alana
  binding yapan kod bozulmaz). Frontend rehberi: dropdown / liste
  görünümünde kullanıcıya `label` (insan-okur etiket) gösterin, `id` /
  `x509SerialNumber` (kanonik kimlik) ise imzalama uçlarına
  `certificateId` olarak gönderilmek üzere saklanmalıdır. Eşzamanlı olarak
  `POST /pades/sign`, `POST /xades/sign`, `POST /gibApplication` uçlarının
  `certificateId` alan açıklamaları yeni semantiğe (önce serial, geriye
  uyumluluk için alias) göre güncellendi. `POST /gibApplication`'da
  sertifika eşleştirmesi artık `id` (serial) **veya** `label` (alias)
  üzerinden case-insensitive yapılır — yani serial'e geçmemiş frontend'ler
  `label` değerini gönderdiğinde yine eşleşir; `/pades/sign` ve
  `/xades/sign` zaten `Pkcs11Session.resolveAlias` üzerinden alias /
  serial / `0x...` öneki / büyük-küçük harf farkını tolere ediyordu.
  Migration: frontend `cert.id` referansını `cert.label`'a (gösterim için)
  ve `cert.id` (yeni serial değeri) `certificateId` payload'ına (imzalama
  için) ayrıştırmalıdır.

### Fixed

- **`POST /smartcard/pin/validate` — yanlış PIN durumunda artık `PKCS11_PIN_INCORRECT`
  + `pkcs11Code: "CKR_PIN_INCORRECT"` dönüyor (regression düzeltmesi, REST wire
  contract genişlemesi)**: Eski davranışta `Pkcs11Session.open` sadece üst
  seviye `IOException("load failed")` mesajına bakıyordu; gerçek
  `sun.security.pkcs11.wrapper.PKCS11Exception("CKR_PIN_INCORRECT")` 3 katman
  derinlikteki cause zincirinde gizli kalıyordu. "load failed" string'i
  PIN-heuristic match'ine düşmediği için yanlış PIN bile `503
  PKCS11_UNAVAILABLE / "PKCS#11 keystore yüklenemedi: load failed"` olarak
  dönüyordu — frontend yanlış PIN ile sürücü hatasını ayırt edemiyordu, kart
  kilitlenme uyarısı da gösterilemiyordu. Çözüm: yeni `services.keystore.Pkcs11Errors`
  helper'ı cause zincirini gezerek PKCS11Exception'ın `CKR_xxx` sembolik koduna
  ulaşıyor (PKCS#11 v2.40 §A "Return Values"), ardından bilinen kodları yapısal
  bir `Outcome`'a (`PIN_INCORRECT`, `PIN_LOCKED`, `PIN_EXPIRED`,
  `PIN_INVALID_FORMAT`, `SESSION_BUSY`, `DEVICE_REMOVED`, `UNKNOWN`) eşliyor.
  `Pkcs11AuthException` artık `pkcs11Code`, `locked`, `attemptsRemainingHint`
  alanları taşır; `ErrorModel`'e karşılık olarak `pkcs11Code`, `pinLocked`,
  `pinAttemptsRemainingHint` alanları eklendi (NON_NULL ile sadece ilgili
  hatalarda görünür, geri uyumlu). HTTP statü davranışı: `PIN_INCORRECT` → `401`,
  `PIN_LOCKED` → **`423 LOCKED` (RFC 4918)** + `pinLocked: true` +
  `pinAttemptsRemainingHint: "0"` — frontend bu kombinasyonla PIN alanını disable
  edip "PUK ile sıfırlamanız gerek" mesajı gösterebilir; `PIN_EXPIRED` → `401`,
  `DEVICE_REMOVED` → `503 PKCS#11 cihaz hatası (CKR_TOKEN_NOT_PRESENT)` mesajı.
  Bilinmeyen `CKR_VENDOR_*` kodları yine de `503 PKCS11_UNAVAILABLE`'a düşer fakat
  mesaj artık CKR sembolünü içerir (operasyon ekibi tanı kabiliyeti). Eski 1-arg
  / 2-arg `Pkcs11AuthException` constructor'ları korundu, mevcut
  `errorCode=PKCS11_AUTH_FAILED` davranışı default. **Frontend rehberi**: hata
  yanıtı geldiğinde önce `pinLocked`'a bak — true ise PIN input'u disable et,
  retry'a izin verme; sonra `code`'a bak (`PKCS11_PIN_INCORRECT` retry OK,
  `PKCS11_PIN_LOCKED` PUK reset, `PKCS11_PIN_EXPIRED` out-of-band yenile,
  `PKCS11_PIN_INVALID_FORMAT` PIN format kuralları göster). Otomatik retry
  ASLA — her başarısız deneme PIN sayacını harcar. Frontend için "kalan deneme
  sayısı" bilgisi şu anda sadece `0` (kilitli) veya null döner; ileride
  `CKF_USER_PIN_FINAL_TRY` / `CKF_USER_PIN_COUNT_LOW` token bayrakları
  okunarak `"1"` / `"low"` ipuçları eklenebilir (vendor-bağımsız PKCS#11 v2.20+
  flag'leri). Kapsam: aynı zenginleştirilmiş mapping `Pkcs11Session.getPrivateKey`
  yolunda da aktif — imzalama akışı sırasında PIN session timeout'u olursa
  tutarlı hata sözleşmesi sağlar.
- **`GET /smartcard/certificate` — Revocation kontrolü artık AIA ile
  tamamlanmış zincir üzerinden çalışıyor (davranış düzeltmesi)**: KamuSM
  kartlarının token'ında genellikle yalnız end-entity sertifika yazılı
  olduğundan, listeleme akışı `RevocationChecker`'a yalnız leaf içeren bir
  zincir geçiyordu. `RevocationChecker.findIssuer` issuer bulamayınca
  OCSP/CRL'i atlıyor ve `status=UNKNOWN` dönüyordu — kullanıcının "geçerli"
  bir kartı UI'da revocation alanı şüpheli görünüyordu. Çözüm:
  `CertificateListingService` artık `CertificateChainBuilder`'ı (önceden
  yalnız `PadesService` / `XadesService`'in kullandığı AIA-takipli zincir
  inşacı) inject ediyor; her sertifika için token-bundle'dan inşa edilen
  kısmî zinciri AIA `caIssuers` URL'leriyle root CA'ya kadar genişletiyor
  ve `RevocationChecker`'a bu tam zinciri veriyor. Artık KamuSM cert'i
  için OCSP yanıtı alındığında `status=ACTIVE` (veya `REVOKED`) doğru
  şekilde set edilir. `valid` (zamansal pencere) alanı bu değişimden
  etkilenmez — zaten OCSP/CRL'den bağımsız çalışıyordu. AIA HTTP timeout
  / hata durumlarında `CertificateChainBuilder` mevcut zincirle sessizce
  devam ettiği için listeleme akışı bozulmaz; en kötü ihtimalle eski
  davranışa düşer (`status=UNKNOWN`). Performans: tipik kart için ek
  ~1-2 HTTP GET (her biri 3 sn timeout cap), karta bağlı 0.5-1.5 sn
  ek listeleme süresi.

## [1.0.3] — 2026-05-28

## [1.0.2] — 2026-05-26

## [1.0.1] — 2026-05-26

Bu ilk public release — projenin sözleşmesi (REST wire + `services.*` Java
imzaları) bu sürümle stabilize ediliyor. Frontend tüketicilerin sözleşme
sürümüne kilitleyebileceği iki ana endpoint:

- `POST /xades/sign` — `contentType` enum değeri `HrXmlDocument` →
  `HrXmlCounterSignature` olarak yeniden adlandırıldı (mantıksal davranışı
  birebir yansıtır: ETSI XAdES counter-signature). Eski değer
  `IllegalArgumentException` fırlatır.
- `GET /smartcard/certificate` — `pin` query parametresi tamamen kaldırıldı
  (PIN'siz public-session listeleme); `eligibleOnly` varsayılanı `false` →
  `true` oldu.

Programatik olarak `services.certificate.CertificateListingService` çağıran
kod varsa, `listCertificates(...)` overload'ından `pin` argümanını çıkar.

### Changed

- **`XmlContentType.HrXmlDocument` → `HrXmlCounterSignature` rename
  (BREAKING — REST wire contract)**: `/xades/sign` çağrısındaki
  `contentType` query/form alanının iki kabul ettiği değerden biri olan
  `"HrXmlDocument"` artık `"HrXmlCounterSignature"` olarak değişti.
  Mantıksal sebep: bu kanal aslında "İK XML belgesi imzalama" değil,
  **var olan imzalı XML üzerine ETSI XAdES counter-signature ekleme**
  yapıyordu — eski ad davranışla uyumsuzdu, frontend okuyan
  geliştirici için yanıltıcıydı. Yeni ad metodun yaptığı işi birebir
  yansıtır. Eşzamanlı olarak `XadesService.signHrXmlDocument()` →
  `signHrXmlCounterSignature()` public metodu da yeniden adlandırıldı
  (Java sözleşmesi de değişti — bu servisi programatik olarak çağıran
  kod varsa metod adı güncellenmeli). Case-insensitive parser
  korunur: `HrXmlCounterSignature`, `hrXmlCounterSignature`,
  `HR_XML_COUNTER_SIGNATURE`, `hr-xml-counter-signature` hepsi kabul
  edilir; eski değer `"HrXmlDocument"` artık `IllegalArgumentException`
  fırlatır ("Geçersiz XmlContentType" mesajıyla). OpenAPI schema'da
  yeni değer görünür, Scalar UI'da güncellenmiş enum option'ı listelenir.
  Frontend değişikliği: XAdES counter-signature gönderirken
  `contentType: "HrXmlCounterSignature"`.
- **`/smartcard/certificate` `eligibleOnly` varsayılanı `true` oldu
  (BREAKING — frontend davranış değişikliği)**: Default temiz UX'i
  öne almak için `?eligibleOnly` belirtilmediğinde yalnız
  `eligibleForSignature=true` olan sertifikalar döner — son kullanıcı
  seçim ekranı için tek satır filtre yazmak gerekmez, sadece
  `?purpose=SIGNING` yeterli. Audit / debug / "neden cert görünmüyor"
  diagnostiği için `?eligibleOnly=false` parametresi ile geçmiş davranışa
  (tüm cert'ler) dönülebilir. OpenAPI Schema'da `defaultValue` `"true"`
  olarak güncellendi; Scalar UI'da default işaretli görünür. Geriye
  uyumluluk: parametresiz mevcut çağrılar daha az veri döner (revoked /
  süresi dolmuş / KeyUsage uyumsuz cert'ler artık yanıtta yok). Frontend
  bu cert'leri "expired/invalid" sekmesinde göstermek isterse explicit
  `?eligibleOnly=false` geçmelidir.
- **`/smartcard/certificate` artık PIN istemez (BREAKING)**: PKCS#11
  spec'i v2.40 §10.4 gereği `CKO_CERTIFICATE` objeleri
  `CKA_PRIVATE=FALSE` ile saklanır — public session ile (login'siz)
  okunabilir. `pin` query parametresi kaldırıldı; çağrı yalnız
  `terminalName` + opsiyonel `pkcs11LibraryPath` / `cardType` /
  `purpose` / `eligibleOnly` ile yapılır. Frontend'i etkileyen
  değişiklik: kart takıldığında PIN istemeden sertifika listesi
  alınabilir; PIN yalnız imzalama uçlarında (`/pades/sign`,
  `/xades/sign`, `POST /gibApplication`) gereklidir. Avantajlar: PIN
  sayacı gereksiz harcanmaz (kart kilitlenme riski düşer), PIN
  frontend'de gereksiz uzun süre tutulmaz, iki-adımlı UX flow (listele
  → seç → PIN gir → imzala) açılır.
- **Low-level PKCS#11 reader ile P11KeyStore bypass**: SunPKCS11'in
  `P11KeyStore.engineLoad()` `CKF_LOGIN_REQUIRED` bayraklı token'larda
  (Türkçe Kamu SM kartlarının tamamı — AKIS, e-İmza, Mali Mühür,
  KamuSM QES bu bayrağı taşır) **listeleme için bile** `C_Login`
  yapmaya çalışır ve `LoginException: no password provided` fırlatır.
  Yeni `Pkcs11PublicCertificateReader` `sun.security.pkcs11.wrapper
  .PKCS11` low-level JNI sarmalayıcısını (reflection üzerinden)
  çağırarak P11KeyStore katmanını atlar ve doğrudan
  `C_OpenSession(public R/O)` → `C_FindObjectsInit({CKA_CLASS=CKO_CERTIFICATE,
  CKA_CERTIFICATE_TYPE=CKC_X_509})` → `C_GetAttributeValue({CKA_LABEL,
  CKA_ID, CKA_VALUE})` ile sertifika DER bytes'larını çeker. Reflection
  tercih nedeni: `Pkcs11Session.instantiateSunPkcs11` ile tutarlı, JDK
  8 / 9+ arasında portable. Alias çözümü: CKA_LABEL (UTF-8) → CKA_ID
  (hex) → `cert-<handle>` fallback.
- **Sertifika zinciri in-memory subject-issuer matching ile inşa
  edilir**: `CertificateListingService.buildChainFromBundle` token'daki
  tüm cert'lerden (low-level reader ile çekilenler) leaf'in zincirini
  kurar; identity-based visited guard ile cyclic bundle'lara karşı
  korunaklı. KamuSM kartlarında ara + kök CA cert'leri genelde birlikte
  yazılı olduğundan tam zincir döner; eksik halkalar
  `RevocationChecker` / `CertificateChainBuilder` tarafından AIA ile
  sonradan tamamlanabilir.
- **`CertificateListingService.listCertificates` imzası değişti
  (BREAKING)**: `(terminalName, pin, pkcs11LibraryPath[, cardType])`
  → `(terminalName, pkcs11LibraryPath[, cardType])`. Bu servisi
  programatik olarak çağıran kod (örn. `GibApplicationController`)
  uyarlandı.

### Added

- **`GET /smartcard` artık host ortam metadata'sı döner** (`SmartCardResponse`
  + `withCurrentHost()`): `osName`, `osVersion`, `osArch`, `javaVersion`
  alanları `System.getProperty("os.name" / "os.version" / "os.arch" /
  "java.version")` ile doldurulur. Eski projedeki (`SmartCard(osName,
  osVersion, osArch, cards[])`) host metadata paritesi geri getirildi —
  frontend platforma özgü vendor lib path seçimi (örn. macOS `.dylib`,
  Windows `.dll`, Linux `.so` + ARM/x86_64 suffix) için bu alanları
  okuyabilir. `@JsonInclude(NON_NULL)` ile alanlar boş geldiğinde JSON'dan
  süzülür (server-side `withCurrentHost()` çağrılmazsa frontend `null`
  görmez).
- **PAdES için opsiyonel `reason` + `location` override** (`SignDocumentDto`
  + `PadesService.resolveReason` / `resolveLocation`): PDF'in imza
  panelinde "Reason" ve "Location" alanlarını DTO üzerinden override
  etmek artık mümkün. Trim + null/empty → default fallback (`"e-Belge
  imzalama"` + `""`) ile eski projedeki davranışa geriye uyumludur. Boş
  string override default ile aynı sonucu verir (PDF reader'larda
  "(unspecified)" gösterimi gibi UX kirliliklerini önler). XAdES'te
  kullanılmaz; sadece PAdES.
- **`EInvoiceGibApplicationDto` → 13 yeni opsiyonel alan** (GİB başvuru
  formu tam parite): Tüzel kişi bloğu için `tradeRegistryNo`,
  `tradeRegistryOffice`, `foundationDate`, `chamberName`,
  `chamberRegistryNo`, `website`, `fax` — eski projede hep boş geçilen
  bu alanlar artık DTO'dan map'lenebilir (GİB tarafı eksik formda
  bazen reddediyor; bu davranış bir bug'dı). Sorumlu (yetkili) kişi
  bloğu için `responsibleTckn`, `responsibleFirstName`,
  `responsibleLastName`, `responsibleMobilePhone`, `responsibleEmail` —
  tüzel kişi başvurusunda yetkili kişiyi forma yazmak için. Mali Mühür
  talep bayrağı için `requestsFinancialSeal` (0/1). TCKN başvurusunda
  `responsibleFirstName` / `responsibleLastName` verilirse sertifika
  subject'inden türetilen ad/soyad'ı override eder; boş/blank verilirse
  sertifika değerine fallback (`preferDtoValue` helper). Tüm alanlar
  `nullSafe()` ile trim + null → boş string normalize edilir.
- **`RevocationChecker` mesajları zenginleştirildi** (RFC 5280 §5.3.1
  CRLReason desteği): OCSP `RevokedStatus` ve CRL entry'lerinde
  bulunan reason kodu numerik değer yerine human-readable label döner
  (`keyCompromise`, `cACompromise`, `affiliationChanged`, `superseded`,
  `cessationOfOperation`, `certificateHold`, `removeFromCRL`,
  `privilegeWithdrawn`, `aACompromise`, `unspecified`; bilinmeyen kod
  için `unknown(N)`). Mesajda artık `producedAt` (OCSP `BasicOCSPResp`'den),
  `thisUpdate`, `nextUpdate`, `responder` URL'i de ` ; key=value` formatında
  ek bilgi olarak yer alır. CRL entry'sinde opsiyonel `reasonCode`
  extension (OID `2.5.29.21`) parse'ı için `crlEntryReasonText` helper'ı
  eklendi — extension yoksa graceful null döner (RFC 5280 §5.3.1
  ReasonCode opsiyoneldir, KamuSM eski CRL'lerinin bazıları bu
  extension'ı koymaz). OCSP `UnknownStatus` artık debug log + CRL fallback
  ile devam eder (responder URL log'lanır). Frontend bu mesajı parse edip
  kullanıcıya "Sertifikanız `keyCompromise` nedeniyle iptal edilmiş — yeni
  cert için CA'nıza başvurun" gibi anlamlı gösterim yapabilir.
- **ARM64 (aarch64) lib path desteği** (`application.yml` →
  `mersel.signer.pkcs11.library-search-paths` + softhsm): Linux
  multiarch dizini `/usr/lib/aarch64-linux-gnu`,
  `/usr/local/lib/aarch64-linux-gnu` ve
  `/usr/lib/aarch64-linux-gnu/softhsm` lib arama yoluna eklendi. Apple
  Silicon Linux VM'leri (Parallels, UTM, Lima, OrbStack), Raspberry Pi
  4/5, AWS Graviton (`a1`, `m6g`, `c7g`), Pardus ARM ve diğer ARM64
  Linux platformlarında vendor PKCS#11 lib'leri (AKIS, e-İmza, Mali
  Mühür) artık otomatik bulunur. x86_64 macOS / Linux davranışı
  değişmedi.
- **4 yeni test dosyası + 1 genişletilmiş test dosyası — toplam 23
  yeni senaryo**:
  - `SmartCardControllerHostMetadataTest` (4 senaryo, yeni dosya) —
    controller seviyesinde Mockito ile `listCards()`'in host metadata 4
    alanını set ettiğini, `SmartCardInfo` → `SmartCardDetail` map
    paritesini ve JSON serializasyon kontratını doğrular.
  - `SmartCardResponseTest` (4 senaryo, yeni dosya) —
    `withCurrentHost()` host metadata set davranışı + Jackson
    `@JsonInclude(NON_NULL)` ile null alanların JSON'dan süzülmesi +
    null cards constructor fallback'i.
  - `GibApplicationControllerBuildFormTest` (5 senaryo, yeni dosya) —
    DTO → `EFaturaBasvuruFormIstek` map'leme; minimal DTO opsiyonel
    alanlar default boş string; full DTO tüm opsiyonel alanları forma
    yazar; TCKN başvurusunda sertifika subject ad/soyad split'i; DTO
    `responsibleFirstName/LastName` override önceliği; blank override
    sertifika değerine fallback (`preferDtoValue` davranışı).
  - `PadesServiceReasonLocationTest` (6 senaryo, yeni dosya) —
    `resolveReason` / `resolveLocation` null / blank / trim / custom
    değer / default fallback davranışı.
  - `RevocationCheckerReasonTest` (4 yeni senaryo, dosya
    genişletildi) — BC `X509v2CRLBuilder` ile in-memory CRL üretip
    `crlEntryReasonText`'in RFC 5280 §5.3.1 ReasonCode extension'ını
    doğru parse ettiğini (`keyCompromise`, `cessationOfOperation`,
    `superseded`) ve extension yokken graceful null döndüğünü
    doğrular. Mevcut 3 senaryo (`crlReasonText` tablosu + null input)
    korundu.

  Toplam unit test sayısı **199 → 207** (sadece bu chat'te eklenen 8
  yeni senaryo: 4 SmartCardControllerHostMetadataTest + 4
  RevocationCheckerReasonTest); diğer 3 yeni dosya (15 senaryo)
  working tree'de hâlihazırda mevcuttu, bu sürümle git'e ilk kez
  commit'lendi.
- **`Pkcs11PublicCertificateReader`** (`services/keystore/`): low-level
  PKCS#11 ile PIN'siz token sertifika okuyucu. Slot bazlı hata
  toleransı (bozuk token / removed card → warn log, geri kalan
  slot'lara devam).
- **`Pkcs11Reflection`** (package-private helper): SunPKCS11
  `sun.security.pkcs11.wrapper.PKCS11` API'sini reflection üzerinden
  thread-safe singleton ile expose eder. Method handle'lar bir defa
  initialize edilir, hot-path overhead'i ihmal edilebilir.
- **6 yeni unit test**: `CertificateListingServiceChainTest` (gerçek 3
  katmanlı Root → Intermediate → Leaf RSA chain üretip subject-issuer
  matching'i, eksik intermediate, self-signed root, null leaf, cyclic
  bundle senaryolarını doğrular).
- **CertificatePolicies + QCStatements extension parser'ları**
  (`CertificateInspector.certificatePolicies` /
  `CertificateInspector.qcStatementOids`): RFC 5280 §4.2.1.4
  CertificatePolicies (OID `2.5.29.32`) ve ETSI EN 319 412-5
  QCStatements (OID `1.3.6.1.5.5.7.1.3`) extension'larından ham OID
  setleri çıkarır. BouncyCastle `PolicyInformation` / `QCStatement`
  parser'ları, IO/parse hatalarında boş set döner.
- **`TurkishCertificatePolicy` enum + Türkçe Kamu SM policy OID
  tanıma**: `2.16.792.1.61.0.1.5070.1.1` (KamuSM QES Bireysel),
  `2.16.792.1.61.0.1.5070.1.2` (KamuSM QES Kurumsal),
  `2.16.792.1.2.1.1.5.7.50.1` (Mali Mühür İmza),
  `2.16.792.1.2.1.1.5.7.50.2` (Mali Mühür Şifreleme). Her enum sabiti
  `impliedPurpose` ve `qualified` bilgisi taşır.
- **`CertificateInspector.purpose()` Türkçe politika kısa-yolu**:
  CertificatePolicies'te Türkçe SIGNING / ENCRYPTION policy OID'i
  bulunduğunda RFC 5280 generic KU/EKU karar ağacı atlanır ve doğrudan
  kilitlenir. TÜBİTAK Kamu SM kartlarındaki non-standart bit
  kombinasyonlarını tolere eder (örn. bit'ler yanlış set olsa bile
  Mali Mühür İmza OID'i SIGNING'i garantiler).
- **`CertificateResponse` yeni alanları**: `certificatePolicyOids`,
  `turkishCertificatePolicies`, `qcStatementOids`. `extendedKeyUsageOids`
  semantiği değişti — artık extension'daki **TÜM** ham OID'leri taşır
  (enum'a düşenler dahil), audit / debug / vendor-specific OID
  görünürlüğü için. `extendedKeyUsages` tipli enum seti aynı extension'ın
  enum'a eşleşen alt setini taşır; iki set **kesişimseldir**.
- **`recommended` 4 kademeli tie-breaker**
  (`CertificateListingService.RECOMMENDATION_PRIORITY`): `qualified=true`
  (ETSI QES) → Türkçe SIGNING policy (Mali Mühür İmza / KamuSM QES) →
  `purpose==SIGNING` (saf imza, MIXED sonra) → en yeni `notBefore`.
  Eligibility filtresinden artık `MIXED` cert'ler de geçer (eskiden
  sadece SIGNING).
- **`/smartcard/certificate` query filtreleri**: `purpose` (SIGNING /
  ENCRYPTION / AUTHENTICATION / MIXED / OTHER / ALL, case-insensitive)
  ve `eligibleOnly` (boolean) — frontend son kullanıcı seçim ekranı için
  `?purpose=SIGNING&eligibleOnly=true` ile temiz liste alır;
  parametresiz çağrı audit / debug için tüm cert'leri döner.
- **9 yeni unit test**: `SmartCardControllerFilterTest` (filtre AND /
  case-insensitive / unknown fallback), `CertificateInspectorKeyUsageTest`
  Türkçe policy + CertificatePolicies + QCStatements parse'ı (7 yeni
  senaryo), `CertificateListingServiceRecommendationTest` 4 kademeli
  tie-breaker (5 yeni senaryo).
- **Modern API reference UI (Scalar)**: Swagger UI yerine
  [Scalar](https://scalar.com) bundle'lı. OpenAPI 3 spec'i `/v3/api-docs`
  endpoint'inden çekilir; 3.5 MB standalone bundle yerel `static/vendor/scalar/`
  altında — offline çalışır. `springdoc-openapi-ui` dep'i kaldırıldı,
  `springdoc-openapi-webmvc-core` yalnız spec generator olarak kaldı.
- **Masaüstü splash + system tray** (`SplashWindow`, `SystemTrayManager`,
  `DesktopUiBootstrap`): Swing `JWindow` ile minimal splash, açılışta;
  Spring `ApplicationReadyEvent` ile kapanır + tray icon mount edilir.
  Tray menüsü: "API dökümanını aç", "Sağlık kontrolünü aç", "Çıkış" +
  güncelleme bulunduğunda dinamik "Yeni sürüm v… — indir" öğesi.
  Headless / Docker / sunucu ortamlarda (`GraphicsEnvironment.isHeadless()`)
  sessizce devre dışı. Env var override'ları: `MERSEL_AGENT_UI`,
  `MERSEL_AGENT_UI_SPLASH`, `MERSEL_AGENT_UI_TRAY`, `MERSEL_AGENT_UI_URL`.
- **GitHub Releases tabanlı otomatik güncelleme** (`SemanticVersion`,
  `UpdateInfo`, `LatestRelease`, `VersionProvider`, `GitHubReleaseClient`,
  `UpdateService`, `UpdateController`): Daemon hazır olduğunda arka plan
  daemon thread'inde GitHub API'sini çağırır, `tag_name` → mevcut sürüm
  karşılaştırması yapar. `Implementation-Version` (MANIFEST) ve
  `pom.properties` üzerinden mevcut sürüm tespit edilir. ETag /
  conditional GET ile rate-limit dostu. Pattern eşleşen jar asset'i (yoksa
  ilk `.jar`, o da yoksa `html_url`) seçilir. Prerelease desteği opt-in.
  Tüm HTTP/parse hataları yutulur (WARN log) — daemon ASLA güncelleme
  kontrolü yüzünden patlamaz. REST: `GET /update/status` (cache'li),
  `POST /update/check` (cache bypass). Konfigürasyon: `mersel.signer.update.*`
  + 7 env var (`MERSEL_AGENT_UPDATE*`).
- **Sertifika iş amacı türetme** (`KeyUsage`, `ExtendedKeyUsage`,
  `CertificatePurpose` enum'ları + `CertificateInspector.keyUsage()` /
  `extendedKeyUsage()` / `purpose()` yardımcıları): RFC 5280 §4.2.1.3
  KeyUsage extension'ından 9 bit'in tipli enum eşleştirmesi (saf JDK
  `cert.getKeyUsage()`, BouncyCastle yok); RFC 5280 §4.2.1.12 EKU'dan
  tanınan 7 OID için tipli enum + bilinmeyen OID'ler için pass-through
  `Set<String>`; yüksek seviyeli `CertificatePurpose` (`SIGNING` /
  `ENCRYPTION` / `AUTHENTICATION` / `MIXED` / `OTHER`) türetimi 6 kurallı
  bir karar ağacıyla.
- **`CertificateResponse` yeni alanları + recommendation post-processing**
  (`CertificateListingService.annotateRecommendation`): `keyUsage`,
  `extendedKeyUsages`, `extendedKeyUsageOids`, `purpose`,
  `eligibleForSignature`, `recommended`. Liste seviyesinde en yeni geçerli
  `SIGNING` cert tek bir `recommended=true` ile işaretlenir; tie-breaker
  `notBefore` desc. Frontend tüm cert'leri görür (eskiden legacy TÜBİTAK
  SDK sessizce ENCR0'ı filtreliyordu); SIGN0 pre-select edilir.
- **REST 2.1 rename pass** — anlamsız / legacy alan adları temizlendi,
  domain-anlamlı yenileriyle değiştirildi (geriye uyumluluk yok, sözleşme
  kesik kesilir):
  - `wrapperFile` → `pkcs11LibraryPath` (SignDocumentDto, EInvoiceGib
    DTO, SmartCardController query)
  - `type` / `xmlContentType` → `contentType` (XAdES)
  - `mail` → `email` (EInvoiceGibApplicationDto)
  - `registerNumber` → `taxId` (`/gibApplication`)
  - `serialNumber` (CertificateResponse VKN/TCKN) → `taxId`
  - `certificateSerialNumber` → `x509SerialNumber`
  - `alias` (CertificateResponse) → `id` (kanonik)
  Önceki APDU/PKCS#11 seçim bayrağı `useApdu` da tamamen kaldırıldı; imzalama
  her durumda PKCS#11 üzerinden yapılır.
- **`XmlContentType` enum'a `@JsonCreator`**: case-insensitive +
  `_-` normalize (örn. `xml_document`, `XML-DOCUMENT`, `xmldocument`
  hepsi kabul).
- **PadesController/XadesController @ModelAttribute pattern**: Flat
  `@RequestParam` lerden DTO binding'e geçildi (`@Valid @ModelAttribute
  SignDocumentDto`). Multipart form-data binding'i Spring'in kendi
  setter-based binder'ı yapar.
- **`UpdateController` + `UpdateService` + `GitHubReleaseClient` tests**:
  `SemanticVersionTest` (12 senaryo), `GitHubReleaseClientTest` (8
  senaryo, OkHttp `MockWebServer` + `release-latest.json` fixture, ETag
  round-trip dahil), `UpdateServiceTest` (11 senaryo, Mockito; hata
  yutma, prerelease filter, download URL fallback).
- **`CertificateInspectorKeyUsageTest`** (6 senaryo, BouncyCastle
  `X509v3CertificateBuilder` ile in-memory self-signed RSA cert'ler):
  Signing-only → SIGNING; Encryption-only → ENCRYPTION; Mixed → MIXED;
  CA bits → OTHER; TLS client (DIGITAL_SIGNATURE + EKU CLIENT_AUTH) →
  AUTHENTICATION; boş extension → OTHER + boş set'ler; unknown OID
  pass-through.
- **`CertificateListingServiceRecommendationTest`** (7 senaryo): Tek
  SIGNING cert recommended; ENCR0 + SIGN0'da SIGN0 seçilir; çoklu SIGN
  cert'te en yeni `notBefore` kazanır; SIGNING cert yoksa kimse
  recommended değil; revoked SIGN cert recommended değil; her zaman tam
  olarak bir recommended; null / empty list graceful.
- **`SignerApplicationContextTest` genişletildi**: `UpdateController` +
  `UpdateService` bean'lerinin de wiring'i CI'da doğrulanır. UI / update
  startup-check'i `@TestPropertySource` ile kapatılır.
- **`mersel.signer.ui.*` + `mersel.signer.update.*` konfigürasyon
  bloğu** ve `SignerProperties.Ui` / `SignerProperties.Update` nested
  classes (toplam 12 yeni property, hepsi env var override'lı).
- **`UpdateCheckException` (SignerException alt sınıfı)** — kod
  `UPDATE_CHECK_FAILED`.
- **`mockwebserver` test dependency** (`com.squareup.okhttp3`,
  `${okhttp.version}` ile aynı).

- **4-katmanlı akıllı kart tanıma stratejisi** (`SmartCardManager.resolveLibrary` —
  L1 → L2 → L3 → L5 fallback). TÜBİTAK ma3api `AkisTemplate.isAkisATR` mimarisinin
  vendor-agnostic ve genelleştirilmiş hâli. Her katman bir öncekinin başarısızlığında
  devreye girer; ilk başarı sonraki katmanları atlar.
- **Layer 2 — Historical-bytes regex matching** (`AtrPatternMatcher` +
  `CardType.historicalBytePatterns` + `<atr-pattern regex="..."/>` XML elementi):
  Bilinen ATR listesi dışında kalan yeni sürüm AKIS varyantları, vendor parmak izi
  (`80(67|65)55454B4145...` UEKAE, `80655443...31C073F6218081..` TC) regex'leriyle
  otomatik tanınır. TÜBİTAK orijinal `HISTORICAL_BYTE_REGEXES`'in birebir hâli
  + Java `Pattern.compile` ile derlenir, sonuçlar 5 sn TTL cache'lenir. AKIS card-type'ına
  iki regex eklendi: UEKAE ailesi (eski NES 2011-2019) + TC ailesi (yeni NES + T.C.
  Kimlik 2020+).
- **Layer 3 — `Pkcs11ModuleProbe`**: L1 ve L2 boş çıktığında devreye giren vendor lib
  probe servisi. `smartcard-config.xml`'deki tüm `<lib>` adlarını sırayla diskte arar,
  fiziksel olarak bulunan her vendor PKCS#11 kütüphanesini `SunPKCS11` provider'ı ile
  başlatır ve slot'unda token gören ilk lib'i o kartın gerçek vendor'ü olarak işaretler.
  Sonuç (terminal + ATR) anahtarıyla 30 sn cache'lenir. Yan etki yönetimi: her denemede
  provider eklenir ve hemen kaldırılır; native `UnsatisfiedLinkError`, `ProviderException`,
  `Error` yutulur — yanlış vendor lib'inin yüklenmesi bütün servisi devirmemeli.
- **Layer 5 — Frontend dropdown fallback**: Hiçbir katman kart tipini tespit edemediğinde
  `Pkcs11LibraryNotFoundException` artık iki yeni alan taşıyor:
  - `cardTypeCandidates`: sistemdeki tüm CardType isimleri (alfabetik) — frontend kart
    seçim modal'ı için
  - `userSelectionRequired`: `true` → frontend kullanıcıdan seçim istemeli
  HTTP 503 JSON yanıtı bu alanlarla zenginleşti; `@JsonInclude(NON_NULL)` sayesinde
  başarılı tanıma durumlarında JSON'a sızmaz.
- **`GET /smartcard/certificate?cardType=AKIS`**: Frontend kart seçim modal'ının
  sonucu için opsiyonel parametre. Verildiğinde ATR algılaması atlanır ve manuel
  seçilen kart tipiyle PKCS#11 lib aranır.
- **TÜBİTAK ma3api-smartcard-2.3.11 reflection extract**: `AkisTemplate.ATR_HASHES`
  static field'ından (29 ATR + 2 regex) bizim listede olmayan 10 yeni AKIS ATR
  `smartcard-config.xml`'e eklendi. Yöntem: `URLClassLoader` + `Field.setAccessible(true)`
  ile XOR-deobfuscate edilmiş final string'ler çekildi. TÜBİTAK jar repo'ya eklenmedi;
  sadece tek-seferlik extract ile sabitler kopyalandı. `AladdinTemplate`/`SafeSignTemplate`'de
  `ATR_HASHES` field'ı yok (yalnız AKIS pattern-matching kullanıyor).
- `AtrPatternMatcherTest` — 16 senaryo: 16 UEKAE varyantı + 13 TC varyantı (kullanıcının
  yeni kartı dahil) regex'lere düşüyor; ALADDIN/SAFESIGN/GEMPLUS ATR'leri AKIS regex'ine
  düşmüyor (false-positive yok); bozuk/kısa/null ATR graceful `Optional.empty()`;
  anchored `matches()` semantiği; case-insensitive flag.
- `Pkcs11ModuleProbeTest` — 10 senaryo (JNI olmadan, mock `ProbeStep`'le): cache hit/miss,
  null key cache bypass, exception swallowing, ilk başarılı match wins, registry sırası
  korunur, invalidate cache temizler, fonksiyonel arayüz sözleşmesi.
- `CardTypeRegistryTest` — 8 senaryo: L1 exact match, L2 regex fallback (sentetik AKIS
  ATR), L2'nin ALADDIN'i yakalamadığı, `candidateNames()` alfabetik sıralı, L2 cache
  davranışı, `invalidatePatternCache()`.
- `SmartCardManagerTest` 5 senaryoya çıktı: L1 hit → L3 atlanır, L1+L2 miss + L3 hit
  → kart tipi probe'tan döner, manual `cardType` override ATR algılamayı bypass eder,
  Layer 5 candidates dolu döner.
- **Kullanıcı dostu PKCS#11 lib bulunamadı hatası** (`PKCS11_LIBRARY_NOT_FOUND`):
  Sürücü bulunamadığında artık kullanıcıya yapılandırılmış JSON yanıtı dönüyor:
  algılanan kart tipi (`cardType`), aranan lib adı (`requiredLibrary`),
  diskte taranıp bulunamayan tüm yollar (`searchedPaths`) ve vendor sürücüsünün
  resmi indirme URL'i (`downloadHint`). HTTP statüsü 503 Service Unavailable.
  Eski hata mesajı (`ILLEGAL_STATE: PKCS#11 kütüphanesi çözümlenemedi`) terminal
  adı ve wrapperFile dışında bilgi vermiyor, kullanıcıyı çıkmaza sürüklüyordu.
- **`Pkcs11VendorHints`**: AKIS, ALADDIN, SAFESIGN, GEMPLUS, NCIPHER ve diğer
  18+ kart tipi için vendor sürücü indirme/destek URL'leri (Kamu SM, Thales,
  A.E.T. Europe, Entrust, Atos, vb). Bilinmeyen kart için generic mesaj.
- **`Pkcs11LibraryResolver.ResolutionResult`**: Lib path çözümleme sürecinin
  tanılayıcı görünümü — çözülen yol (varsa), denenen dosya adları, taranan
  dizinler, bare-name fallback kullanıldı mı bilgisi. `SmartCardManager` bu
  yapıdan zenginleştirilmiş hata mesajı üretir.
- **ATR cache** (5 sn TTL, `SmartCardReaderService`): Bazı PKCS#11 sürücüleri
  kartı eksklusif modda tutar; aynı kartı kısa aralıklarla iki kez bağlamak
  `SCARD_E_SHARING_VIOLATION` ile başarısız olur. Diagnostic endpoint çağrısı
  ardından sertifika listeleme yaparken sessizce ATR okuyamayan eski kod yerine
  artık ATR cache'ten okunup PKCS#11 oturumunun tek hâkim olması sağlanıyor.
  Cache TTL kısa olduğu için kart değişikliklerinde cache otomatik yenilenir.
- **Genişletilmiş PKCS#11 lib arama yolları** (`application.yml`,
  TÜBİTAK ma3api-smartcard-2.3.11 referansından): Windows için `C:\Program Files\AKiA`,
  `C:\Program Files (x86)\AKiA`, `\bin` alt-dizinleri; macOS için `/Library/Akia`,
  `/Library/Akia\` (Türkçe büyük), `/usr/local/Cellar/akia/lib`, `/Applications/AKiA.app`;
  Linux için `/usr/lib/akia`, `/usr/lib/akia`, `/opt/akia`. Aynı zamanda nCipher
  nfast, SafeNet Luna, A.E.T. Europe SafeSign, KOBIL mIDentity klasörleri eklendi.
- **3. ALADDIN ATR'si** (`3B7F96000080318065B0846160FB120FFD829000`,
  SafeNet eToken 5110 GA): TÜBİTAK ma3api-smartcard-2.3.11 referansından eklendi.
- `Pkcs11VendorHintsTest` (7 senaryo): AKIS → Kamu SM URL eşleşmesi,
  case-insensitive lookup, bilinmeyen kart için null, Türkçe ipucu formatı,
  tüm URL'lerin `https://` ile başladığı, yaygın Türkiye e-imza kartlarının
  tablo kapsamında olduğu.
- `Pkcs11LibraryNotFoundExceptionTest` (6 senaryo): Yapısal alanlar, immutable
  list garantisi, null-safety, JSON serialization (`code`, `cardType`,
  `requiredLibrary`, `searchedPaths`, `downloadHint`), eski hata kodlarının
  ek alanlarla kirlenmediği (`@JsonInclude(NON_NULL)`).
- `Pkcs11LibraryResolverTest` (6 senaryo): Boş giriş için unresolved, bare-name
  fallback, OS-aware candidate file names, extra search paths,
  per-OS sanity checks (Akia/AKiA/nfast yolları), OS detection ile
  `os.name` system property uyumu.
- `SmartCardManagerTest` (2 senaryo): ATR ile kart algılansa da sürücü diskte
  yoksa rich exception fırlatması, hiçbir kart + lib path olmadığında kullanıcıyı
  `pkcs11LibraryPath` parametresine yönlendiren mesaj.
- `ErrorModel`'e `cardType`, `requiredLibrary`, `searchedPaths`, `downloadHint`
  alanları eklendi (`@JsonInclude(NON_NULL)` sayesinde diğer hatalarda gizli).
- **Cross-platform PCSC bootstrap** (`PcscEnvironment`): macOS / Linux / Windows
  için `sun.security.smartcardio.library` system property'sini otomatik set
  eder. macOS'ta Big Sur ve sonrasında Apple framework binary'leri dyld shared
  cache'e taşındığından önceki `Files.exists` check'i her zaman false dönüyor
  ve **kart algılanmıyordu**; artık symlink path'i koşulsuz set ediliyor
  (`dlopen` dyld cache üzerinden yükler). Linux'ta `libpcsclite.so.1` için 6
  yaygın aday dizini sırayla denenir (Debian multi-arch, RHEL/Fedora, vb).
  Windows için JDK'nın yerleşik `winscard.dll` araması bozulmaz.
- **`MERSEL_AGENT_PCSC_LIBRARY`** env var: native PCSC kütüphane yolunu manuel
  override.
- **`GET /smartcard/diagnostics`**: Kart algılanmadığında ilk başvurulacak uç.
  Yanıt OS/arch/Java version, kullanılan native lib yolu, çözüm sebebi, PCSC
  provider adı ve her terminal'in anlık durumu (ATR + tanınan kart tipi) ile
  problem yerini saniyeler içinde tespit etmeyi sağlar.
- **AIA chain building** (`CertificateChainBuilder`): Akıllı kartın döndürdüğü
  kısmî zincir, sertifikanın `id-ad-caIssuers` AIA URL'leri takip edilerek
  root CA'ya kadar HTTP üzerinden otomatik tamamlanıyor. PAdES-B-LT /
  XAdES-B-LT seviyesindeki imzaların certificate chain'i artık imza içine
  gömülüyor. Sadece BouncyCastle ASN.1 parser + JDK `HttpURLConnection` kullanır;
  ek bağımlılık yoktur. Önceki vendor jar (CSSigner.jar / `C_CertUtil`) ile aynı
  davranışı 50 satır kod ile sağlar.
- Yeni `mersel.signer.chain.*` ayar grubu (env: `MERSEL_AGENT_AIA_ENABLED`,
  `MERSEL_AGENT_AIA_TIMEOUT_MS`, `MERSEL_AGENT_AIA_MAX_DEPTH`).
- `CertificateChainBuilderTest` — 7 senaryo: passthrough, full AIA hop,
  self-signed durma, indirme hatası tolerans, max-depth, AIA URL parse,
  PKCS#7 bundle'dan subject-eşleşmeli seçim.
- `etc/license-header.txt` ile zorunlu MIT license-header'ı; `license-maven-plugin`
  ile `mvn -P quality license:format` üzerinden uygulanıyor.
- **`quality` profile**: JaCoCo line-coverage gate (baseline %15) + MIT
  license-header check, `mvn -P quality verify` ile devreye alınır.
  Default lifecycle'a takılmıyor — `mvn verify` artık coverage gate'e takılmaz.
- **`security` profile**: OWASP dependency-check (CVSS ≥ 8 build kırar),
  `mvn -P security verify` ile çalışır.
- Maven Enforcer (`mvn 3.6.3+`, `jdk 1.8+`).
- GitHub Actions CI workflow (`.github/workflows/ci.yml`).
- PR + Issue template'leri (`.github/`).
- `.editorconfig`.
- `LICENSE` (MIT), `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`,
  `CHANGELOG.md`.
- `MERSEL_AGENT_PORT`, `MERSEL_AGENT_BIND`, `MERSEL_AGENT_CORS_ORIGINS`
  ENV var override desteği.

### Changed

- **`CertificateResponse.keyUsage` → `keyUsages`** (plural, EKU / policy
  alanlarıyla tutarlı). `CertificateInspector.purpose(Set<KeyUsage>,
  Set<ExtendedKeyUsage>)` → `purpose(Set<KeyUsage>, Set<ExtendedKeyUsage>,
  Set<TurkishCertificatePolicy>)` 3-arg overload'a genişletildi.
- **`CertificateResponse` deprecated alanları kaldırıldı** (no-backcompat):
  `alias` (→ `id`), `serialNumber` (→ `taxId`), `certificateSerialNumber`
  (→ `x509SerialNumber`). İstemciler yeni adlara geçmelidir.
- **`extendedKeyUsageOids` semantiği genişledi**: artık extension'daki
  TÜM ham OID'leri taşır (enum'a düşenler dahil). Önceki davranış sadece
  enum'a düşmeyenleri tutuyordu; yeni davranış audit/debug görünürlüğü
  için tüm OID'leri ortaya koyar. Enum'a düşen alt set hâlâ
  `extendedKeyUsages` (tipli) alanında ayrı tutulur.
- **`/smartcard/certificate` yanıt modeli** artık `status` alanını
  tipli `CertificateStatusResponse.Status` enum'u olarak yazar (önceden
  string), KeyUsage / EKU / CertificatePolicies / QCStatements / Türkçe
  Kamu SM policy / iş amacı / eligibility / recommended alanlarını
  ekler.
- **`/smartcard/certificate` query param `wrapperFile` → `pkcs11LibraryPath`**
  (breaking — eski ad kabul edilmiyor).
- **`/gibApplication` GET query param `registerNumber` → `taxId`**
  (breaking — eski ad kabul edilmiyor).
- **`/pades/sign` + `/xades/sign` controller imzaları**: Flat
  `@RequestParam`'lerden `@Valid @ModelAttribute SignDocumentDto`'ya
  geçildi; sözleşme açısından geriye uyumlu (Spring multipart binding
  aynı form alanlarını DTO setter'larına bağlar).
- **`XadesController`** `type` query/form parametresi → `contentType`;
  enum binding case-insensitive.
- **`SignerApplication.main`** açılışta env var bayraklarıyla guarded
  splash gösterir (`SplashLifecycle.show`). Spring `ApplicationReadyEvent`
  ile `DesktopUiBootstrap` splash'i kapatır + tray'i mount eder.
- **`application.yml`**: `springdoc.swagger-ui` ayarları çıkarıldı,
  `springdoc.api-docs.path=/v3/api-docs`; `mersel.signer.ui` ve
  `mersel.signer.update` blokları eklendi.
- **`SmartCardController`** OpenAPI annotation'ları zenginleştirildi
  (`@Operation.description`, her parametre için Türkçe `@Parameter`).
- **`CertificateInspector.hasNonRepudiation`** artık yeni `keyUsage(cert)`
  helper'ı üzerinden döner — duplicate bit-parsing kaldırıldı.

- **`CardTypeRegistry.findByAtr` 4-katmanlı stratejiye geçti**: L1 (exact ATR) →
  L2 (historical-bytes regex) sıralı arama. Mevcut çağrı yerleri (`SmartCardReaderService`,
  `SmartCardManager`) otomatik faydalanır; API geriye uyumludur. L1 yalnız sorgu için
  `findByAtrExact` ve L2 yalnız için `findByAtrPattern` yardımcı method'ları eklendi.
- **`SmartCardManager` ctor 4 argüman**: `(reader, registry, resolver, moduleProbe)`.
  Üç-arg `resolveLibrary(terminalName, pkcs11LibraryPath, cardTypeOverride)`
  Layer 5 fallback round-trip'i için kullanılır; iki-arg overload yalnızca
  cardType override'ı olmayan çağrılar için kısayol.
- **Daemon HTTP-only yayın yapıyor**; HTTPS connector ve `keystore.jks`
  kaldırıldı. TLS gerekiyorsa önüne reverse-proxy konmalı.
- `WebConfig` CORS politikası varsayılan olarak yalnız loopback origin
  pattern'lerine açık; `mersel.signer.cors-allowed-origins` ile genişletilir.
- README endpoint dökümantasyonu controller'lar ile birebir uyumlu hâle
  getirildi (`/pades/sign`, `/xades/sign`, `/smartcard`, `/smartcard/certificate`).
- Spotless yapılandırması Google Java Format + import order ile tamamlandı.

### Removed

- **Ölü ağırlık (~5.6 MB)**: `lib/CSSigner.jar`, `lib/IAIKPKCS11Wrapper.jar`
  ve `lib/native/{linux,macos,windows}/` tüm native binary'leri silindi.
  Statik/dynamic analiz kanıtladı: bu jar'ları **hiçbir kod yolu çağırmıyor**.
  PKCS#11 erişimimiz Sun JDK built-in provider (`sun.security.pkcs11.SunPKCS11`)
  üzerinden; iText 5 ise Maven `itextpdf 5.5.13.3` artifact'ından geliyor.
- POM'dan `tr.com.cs.signer:cssigner` system-scope dependency'si ve
  Spring Boot plugin `<includeSystemScope>` ayarı kaldırıldı.
- `.gitignore`'daki `!lib/**/*.jar` whitelist'i sadeleştirildi (artık `*.jar`
  global yasağı yeterli; tek istisna gerekli değil).

### Fixed

- **Kart algılansa da sertifika listelenmiyordu (race condition)**: Diagnostic
  endpoint çağrısı kartı `connect("*")` ile kısaca açıp kapatıyordu; bazı
  AKIS / ACS sürücüleri kartı bu sırada eksklusif modda tutuyor ve hemen
  ardından gelen `findByTerminalName` çağrısı ATR'i tekrar okuyamıyordu. ATR
  null kalınca `Pkcs11LibraryResolver` candidate üretemiyor, kullanıcı
  `ILLEGAL_STATE: PKCS#11 kütüphanesi çözümlenemedi` hatasını alıyordu. ATR
  cache (5 sn TTL) bu yarışı kapatır; ATR connect başarısızsa son bilinen
  cached değer kullanılır (kart değişimi cache TTL ile zaten kavranır).
- **Kullanıcı geri bildirimi**: Eski `ILLEGAL_STATE` hatası kullanıcıya hangi
  kartın algılandığını veya hangi lib'in arandığını söylemiyordu, sadece
  `mersel.signer.extra-lib-search-paths` parametresini öneriyordu (hangi yolu
  ekleyeceğini bilmiyordu kullanıcı). Yeni `PKCS11_LIBRARY_NOT_FOUND` formatı
  kart adını, lib adını, denenen tüm yolları ve vendor indirme URL'ini içerir;
  kullanıcı tek tıkla doğru sürücüyü indirebilir.
- **Kritik (macOS Big Sur+)**: `SmartCardReaderService.listTerminals()` her
  ortamda boş liste döndürüyordu, çünkü `SignerApplication` `Files.exists`
  ile PCSC framework binary'sini arıyordu; Big Sur'dan itibaren binary dyld
  cache'e taşındığından dosya sisteminde "yok" görünüyor, system property
  set edilmiyor, JDK `pcsclite.1.dylib` arıyor, kart algılanmıyordu. Path
  artık koşulsuz set ediliyor → AKIS / Kamu SM / e-Tugra kartları gerçek
  donanımla doğrulandı.
- **Kart kümülatif cache bug'ı**: `TerminalFactory.getDefault()` JVM yaşam
  boyunca cache'lenir ve sonradan takılan okuyucuları görmez. `listTerminals()`
  artık her çağrıda `TerminalFactory.getInstance("PC/SC", null)` ile fresh
  provider alıyor.
- **Sun PCSC `SCARD_E_NO_READERS_AVAILABLE` tolerans**: Okuyucu yokken Sun
  implementasyonu exception atıyor, eski kod bunu generic hata olarak
  loglarken artık benign no-readers durumu olarak boş listeye çeviriyor.
- **Kritik**: `CertificateChainBuilder` iki konstrüktör (prod + test-friendly)
  sundüğü için Spring constructor autowiring kararsız kaldı ve `padesService`
  → `gibApplicationController` zinciri runtime'da
  `BeanInstantiationException: No default constructor found` ile patlıyordu.
  Public prod ctor'a `@Autowired` eklendi.
- **Regression koruma**: `SignerApplicationContextTest` (`@SpringBootTest(MOCK)`)
  ile tüm controller + service bean'lerinin context'te temiz çözüldüğü CI'da
  doğrulanıyor. Birim testler manuel `new` ile çalıştığından bu tür DI
  bug'larını yakalamıyordu.
- Spring Boot Test'in transitively getirdiği `vaadin:android-json` exclude
  edildi; `org.json.JSONObject` ile classpath ambiguity warning'i kaldırıldı.
- **Kritik**: `pom.xml` parse hatası — XML yorumlarında `--` (çift tire)
  geçtiği için `Non-parseable POM` hatasıyla build hiç başlamıyordu.
- **Kritik**: JaCoCo `prepare-agent` opt-in profile'a alınınca Surefire
  `argLine`'ı `@{argLine}` placeholder'ını resolve edemiyor, "forked VM
  terminated without saying goodbye" ile patlıyordu. `prepare-agent` default
  fazda kalacak şekilde geri alındı, `check` execution'ı `quality` profile'ında.
- **Kritik**: `logback-spring.xml`'de `%mskmsg` converter yanlış pakete
  bağlıydı (`cloud.mersel.*` → doğrusu `io.mersel.dss.agent.api.util.*`);
  uygulama başlangıcında `ClassNotFoundException` ile log'lama sessiz
  bozulmasını engelliyordu.
- `logback-spring.xml`'de logger adı `cloud.mersel` → `io.mersel` (level'in
  hiç uygulanmamasına neden oluyordu).
- `SignerApplication`: macOS PCSC framework yolu, dosya gerçekten varsa
  set ediliyor (defensive).
- `Pkcs11LibraryResolver`: kullanılmayan `sampleCandidates()` yetim metot
  kaldırıldı.
- `RevocationChecker`: kullanılmayan `touchUnusedRefs()` hack + ölü
  import'lar kaldırıldı.
- `XadesService`: kullanılmayan `signXmlDocumentForTest` yetim metot
  kaldırıldı.
- `WebConfig`: Spring Boot default ObjectMapper'ını ezen custom bean
  kaldırıldı (artık `default-property-inclusion: non_null` ayarı çalışıyor).

### Removed

- `src/main/resources/certificates/keystore.jks` — HTTPS connector kaldırıldı.
- `src/main/resources/sentry.properties` — yetim DSN; Sentry dependency
  hiç eklenmemişti.
- `src/main/resources/static/config/*.xml`, `SertifikaDeposu.svt`,
  `lisans.xml` — TÜBİTAK ESYA legacy artefact'ları, kodda referansı yok.
- `HttpConfiguration` — HTTP-only mimaride çift connector gereksiz.

[Unreleased]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.6...HEAD
[1.0.6]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/releases/tag/v1.0.1
