# Changelog

Bu dosyanın formatı [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
standardına dayanır; sürüm numaralandırması
[Semantic Versioning](https://semver.org/) kurallarına uyar.

## [Unreleased]

### Added

- **Platforma özel TEK-DOSYA "tıkla & çalıştır" paketleri** — son kullanıcının
  makinesinde **Java kurulu olması gerekmez**; her paket kendi gömülü JRE 8'ini
  taşır. ZIP açıp script çalıştırma adımı kalktı:
  - **Windows → tek `.exe`** (NSIS self-extracting): kurulum sihirbazı yok;
    çift tıklayınca kendini geçici dizine açıp gömülü `javaw` ile başlar.
  - **macOS → `.dmg`** (içinde imzalı `.app`): çift tık → mount → uygulamaya
    çift tık. İmzasız host'ta ad-hoc "seal" edilir (macOS 14+ "damaged"
    hatasını önler).
  - **Linux → tek `.AppImage`**: `chmod +x` → çift tık. AppImage type2
    runtime + `mksquashfs` ile deterministik üretilir (appimagetool gerekmez).
- **`linux-arm64` hedefi** — Temurin JRE 8 `aarch64` + AppImage `aarch64`
  runtime (ikisi de sha256-pinned). ARM Linux (Apple Silicon üzerinde
  Parallels/UTM, Raspberry Pi vb.) için `.AppImage` üretir.
- **macOS imza & notarization altyapısı**: `etc/branding/entitlements.plist`
  (PKCS#11 dinamik kütüphane yüklemesi için `disable-library-validation`
  dahil). Apple Developer ID secret'ları tanımlıysa CI hardened runtime +
  notarization + stapling uygular; yoksa ad-hoc imza ile güvenli paket çıkar.

### Changed

- **Dağıtım formatı ZIP → tek-dosya çalıştırılabilirlere geçti.** Gömülü-JRE
  paketleri artık `.exe` / `.dmg` / `.AppImage`; gerekli araç (makensis /
  hdiutil / mksquashfs) yoksa güvenli `.zip` fallback'e düşülür.
- **`bundle.yml` CI workflow'u** platforma özel runner'lara taşındı: macOS
  `.dmg` için `macos-14` (codesign/notarytool/stapler yalnız macOS'ta çalışır),
  Windows `.exe` + Linux `.AppImage` için `ubuntu-latest` (apt `nsis` +
  `squashfs-tools`). Tüm JRE indirme linkleri sha256 ile pinli (deterministik,
  tekrarlanabilir build).

## [1.1.3] — 2026-06-03

### Changed

- **Harici (jar-dışı) yapılandırma yüklemesi devre dışı bırakıldı — çalışma
  dizinine bırakılan `application.*` dosyası artık ne okunuyor ne de başlangıcı
  bozabiliyor**: agent, kullanıcı makinesinde çalışan dışa kapalı bir daemon
  olduğundan tüm yapılandırması jar içindeki gömülü `application.yml`'den gelir.
  Buna rağmen Spring Boot, varsayılan olarak çalışma dizinini ve `config/` alt
  dizinlerini (`file:./`, `file:./config/`, `file:./config/*/`) `application.*`
  dosyaları için tarıyordu.

  **Kök neden / belirti**: çalışma dizininde (Windows'ta CWD; servis/görev
  olarak çalıştırıldığında çoğu zaman `C:\Windows\System32`) adı `application.xml`
  olan sıradan bir XML bulunduğunda Spring bunu **Java Properties XML** sanıp
  `Properties.loadFromXML()` ile parse etmeye çalışıyor; dosyada zorunlu DOCTYPE
  bulunmadığı için uygulama daha environment hazırlanırken devriliyordu:

  ```
  IllegalStateException: IO error on loading imports from
    [optional:file:./;optional:file:./config/;optional:file:./config/*/]
    └─ InvalidPropertiesFormatException: An XML properties document must
       contain the DOCTYPE declaration as defined by java.util.Properties.
  ```

  **Çözüm**: `SignerApplication.main`, `SpringApplication.run` çağrılmadan önce
  `setDefaultProperties` ile `spring.config.location=optional:classpath:/`
  veriyor. Bu, varsayılan dosya tarama yollarını **tamamen** kaldırır; config
  yalnızca jar içindeki classpath kaynağından (`application.yml`) okunur.
  Değer, config-dosyası tarama mantığı (`ConfigDataEnvironmentPostProcessor`)
  çalışmadan önce environment'a eklendiği için zamanlama doğrudur ve OS'tan
  bağımsız çalışır (macOS'ta, bozuk `application.xml` + `config/application.xml`
  içeren bir dizinden başlatılarak doğrulandı). Çalışma zamanı override'ları
  config dosyası değil ayrı property source oldukları için etkilenmez: ortam
  değişkenleri (`MERSEL_AGENT_*`) ve komut satırı argümanları aynen çalışır.

## [1.1.2] — 2026-06-03

### Fixed

- **Birden çok akıllı kart sürücüsü kuruluyken PIN doğrulama ve imzalamanın
  `503 PKCS11_UNAVAILABLE` ("PKCS11 not found") ile patlaması — SunPKCS11
  default slot seçimi tuzağı**: bir makinede birden çok PKCS#11 sürücüsü /
  sanal okuyucu aktifken (örn. Aladdin VR Handler, Rainbow iKey Virtual
  Reader) doğru kart seçilmesine ve kart listesinde tek kart görünmesine
  rağmen `/smartcard/pin/validate` ve XAdES imzalama akışları şu hata
  zinciriyle başarısız oluyordu:

  ```
  NoSuchAlgorithmException: no such algorithm: PKCS11 for provider SunPKCS11-…
    └─ KeyStoreException: PKCS11 not found
        └─ Pkcs11LibraryException: PKCS#11 keystore yüklenemedi: PKCS11 not found
  ```

  Sürücüler aygıt yöneticisinden devre dışı bırakılınca sorun kayboluyordu.

  **Kök neden**: `SunPKCS11`, config'inde `slot` / `slotListIndex`
  belirtilmezse default olarak `C_GetSlotList()`'in **0. slot'unu** hedefler.
  Kartın PKCS#11 kütüphanesi PC/SC üzerinden sistemdeki **tüm okuyucuları**
  (token takılı olmayan boş sanal okuyucular dahil) enumerate eder. Boş bir
  sanal okuyucu liste sırasında slot 0'a denk geldiğinde SunPKCS11 token
  bulamaz, provider hiçbir algoritma register etmez ve
  `KeyStore.getInstance("PKCS11", provider)` `NoSuchAlgorithmException` atar.
  Sürücüleri kapatmak phantom okuyucuları listeden düşürdüğü için gerçek kart
  slot 0'a kayıyor ve akış çalışıyordu.

  **Çözüm**: SunPKCS11'i artık token-present gerçek slot'a kilitliyoruz.
  `IaikPkcs11Signer#findTokenPresentSlotId` xipki cache'li `PKCS11Module`
  üzerinden `C_GetSlotList(tokenPresent=true)` ile kartın gerçek `slotID`'sini
  tespit eder (ek `C_Initialize` maliyeti yok; modül lib başına JVM-ömrü tek
  instance). Bu değer iki yola da besleniyor:

  | İmza/PIN yolu                | Mekanizma                                            |
  |------------------------------|------------------------------------------------------|
  | `Pkcs11Session.open` (PIN + PAdES) | SunPKCS11 config'ine `slot = <id>` satırı yazılır |
  | `XadesService.doXadesBesSign` (xades4j) | `PKCS11KeyStoreKeyingDataProvider(…, slotId, …)` |

  IAIK native imza yolu (`IaikPkcs11Signer.open`) zaten `getSlotList(true)[0]`
  kullandığı için bu tuzaktan etkilenmiyordu; üç yol artık aynı kartı seçer.
  Slot tespiti **best-effort**: native lib yüklenemez / slot okunamazsa eski
  davranışa (slot satırsız config) düşülür, hiçbir akış devrilmez.

- **Aynı anda iki gerçek kart takılıyken yanlış kartın seçilebilmesi —
  `terminalName` → slot eşlemesi**: yukarıdaki slot seçimi başlangıçta "ilk
  token-present slot"u (`getSlotList(true)[0]`) seçiyordu; bu boş okuyuculara
  karşı yeterli ama aynı kütüphanede **iki gerçek kart** varken kullanıcının
  arayüzde seçtiği kart yerine enumerate sırasındaki ilk kartı kullanabiliyordu.

  **Çözüm**: `IaikPkcs11Signer.findTokenPresentSlotId` ve `IaikPkcs11Signer.open`
  artık seçilen PC/SC okuyucu adını (`terminalName`) alır ve birden çok
  token-present slot varsa `SlotInfo.getSlotDescription()` (PKCS#11 v2.40 §3.2:
  PC/SC tabanlı kütüphanelerde okuyucu adıdır) ile eşleşen slot'u seçer
  (normalize: trim + tek boşluk + küçük harf; tam/contains eşleşme). Eşleşme
  yoksa ilk token-present slot'a düşülür ve **WARN** loglanır. `terminalName`
  tüm imza/PIN yollarına geçirilir: `Pkcs11Session.open(lib, pin, terminalName)`
  (PIN doğrulama + PAdES + counter-signature), xades4j `slotId` ve iki IAIK
  native imza yolu. SoftHSM ile iki tokenlı senaryoda doğrulandı (her okuyucu
  adı kendi slotID'sine; eşleşmeyen ad ilke düşüyor).

- **Aynı anda iki gerçek kart takılıyken `/smartcard/certificate` listesinde
  her iki kartın sertifikalarının birden gelmesi — listeleme yolunda slot
  daraltma eksikliği**: kullanıcı bir okuyucu adı (`terminalName`) gönderip o
  karttaki sertifikaları beklerken, listeye her iki karttaki sertifikalar
  birden dönüyordu (örn. bir okuyucuda NES Bulut, bir başkasında Mali Mühür;
  her ikisi de AKİS/Mali Mühür olduğu için aynı `libakisp11` kütüphanesinden
  okunuyordu).

  **Kök neden**: PIN'siz public sertifika okuyucusu
  (`Pkcs11PublicCertificateReader.read`) `C_GetSlotList(tokenPresent=true)`'in
  döndürdüğü **tüm** token-present slot'ları dolaşıp hepsinin sertifikalarını
  topluyordu. `terminalName` yalnızca PKCS#11 **kütüphanesini** çözmek için
  kullanılıyor, slot'u daraltmıyordu; tek kütüphane birden çok okuyucudaki
  kartları enumerate ettiği için hepsi listeye sızıyordu.

  **Çözüm**: imza/PIN yolundaki aynı slot eşleştirme mantığı listeye de
  taşındı. `CertificateListingService` artık
  `IaikPkcs11Signer.matchSlotIdByTerminal(lib, terminalName)` ile seçilen
  okuyucunun `slotID`'sini (yalnız kesin eşleşmede) çözer ve
  `Pkcs11PublicCertificateReader.read(lib, OptionalLong)` overload'una geçirir;
  okuyucu yalnız o slot'u okur. Eşleşme yoksa (`terminalName` boş ya da vendor
  slot açıklaması okuyucu adıyla uyuşmuyor) **tüm slotlar okunur** — kullanıcının
  kartını yanlışlıkla gizlememek için güvenli fallback. slotID değeri
  kütüphane-global (`CK_SLOT_ID`) olduğundan xipki ile tespit edilen değer,
  reader'ın sun reflection katmanının enumerate ettiği slotID'lerle birebir
  eşleşir.

## [1.1.1] — 2026-06-02

## [1.1.0] — 2026-06-01

### Fixed

- **XAdES çıktısında `<ds:X509Certificate>` ve `<ds:SignatureValue>` text
  node'larında `&#13;` (CR entity) görsel kirliliği — Santuario CRLF wrap +
  Transformer round-trip artefaktı**: agent'ın ürettiği XAdES dosyasında
  Base64-encoded sertifika ve imza değerlerinin her satır sonunda `&#13;`
  entity'si görünüyordu (örn. `MIIDhTCCAm2gAwIBAg...&#13;...&#13;...`). GİB
  entegratörlerinin görsel önizleme akışında ve manual XML inspection'da
  standart XAdES çıktısından ayırt edilebilir bir kirlilik yaratıyordu.

  **Kök neden**: Apache Santuario `KeyInfo.add(X509Certificate)` ve
  `<ds:SignatureValue>` text node'larını Apache Commons Codec MIME mode ile
  Base64'e çevirir → çıktı 76 karakterde **`\r\n`** ile bölünür (RFC 2045 /
  MIME geleneği). Sonra DOM `Transformer` XML 1.0 spec gereği literal CR
  karakterini text içeriğinde **`&#13;`** olarak entity-encode eder
  (round-trip korunsun diye — XML parser CR'leri normalize edip yutmasın).
  Sonuç: her 76 karakterde bir `&#13;` entity'si.

  **Neden global property çözüm değil**:
  `-Dorg.apache.xml.security.ignoreLineBreaks=true` Santuario sınıfı
  yüklenmeden ÖNCE set edilmek zorunda; agent boot'unda Spring autoconfigure
  Santuario'yu çoktan yüklemiş oluyor ve property runtime'da yan etkisiz
  kalıyor. Ayrıca production XAdES davranışını global olarak değiştirmek
  başka path'leri (CAdES detached, counter-signature parent c14n) riske
  sokar.

  **Çözüm**: imza üretildikten sonra ilgili subtree içindeki
  `<ds:X509Certificate>` ve `<ds:SignatureValue>` text node'larını standart
  76-char LF wrap'ine normalize ediyoruz (`XadesService#rewrapBase64InSignatureSubtree`).
  Üç imza yolunda da çağrı yapılır:

  | İmza yolu                | Subtree                       | Etkilenen elementler        |
  |--------------------------|-------------------------------|------------------------------|
  | `doXadesBesSign` (xades4j) | document root                 | X509Certificate + SignatureValue |
  | `doXadesBesSignNative` (IAIK) | document root              | X509Certificate (+ SV no-op) |
  | `doCounterSignature`     | yalnız `<xades:CounterSignature>` | counter-sig X509 + SV        |

  Bu iki node canonicalization / digest girişi <em>değil</em> (sadece Base64
  decode edilip ham byte'lara çevrilirler; Base64 decoder whitespace'i —
  space/tab/CR/LF — yutar) → imza geçerliliği etkilenmez. Counter-signature
  yolunda parent imzanın text node'larına dokunulmaz (parent c14n parity
  korunur).

  **Etkilenen bileşenler**: `XadesService.java` — yeni package-private
  static helper'lar: `BASE64_LINE_WIDTH=76` sabiti, `wrapBase64(String)`
  (standalone LF wrapper, library bağımlılığı yok),
  `rewrapBase64InSignatureSubtree(Element)` (X509Certificate + SignatureValue
  normalize'ı), `rewrapBase64ForAll(Element, ns, localName)` (NodeList
  iterator). Üç imza akışının `serialise()` çağrısından önce subtree rewrap
  ekleyen 3 satırlık değişiklik.

  **Regresyon koruması**: `XadesServiceBase64WrapTest` (8 senaryo) —
  `wrapBase64` happy path (kısa string passthrough, tam 76 char no-op,
  77+ char LF wrap, 1500 char gerçekçi cert boyutu, roundtrip whitespace
  stripping), `rewrapBase64InSignatureSubtree` Santuario CRLF → LF
  normalize, DOM Transformer çıktısında `&#13;` / `&#xD;` entity'sinin
  kalmadığı, null root no-op davranışı, tek-satır temiz Base64'ün korunması.
  Aynı çözüm server projesinde `TestUserCounterSignatureService#rewrapBase64InSubtree`
  olarak mevcut; regresyon kardeşi `TestUserCounterSignatureCleanOutputTest`.

- **`CKR_CRYPTOKI_ALREADY_INITIALIZED` IAIK fallback NULL-args yolundan
  leak — hibrit SunPKCS11+xipki paritesi**: AKİS macOS / Linux'ta sertifika
  listeleme akışı (`Pkcs11PublicCertificateReader`, SunPKCS11 wrapper'ı
  üzerinden) `.dylib`'i ilk kez init ettikten sonra IAIK fallback'e düşen
  imza akışı `Pkcs11LibraryException: PKCS#11 modülü initialize edilemedi
  (/usr/local/lib/libakisp11.dylib): CKR_CRYPTOKI_ALREADY_INITIALIZED`
  hatasıyla durup hiç imza atamıyordu — IAIK fallback yolu sahada hiçbir
  zaman çalışmıyordu.

  **Kök neden**: PKCS#11 v2.40 §11.4 — `C_Initialize` process-global state
  taşır, ref-count yok. Agent hibrit yapısında SunPKCS11 (cert listeleme +
  xades4j) ve xipki (IAIK fallback) aynı `.dylib`'i paylaşıyor; biri init
  ettikten sonra diğeri ALREADY_INITIALIZED alıyor. `IaikPkcs11Signer#initializeIdempotent`
  standart yolda bu kodu yakalıyordu ama AKİS yolundan ARGS_BAD nedeniyle
  düşülen NULL-args fallback'i ALREADY_INITIALIZED'i ölümcül hata sayıp
  re-throw ediyordu (`initializeWithNullArgs` `InvocationTargetException`
  altındaki IAIK PKCS11Exception'ı koşulsuzca high-level xipki exception'a
  sarıyordu). Server projesi `IaikPkcs11Module` aynı kalıbı taşıyor ama
  HSM tarafında SunPKCS11 olmadığı için hiç tetiklenmemişti.

  **Çözüm**: `initializeWithNullArgs` artık `InitOwnership` (FRESH / SHARED)
  döner ve NULL-args yolunda ALREADY_INITIALIZED'i `InitOwnership.SHARED`
  olarak sınıflandırıp paylaşımlı Cryptoki state'iyle devam eder; finalize
  sahipliği eski sahibine bırakılır. Karar matrisi:

  | Senaryo                                      | Sonuç                          |
  |----------------------------------------------|--------------------------------|
  | `forceNullInitArgs=true`                     | NULL-args, singleThreaded=TRUE |
  | `module.initialize()` OK                     | FRESH, multi-thread            |
  | `module.initialize()` ALREADY_INITIALIZED    | SHARED no-op, multi-thread     |
  | `module.initialize()` ARGS_BAD (AKİS)        | NULL-args fallback             |
  | NULL-args OK                                 | FRESH                          |
  | NULL-args ALREADY_INITIALIZED                | SHARED no-op (sahada en sık)   |
  | Diğer CKR_*                                  | Caller'a propagate             |

  Standart yolda da ALREADY_INITIALIZED dönerse `populateModuleInfoAndVendor`
  reflection ile `moduleInfo` + `initVendor()` populate eder; aksi halde
  xipki vendor behaviours (EC point fix, ECDSA signature x962, vb.) sessizce
  devre dışı kalırdı. Vendor behaviours eksikliği RSA/ECDSA imzayı bozmaz
  ama nadir EC eğrilerinde fallback davranışlarını eksik bırakır.

  **Etkilenen bileşenler**: `IaikPkcs11Signer.java` — `initializeIdempotent`
  yeniden yazıldı; `initializeWithNullArgs` artık `InitOwnership` döner;
  yeni helper'lar: `classifyNativeInitFailure` (package-private,
  unit-test'lenebilir saf classifier), `populateModuleInfoAndVendor`
  (reflection-tabanlı moduleInfo + initVendor populate). Yeni
  `InitOwnership` enum (FRESH / SHARED) ownership semantiğini açıkça
  ifade eder.

  **Regresyon koruması**: `IaikPkcs11SignerInitTest` `classifyNativeInitFailure`
  contract'ını 5 senaryoda doğrular (ALREADY_INITIALIZED → SHARED, diğer
  CKR_* → xipki PKCS11Exception, IAIK olmayan cause → IllegalStateException).
  Mevcut `IaikPkcs11SignerErrorMatchTest` etkilenmedi.

- **TÜBİTAK AKİS macOS / Linux sürücüsünde `CKR_ARGUMENTS_BAD` —
  ipkcs11wrapper `module.initialize()` çağrısında AKİS uyumluluk fallback'i
  (server projesi `IaikPkcs11Module#initializeWithNullArgs` kalıbının agent
  porte hali)**: `libakisp11.dylib` / `libakisp11.so` xipki'nin standart
  `C_Initialize(CK_C_INITIALIZE_ARGS{flags=CKF_OS_LOCKING_OK})` çağrısını
  reddedip `CKR_ARGUMENTS_BAD` döner — TÜBİTAK BİLGEM sürücüsünün macOS / Linux
  portunda yer alan klasik bir bug; Windows portunda yok. Sahada NES Bulut
  dual-key kartı IAIK fallback'e düştüğünde `Pkcs11LibraryException: PKCS#11
  modülü initialize edilemedi (/usr/local/lib/libakisp11.dylib):
  CKR_ARGUMENTS_BAD` hatası ile imza akışı duruyordu.

  **Kök neden**: AKİS sürücüsü `C_Initialize` çağrısında yalnız {@code
  C_Initialize(NULL)} formunu kabul ediyor — `CKF_OS_LOCKING_OK` flag'i set
  edilirse arg validation reddeder. xipki `PKCS11Module.initialize()` default
  davranışta `CKF_OS_LOCKING_OK` set ettiği için bu kombinasyon AKİS macOS /
  Linux üzerinde her zaman patlar. Kardeş proje
  `mersel-dss-server-signer-java` aynı patolojiyi `IaikPkcs11Module#initializeIdempotent`
  + `initializeWithNullArgs` kalıbıyla çözmüştü.

  **Çözüm**: `IaikPkcs11Signer#openOrGetModule` artık server projesinin
  initialize-idempotent kalıbını takip ediyor:

  1. Standart `module.initialize()` denenir.
  2. `CKR_CRYPTOKI_ALREADY_INITIALIZED` → modül başka bir bileşen tarafından
     init edilmiş; ownership=false, mevcut state kullanılır (cache'lenir).
  3. `CKR_ARGUMENTS_BAD` → AKİS macOS / Linux NULL-args fallback'i devreye
     girer: xipki `PKCS11Module` private `pkcs11` field'ı reflection ile alınır,
     altındaki IAIK `PKCS11Implementation.C_Initialize(null, true)` (NULL args,
     single-threaded) doğrudan çağrılır. Best-effort `moduleInfo` populate +
     `initVendor` çağrıları reflection ile yapılır. PKCS#11 v2.40 §5.4 gereği
     NULL-args mode kütüphaneyi thread-unsafe sayar — caller {@link PKCS11Token}
     oluştururken `numSessions=1` verir (akıllı kart donanımı zaten paralel
     oturum kaldırmaz, görünür performans kaybı yok).
  4. Diğer `CKR_*` hatalar üst katmana yükselir (standart sınıflandırıcı
     Pkcs11LibraryException döner).

  **Operatör escape hatch**: `PKCS11_NULL_INIT_ARGS=true` env var veya
  `-Dpkcs11.nullInitArgs=true` JVM property → standart `C_Initialize`
  denenmeden doğrudan NULL-args yoluna gider. Auto-detect zaten devrede; bu
  bayrak operatöre "ben biliyorum, trial-and-error'u atla" diyebilmesi için.
  Aynı isim ve davranışla server projesinde
  `SignatureServiceConfiguration#pkcs11NullInitArgs` olarak mevcut.

  **Etkilenen bileşenler**: `IaikPkcs11Signer.java` — `MODULE_CACHE`
  artık `PKCS11Module` yerine `ModuleEntry` (module + owned + singleThreaded)
  taşıyor. Yeni metotlar: `initializeIdempotent`, `initializeWithNullArgs`,
  `readForceNullInitArgsFlag`. Yeni nested classes: `ModuleEntry`,
  `InitOutcome`. Constructor `IaikPkcs11Signer(module, token, singleThreaded)`
  imzası genişledi.

  Reflection ile çağrılan native sınıflar: `iaik.pkcs.pkcs11.wrapper.PKCS11Implementation`
  (xipki ipkcs11wrapper JAR'ı içinde bundled, `iaik.pkcs.pkcs11.wrapper.PKCS11Exception`
  + `CK_INFO` aynı şekilde). xipki paketlerinin internal sınıflarına bağımlılık
  oluştu — gelecek sürümlerde (ipkcs11wrapper 1.1+) bu private API değişebilir;
  yeni sürüme geçişte `PKCS11Module.pkcs11` field adı ve `C_Initialize(Object,
  boolean)` imzası doğrulanmalı.

- **SunPKCS11 `CKR_USER_NOT_LOGGED_IN` regresyonu — `Signature.initSign()`
  aşamasında C_SignInit'in login state'siz yeni bir P11Session ile
  başlatılması**: `POST /xades/sign` PIN doğru geçirildiği halde
  `Initialization failed | root: CKR_USER_NOT_LOGGED_IN` ile patlıyordu. Trace
  cause zinciri: `xades4j.production.SignerBES.sign → xmlsec
  SignatureECDSA.engineInitSign → SunPKCS11 P11Signature.initialize →
  C_SignInit → CKR_USER_NOT_LOGGED_IN`. Kullanıcı `dto.getPin()`'i geçtiği,
  `KeyStore.load(null, pin)` çağrısı da başarılı şekilde tamamlandığı halde
  imza başlangıcında token "henüz login olmamış" hatası dönüyordu.

  **Kök neden**: SunPKCS11'in `P11SessionManager`'ı KeyStore.load çağrısında
  açtığı P11Session'da `C_Login` yapar; ancak `Signature.initSign()`
  çağrıldığında **yeni bir P11Session** alır. Cryptoki spec'i login state'in
  app-wide olmasını öngörse de bazı kart sürücüleri (AKIS, SafeSign, bazı
  Kamu SM kartları) bunu session-scoped uygular — yeni açılan session
  unauthenticated başlar. `KeyStore.load` ile yapılan implicit login bu
  davranışta sonraki session'lara devredilmez.

  **Çözüm**: SunPKCS11 `AuthProvider.login(subject, callbackHandler)`
  çağrısını explicit olarak ekledik. Bu çağrı, KeyStore.load'un session-bound
  login'inin aksine, login state'i Provider objesinin yaşam süresine bağlar
  (`Security.removeProvider`'a kadar). P11SessionManager bu noktadan sonra
  açacağı tüm session'ları "authenticated" işaretler; `C_SignInit` artık
  CKR_USER_NOT_LOGGED_IN almaz. Spec'e göre idempotent: zaten login ise
  SunPKCS11 sessizce no-op yapar.

  **Etkilenen kod yolları**:
  - `XadesService#doXadesBesSign`: `keyingProvider.getSigningCertificateChain()`
    çağrısı xades4j'in `PKCS11KeyStoreKeyingDataProvider`'ını lazy init eder
    (provider Security registry'sine kayıt olur). Bu çağrıdan hemen sonra,
    `signer.sign(...)` öncesinde, `Security.getProvider(jcaProviderName)` ile
    alınan provider'a `AuthProvider.login()` çakılır. Provider AuthProvider
    değilse (mock test ortamı) sessizce atlanır; LoginException olursa
    `Pkcs11Session.mapKeyStoreLoadFailure(e)` ile yapılandırılır (PIN incorrect
    vs locked vs unknown classifier).
  - `Pkcs11Session#open`: counter-signature ve diğer custom kullanım yolları
    için aynı pattern. `KeyStore.load` başarılı olduktan sonra explicit
    `AuthProvider.login` çağrılır. `close()` zaten `AuthProvider.logout`
    yapıyor — login state Provider yaşam süresine bağlanır ve close'da temiz
    keser. `mapKeyStoreLoadFailure` API'si package-private'tan public'e
    yükseldi (XadesService.doXadesBesSign'dan da çağrılıyor).

  Bu fix IAIK fallback path'inden bağımsızdır; CKR_USER_NOT_LOGGED_IN
  CKA_ID collision değildir. xades4j path'i AKIS / SafeSign kartlarında
  artık doğru çalışır; CKA_ID collision'ı olan NES Bulut dual-key kartları
  ise hâlâ IAIK PKCS#11 wrapper fallback'ine düşer.

  **Sahada doğrulama sonrası ek tedbir** — `AuthProvider.login()` çağrısı
  bazı AKİS firmware versiyonlarında `C_Login` yapsa da P11SessionManager
  sonradan açtığı session'lara login state'i devretmiyor (sürücü
  session-scoped davranıyor, app-wide spec'i ihlal). Bu sürücü-derinliği
  bug'ı driver güncellemesi olmadan SunPKCS11 katmanından çözülemiyor.
  Bu nedenle `CKR_USER_NOT_LOGGED_IN` hatası `IaikPkcs11Signer.requiresIaikFallback`
  tetikleyici listesine eklendi: xades4j path bu hatayı verdiğinde sistem
  otomatik olarak IAIK PKCS#11 wrapper fallback'ine düşer. IAIK kendi
  C_Login + sign akışını yönetir, SunPKCS11 P11SessionManager hiç
  dokunulmaz; bu yol AKİS macOS / Linux sürücüsünde stabil. Trigger ismi
  `isDuplicateCkaIdError` → `requiresIaikFallback` olarak genelleştirildi
  (mevcut method `@Deprecated` alias olarak korundu). Fallback log mesajı
  hangi pattern tetiklediği bilgisini içerir (`CKR_USER_NOT_LOGGED_IN
  (session-scoped login state, AKİS / SafeSign tipik)` vs `CKA_ID collision
  (NES Bulut dual-key SIGN0+SIGN1)`). Tanılama bağlamı
  `fallbackStrategy=iaik-pkcs11-sunpkcs11-bypass` (önceki ad
  `native-pkcs11-dual-key-bypass` artık tek pattern'a özel olmadığı için
  generic ada kavuştu).

- **NES Bulut / Kamu SM dual-key (SIGN0 imzalama + SIGN1 anahtar uzlaşımı)
  kartlarında `invalid KeyStore state: found 2 private keys sharing CKA_ID`
  imzalama hatası**: NES Bulut Yazılım gibi tek tüzel kişiye iki imzacı
  sertifika (DIGITAL_SIGNATURE ve KEY_AGREEMENT keyUsage'ları, EC P-384) tahsis
  eden Mali Mühür kartlarında sürücü her iki private key'i **aynı CKA_ID
  attribute'ı ile** yazıyor. OpenJDK 1.8 SunPKCS11
  `sun.security.pkcs11.P11KeyStore#mapPrivateKeys()` her iki anahtarı tek alias
  altında map etmeye çalıştığında — alias map collision'ı — uniqueness check
  fail ediyor ve `KeyStoreException: invalid KeyStore state: found 2 private
  keys sharing CKA_ID 0x...` fırlatıyor. Hata cert seçim mantığına gelinemeden
  `KeyStore.load()` aşamasında atıldığı için kullanıcının `certificateId`
  parametresiyle "doğru cert"i seçtiği akış dahi devreye giremiyor; xades4j
  cause zinciri `SignatureOperationException → UnexpectedJCAException →
  KeyStoreException` olarak yığılıyor.

  Üstelik bu davranış JDK 9+'da relaxed (`P11KeyStore` aynı CKA_ID'li
  private/public key pair'lerine tolere edebilir hale geldi) ama JDK 1.8'de
  fix yok; JDK 1.8'i bırakamadığımız (PCSC + masaüstü uyumluluk) sürece
  workaround zorunlu.

  **Mimari karar — IAIK PKCS#11 Wrapper'a geçiş**: Kardeş proje
  `mersel-dss-server-signer-java` bu problemi `8c39919` commit'inde IAIK PKCS#11
  wrapper'ı (`org.xipki:ipkcs11wrapper`, IAIK Graz 1.6.8 kod tabanından
  türetilen Apache 2.0 forku) entegre ederek çözdü. Agent ilk turda ek
  bağımlılıktan kaçınmak için `sun.security.pkcs11.wrapper.PKCS11` low-level
  JNI sarmalayıcısına reflection ile inen bir prototip denedi; ancak şu
  gerekçelerle IAIK yoluna geçildi:

  1. **JDK 17+ uyumu**: `sun.security.pkcs11.wrapper.*` Jigsaw modüller
     sistemiyle `jdk.crypto.cryptoki` modülünün dışına kapatıldı. Reflection
     erişimi JDK 17+'da `--add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED`
     JVM argümanı gerektirir; jpackage launcher boyu sürekli güncellenmesi
     gereken bir bağlılık. IAIK wrapper kendi JNI bridge'ini (jar içinde
     bundled `natives/{unix,windows}/...libpkcs11wrapper`) kullanır,
     JDK internal'ına HİÇ ihtiyaç duymaz; `add-exports` gerekmez.
  2. **Server-agent paritesi**: HSM (server) ve smart-card (agent) imzalama
     akışları artık aynı PKCS#11 katmanına iner. Aynı kod kalıbı
     (`open → findSigner → sign → close`), aynı hata sınıflandırma şeması,
     aynı tanılama vokabüleri.
  3. **API kalitesi**: Reflection prototipi `CK_ATTRIBUTE[]` template
     kurulumlarını, `CK_MECHANISM(long)` ctor binding'lerini, manuel
     `Object.getClass()` introspection'larını gerektiriyordu. IAIK'in
     `AttributeVector.newPrivateKey().id(bytes).sign(true)` fluent API'si
     aynı işi 50+ satır daha az kodla yapıyor; mock'lanması da kolay.
  4. **Maliyet**: JAR ~2.3 MB. Tüm platform JNI binary'leri (macOS universal,
     Linux x86_64/arm/arm64, Windows x86_64/x86) zaten bundled — jpackage
     installer'a ek native dosya kopyalanmıyor. Shaded JAR büyümesi %5-8;
     ihmal edilebilir.

  Reflection katmanı (`Pkcs11Reflection.java`) PIN'siz cert listing için
  korunuyor — listing akışı patolojik değil; SunPKCS11 P11KeyStore'a hiç
  dokunmadığı (`C_FindObjects` doğrudan native) için CKA_ID collision'dan
  etkilenmiyor.

  **Yeni bileşenler**:
  - `IaikPkcs11Signer.java`: `org.xipki:ipkcs11wrapper` üzerine inşa edilen
    AutoCloseable PKCS#11 imzalama istemcisi. `open(libPath, pin)` →
    `PKCS11Module.getInstance(libPath).initialize()` (lib path → module JVM
    yaşam boyu cache'lenir; `CKR_CRYPTOKI_ALREADY_INITIALIZED` toleranslı) +
    `new PKCS11Token(token, readOnly=false, pin)` (ctor C_Login dahil eder),
    `findSigningKey(certId)` → `AttributeVector.newX509Certificate()` template
    ile cert tarama + identifier match (CKA_LABEL / CKA_ID hex / X.509 serial /
    SHA-1 thumbprint), `AttributeVector.newPrivateKey().id(certIdBytes)`
    template'iyle private key tarama + aynı CKA_ID üzerindeki birden çok key'i
    `CKA_KEY_TYPE` (cert public key tipine eşle) + `CKA_SIGN=TRUE`
    filtreleriyle ayrıştırma (NES Bulut SIGN0 doğru, SIGN1 elenir),
    `sign(key, pkcs11Mechanism, data)` → `PKCS11Token#sign(Mechanism, handle,
    data)`. Static yardımcı `isDuplicateCkaIdError(Throwable)` cause zincirini
    cycle-safe (LinkedHashMap ile loop detection) gezerek "invalid KeyStore
    state" + "CKA_ID" pattern'ini eşleştirip fallback dispatch'i tetikler.
    JVM shutdown hook tüm cached `PKCS11Module`'leri toplu finalize eder.
  - `XadesService#doXadesBesSignNative`: SunPKCS11 P11KeyStore'a hiç dokunmayan
    manuel XAdES-BES enveloped imza akışı. Apache Santuario 2.x'in
    `org.apache.xml.security.signature.XMLSignature` low-level API'sini
    kullanarak DOM iskeletini kurar (Reference[0] = URI="" + Enveloped
    transform, Reference[1] = #SignedProperties + c14n, KeyInfo =
    `<ds:X509Data>` + `<ds:KeyValue>` cert public key tipine göre
    RSAKeyValue/ECKeyValue dispatch, Object =
    QualifyingProperties/SignedProperties:SigningTime+SigningCertificate),
    `SignedInfo.generateDigestValues()` ile Reference DigestValue'ları
    PrivateKey gerektirmeden yazılım tarafında hesaplar,
    `SignedInfo.getCanonicalizedOctetStream()` ile canonical bytes'ı alır,
    software SHA-256 (RSA) / SHA-384 (EC) hash eder. RSA için EMSA-PKCS1-v1_5
    DigestInfo (RFC 8017 §9.2) prefix'i öne ekleyip `CKM_RSA_PKCS` ile,
    EC için ham hash'i `CKM_ECDSA` ile IAIK üzerinden imzalar. Ham byte'lar
    Base64'lenip `<ds:SignatureValue>` text content'ine inject edilir.
    Tanılama bağlamı `fallbackStrategy=native-pkcs11-dual-key-bypass`,
    `resolvedJcaSignature=RAW-RSA-NATIVE|RAW-ECDSA-NATIVE`,
    `resolvedPkcs11Mechanism=CKM_RSA_PKCS|CKM_ECDSA` ve "SunPKCS11 P11KeyStore
    dual-key CKA_ID çakışması tespit edildi; native PKCS#11 imza yoluna
    düşüldü." uyarısı ile zenginleştirilir.

  **Dispatch logic**: `XadesService#signXmlDocument` try/catch chain xades4j
  path'in attığı her hatayı `IaikPkcs11Signer.isDuplicateCkaIdError(e)` ile
  test eder; eşleşirse `doXadesBesSignNativeWithDiag` sarmalayıcısı üzerinden
  IAIK path'e geçer. IAIK path da çuvallarsa hem orijinal xades4j hatasını
  `addSuppressed` ile koruyup hem IAIK cause'u primary olarak raporlar
  (destek logları kök neden zincirini görsün diye). Pattern matching CKA_ID
  haricindeki hatalarda (PIN incorrect, EC parameters, mekanizma uyumsuzluğu)
  asla tetiklenmez — bu durumlar mevcut tek-yol akışlarıyla doğru
  yönlendirilir.

  Counter-signature akışı (`signHrXmlCounterSignature`) henüz IAIK path'e
  taşınmadı; mevcut `<ds:Signature>`'a injection mantığı XAdES-BES enveloped
  imzadan farklı (existing `<ds:SignatureValue>` reference'lanır, başka
  bir signature element ağacına `<xades:CounterSignature>` enjekte edilir) ve
  ayrı bir refactor gerektiriyor. Bu kart tipi için counter-signature
  çağrısında erken `SIGNATURE_FAILED` ile açıklayıcı hata mesajı +
  remediation: kullanıcı `/xades/sign` enveloped sign endpoint'ini
  kullanmalı.

  **Bağımlılık**: `pom.xml` `org.xipki:ipkcs11wrapper:1.0.9` (Tem 2024, son
  release; commit Kas 2024'e kadar aktif). Lisans: xipki kod Apache 2.0;
  orijinal IAIK Graz kodu 5-clause BSD-tipi (ticari kullanım serbest, ürünün
  açıklamalarında "This product includes software developed by IAIK of Graz
  University of Technology." attribution gerekiyor — `LICENSE` / `NOTICE`
  dosyalarına eklenecek).

  Test matrisi:
  - `IaikPkcs11SignerErrorMatchTest` (7 test): SunPKCS11 hata mesajı
    pattern'ini cause zinciri varyasyonlarıyla (KeyStoreException doğrudan,
    SignerException wrap, çoklu özel sayı varyasyonları "3 PRIVATE KEYS
    SHARING") doğrular; PIN/EC hatalarında yanlış pozitif vermez; self-cause
    döngülerine karşı cycle-safe.
  - `XadesServiceNativeFallbackTest` (6 test): `rsaDigestInfo()` PKCS#1 v1.5
    EMSA prefix byte'larını SHA-256 / SHA-384 için RFC 8017 §9.2 ile
    karşılaştırır, `estimateKeySizeBits()` RSA modül / EC field bit'lerini
    Mockito ile RSA-2048 / EC P-384 senaryolarında kanıtlar.
  - `XadesServiceDispatchTest` (3 test): `signXmlDocument` Mockito spy ile
    xades4j patladığında IAIK path'e döndüğünü, ilgisiz hatada
    (CKR_PIN_INCORRECT) IAIK'in çağrılmadığını, IAIK de patladığında
    kullanıcıya iki katmanlı hata mesajı + suppressed orijinal cause
    raporladığını kilitler.

- **RSA / EC imzacı sertifikalarda `ds:KeyInfo` zenginleştirmesi (XAdES-BES
  TÜBİTAK kılavuzu uyumu)**: Sahada hem RSA-2048 hem EC P-384 (NIST secp384r1)
  imzacı sertifikaları yaygın; ancak xades4j default'unda
  `BasicSignatureOptions.includePublicKey=false` olduğu için `ds:KeyInfo`
  yalnız `<ds:X509Data><ds:X509Certificate>` içeriyordu. TÜBİTAK XAdES
  uygulama kılavuzu KeyInfo'nun ek olarak `<ds:KeyValue>` blogu da içermesini
  bekler. **Çözüm**: hem ana xades4j akışı (`XadesService#doXadesBesSign`) hem
  JSR 105 counter-signature akışı (`XadesService#doCounterSignature`) artık
  cert public key tipine göre KeyInfo'yu zenginleştiriyor:
  - RSA cert → `<ds:KeyValue><ds:RSAKeyValue><ds:Modulus/><ds:Exponent/></ds:RSAKeyValue></ds:KeyValue>`
    (xmldsig namespace `http://www.w3.org/2000/09/xmldsig#`)
  - EC  cert → `<ds:KeyValue><dsig11:ECKeyValue
    xmlns:dsig11="http://www.w3.org/2009/xmldsig11#"><dsig11:NamedCurve
    URI="urn:oid:..."/><dsig11:PublicKey>...</dsig11:PublicKey></dsig11:ECKeyValue></ds:KeyValue>`
    (XML-DSig 1.1 namespace; KURUM02 EC P-384 için `urn:oid:1.3.132.0.34`,
    P-256 için `urn:oid:1.2.840.10045.3.1.7`, P-521 için `urn:oid:1.3.132.0.35`)

  Bu branch dispatch'i Apache xmlsec 2.2.3'ün
  `org.apache.xml.security.keys.content.KeyValue` ctor'unda hazır (line 92-115:
  `instanceof RSAPublicKey` / `instanceof ECPublicKey`); xades4j tarafında
  yalnız `XadesBesSigningProfile#withBasicSignatureOptions(new BasicSignatureOptions()
  .includeSigningCertificate(SigningCertificateMode.SIGNING_CERTIFICATE)
  .includePublicKey(true))` çağrısı; counter-sig tarafında ise
  `KeyInfoFactory#newKeyValue(signingCert.getPublicKey())` çağrısı yeterli.
  Cert sertifika modu yine `SIGNING_CERTIFICATE` (zincir değil tek imzacı —
  TÜBİTAK kılavuzuyla uyumlu, certificate-chain leak'i yok). Server-side kardeş
  proje (`mersel-dss-server-signer-java/src/main/java/eu/europa/esig/dss/xades/
  signature/XAdESSignatureBuilder.java`) tamamen DSS'e geçtikten sonra aynı
  zenginleştirmeyi DSS'in `XAdESSignatureBuilder` sınıfını shadow ederek
  `addRSAKeyValue` / `addECKeyValue` private metotlarıyla manuel kuruyor;
  agent xades4j path'inde kalacağı için bu davranışı xmlsec'in zaten hazır
  olan key-type dispatch'inden tek satır config ile ücretsiz alır. Test
  matrisi: `XadesKeyInfoEnrichmentTest` Kamu SM test PFX'leriyle (KURUM01
  RSA-2048 + KURUM02 EC P-384) end-to-end imzalama yapıp counter-signature
  KeyInfo'sundaki Modulus/Exponent ve NamedCurve URI/PublicKey değerlerini
  kilitler.

- **AKIS / SafeSign opaque PKCS#11 private key'leri için `Supplied key is not a
  RSAPrivateKey instance` regresyonu (kritik imzalama hatası)**: AKIS v2.5
  (firmware) gibi CKA_SENSITIVE=true RSA private key tutan kartlarda — yani
  modulus / private exponent attribute'larının token-out extraction'a kapalı
  olduğu tüm akıllı kartlarda — `POST /xades/sign` çağrısı `SIGNATURE_FAILED:
  XAdES-BES imzalama başarısız: Supplied key
  (sun.security.pkcs11.P11Key$P11PrivateKey) is not a RSAPrivateKey instance`
  ile başarısız oluyordu. Trace cause zinciri kök kaynağı gösteriyordu:
  `xades4j.XAdES4jXMLSigException → org.apache.xml.security.signature.XMLSignatureException
  → java.security.InvalidKeyException` (BouncyCastle'ın
  `org.bouncycastle.jcajce.provider.asymmetric.rsa.DigestSignatureSpi`'sından
  fırlatılıyor). Kök neden: önceki commit'in `BouncyCastleSetup` ile BC'yi
  JCA pozisyon 1'e yerleştirmesi sonrası, xmlsec 2.2.3 `SignatureBaseRSA`'nın
  ctor'undaki `Signature.getInstance(algorithmID)` çağrısı provider'sız
  yapılıyor, JCA chain BC'yi (pos 1) seçiyor ve BC'nin RSA Signature SPI'sı
  `key instanceof RSAPrivateKey` kontrolünü pas geçemiyor — çünkü token RSA
  private key material'ını dışarı vermeyince SunPKCS11 generic
  `P11Key$P11PrivateKey` base class'ını döner; ne `RSAPrivateKey` ne
  `ECPrivateKey` arayüzlerini implement etmez. **Çözüm**:
  `XadesService#doXadesBesSign` artık `signer.sign(...)` çağrısını
  `org.apache.xml.security.algorithms.JCEMapper#setProviderId(jcaProviderName)`
  try/finally bloğuna sarmalıyor (`jcaProviderName = "SunPKCS11-" + ourConfigName`).
  xmlsec'in `SignatureBaseRSA` / `SignatureECDSA` constructor'ları
  `JCEMapper.providerId` set olduğunda `Signature.getInstance(algorithmID,
  providerId)` formunu çağırır — bu çağrı doğrudan SunPKCS11 provider'ına
  düşer; SunPKCS11'in `P11Signature` implementasyonu P11Key'i (her alt-sınıfı
  dahil) doğrudan kabul edip `C_Sign`'a yönlendirir, kontrol asla
  `instanceof RSAPrivateKey` testine ulaşmaz. Sensitive private key material'ı
  asla kartı terk etmez. Bonus düzeltme: cert public key tipi (RSA / EC) artık
  `keyingProvider.getSigningCertificateChain()` eagerly çağrılarak öğreniliyor
  ve {@link SignatureProfileResolver}'a doğru `keyHint` (`"RSA"` ya da
  `"EC"`) veriliyor — önceden hardcoded `"RSA"` idi ve EC sertifikalı
  kartlarda yanlış mekanizma seçimine yol açıyordu (sahada AKIS / SafeSign
  hem RSA hem EC sertifika kombinasyonu yaygın). Server-side kardeş projede
  (`mersel-dss-server-signer-java` @ `b38f88d`) aynı RSA-vs-EC branching kalıbı
  daha önce uygulanmıştı; ancak JCEMapper bridge agent-side'da yeni — server
  daha sonra DSS 2-faz API + IAIK Native PKCS#11 backend'ine (`8c39919`)
  geçerek tüm JCA path'ini bypass etti, agent ise xades4j yolunu koruyacağı
  için bu bridge kalıcı çözüm.
- **AKIS / EC objesi taşıyan kartlarda `Only named ECParameters supported`
  patolojisi (kritik imzalama regresyonu)**: AKIS v2.5 gibi RSA imzalama
  anahtarına ek olarak EC objesi (EC private/public key veya EC sertifika)
  taşıyan akıllı kartlarda `POST /xades/sign` çağrısı tutarsız şekilde
  `SIGNATURE_ALGORITHM_UNSUPPORTED: XAdES-BES imzalama başarısız:
  Unsupported parameters | root: Only named ECParameters supported` ile
  başarısız oluyordu. Trace cause zinciri kök nedeni gösteriyordu:
  `xades4j.UnexpectedJCAException → java.security.KeyStoreException →
  java.io.IOException("Only named ECParameters supported")`. Kök neden:
  JDK 1.8 `SunEC` SEC 1 `ECParameters` CHOICE'unun yalnız `namedCurve`
  arm'ını destekler; AKIS firmware'i `CKA_EC_PARAMS` attribute'unu
  **explicit** formda döndürür. SunPKCS11 `P11KeyStore.engineLoad()`
  sırasında karttaki tüm objeleri enumerate eder; EC objesi parse'ı için
  provider belirtmeden `AlgorithmParameters.getInstance("EC")` çağırır
  ve JCA arama sırası `SunEC`'ye düşüp tüm keystore load'u devirir —
  gerçek imzalama RSA bile olsa. **Çözüm**: yeni
  `io.mersel.dss.agent.api.services.keystore.BouncyCastleSetup` utility'si
  ilk SunPKCS11 oluşturulmadan önce {@link
  org.bouncycastle.jce.provider.BouncyCastleProvider}'ı JCA pozisyon 1'e
  yerleştirir ve `SunEC`'yi `Security.removeProvider` ile kaldırır. BC
  hem named hem explicit EC parametre formunu destekler; service cache'i
  EC için BC'ye düşer ve `engineLoad` artık patlamaz. Setup üç giriş
  noktasında idempotent olarak çağrılır:
  - `Pkcs11Session#open` — `POST /pades/sign`, `/xades/sign/counter`,
    `/smartcard/pin/validate` ortak ana akışı.
  - `XadesService#doXadesBesSign` — xades4j
    `PKCS11KeyStoreKeyingDataProvider` kendi SunPKCS11'ini ayağa
    kaldırdığı `/xades/sign` xades4j akışı (bu commit'teki trace'in
    geldiği yer).
  - `Pkcs11ModuleProbe#defaultSunPkcs11Probe` — L3 kart vendor probe'unun
    PIN'siz `ks.load(null, null)` denemesi (kartta public EC sertifikası
    varsa probe yanlış-negatif dönüp L3 down-rank'a sebep olabiliyordu).
  Etki: AKIS kartlarında yıllardır tezgâh-altı düzeltme olarak süren
  "JVM yolundan `SunEC`'yi çıkar" workaround'u artık kütüphane içinde,
  her PKCS#11 girişinde otomatik uygulanıyor. Sunucu-tarafı kardeş projede
  (`mersel-dss-server-signer-java` @ `b38f88d`) aynı kalıp daha önce
  uygulanmıştı; agent tarafıyla davranış paritesi sağlandı.

### Added

- **İmzalama tanılama altyapısı (Signature Diagnostics)**: AKIS / SafeSign /
  Aladdin gibi farklı kart firmware'lerinde `SIGNATURE_FAILED: Unsupported
  parameters` hatasının kökünü tek atışta bulup çözmek için kapsamlı bir
  tanılama katmanı eklendi. Dört bileşen birlikte çalışır:
  - **TraceId + cause chain (Bileşen A)**: Her HTTP isteğine
    `TraceIdFilter` aracılığıyla `X-Mersel-Trace-Id` UUID'si üretilir; aynı
    değer MDC'ye yazılır (logback pattern'inde `[%X{traceId:--}]` olarak
    görünür) ve `ErrorModel.traceId` alanına basılır. `ErrorModel.causeChain`
    (yeni alan) cause zincirini düz, JSON dostu `[{type, message}]` listesi
    olarak rapor eder — `SIGNATURE_FAILED` opak mesajının ardındaki
    `InvalidAlgorithmParameterException → CKR_MECHANISM_INVALID` zinciri
    artık kullanıcıya tek atışta görünür. Kapatmak için
    `mersel.signer.diagnostics.expose-cause-chain=false`.
  - **Mekanizma probe (Bileşen B)**: `Pkcs11MechanismProbe` token'ın
    desteklediği `CKM_*` mekanizmalarını ve token meta bilgilerini (label,
    manufacturer, model, firmware sürümü, maskelenmiş seri no) PIN
    HARCAMADAN okur. `GET /smartcard/mechanisms?terminalName=...` ucu bu
    çıktıyı + RSA/ECDSA için XAdES profil önerisini birlikte döner; frontend
    imzalama akışından önce kullanıcıya "kartınız XAdES-BES için uygun" ya da
    "firmware'iniz `CKM_SHA256_RSA_PKCS` desteklemiyor" gibi proaktif uyarı
    gösterebilir.
  - **Mekanizma-aware algoritma seçimi (Bileşen D)**: `XadesService` artık
    her imzalama çağrısında `SignatureProfileResolver` üzerinden token'ın
    gerçek mekanizma listesine göre xades4j `AlgorithmsProviderEx`'i seçer.
    Modern AKIS firmware'i `CKM_SHA256_RSA_PKCS`'i destekliyorsa
    `rsa-sha256` URL'i kullanılır; eski firmware sadece `CKM_RSA_PKCS` (raw)
    veriyorsa `raw-rsa-soft-digest` fallback'i devreye girer; SHA-1 only
    durumunda ETSI deprecation uyarısıyla `rsa-sha1-only` modu seçilir.
    Sertifika ECDSA ise `CKM_ECDSA_SHA384` öncelikli ECDSA profili
    uygulanır. Hiçbir uyumlu mekanizma yoksa `SIGNATURE_FAILED` yerine
    `SIGNATURE_ALGORITHM_UNSUPPORTED` (yeni hata kodu) +
    `signatureDiagnostics.remediation` listesi (firmware güncelleme
    yönlendirmesi) döner — frontend artık doğru aksiyon ekranını gösterebilir.
  - **Sign-probe ve support-bundle uçları (Bileşen C + E)**:
    `POST /diagnostics/sign-probe` PIN'siz dry-run'la "bu kart imzalayabilir
    mi?" sorusunu yanıtlar (RSA + ECDSA dalları ayrı ayrı raporlanır).
    `GET /diagnostics/support-bundle` ZIP olarak çıktı verir: app sürümü,
    JVM/OS özet, PCSC tanılaması, takılı her kart için mekanizma listesi ve
    sign-probe sonucu. PII içermez (PIN ve kart serisi tam asla pakete
    girmez). Kullanıcı destek talebine bu ZIP'i iliştirir; destek operatörü
    5 dakikada sorunu tespit eder.
- **`ErrorModel`'e `signatureDiagnostics` alanı**: kart tipi, ATR, lib yolu,
  kullanılan/karta gönderilen `CKM_*`, JCA imza adı, fallback stratejisi,
  uyarılar ve aksiyon listesi imzalama hatası yanıtlarında bu objede
  toplanır. `@JsonInclude(NON_NULL)` ile sadece dolu alanlar yanıta yazılır.
- **In-app Tanılama Paneli (Diagnostics Panel)**: HTTP yanıtlarına ek olarak
  trace/destek verisini uygulamanın masaüstü UI'ından da canlı izleyebilmek
  için yeni bir Swing paneli eklendi. Mimari özet:
  - **`TraceRecorder` ring buffer**: Her HTTP isteği tamamlandığında yeni
    `TraceRecordingFilter` (sıra: `TraceIdFilter`'dan hemen sonra)
    `TraceRecord` üretir — traceId, method, path, statusCode, durationMs,
    sanitized query, errorCode, exceptionType, cause chain ve (varsa)
    `signatureDiagnostics` alanlarıyla. Buffer thread-safe, varsayılan 200
    kapasite ile bounded; dolduğunda en eski düşürülür. PIN, ham gövde,
    sertifika içeriği ASLA buraya yazılmaz; query string'de `pin`,
    `password`, `token`, `apikey`, `certificateId` gibi anahtarlar
    otomatik `***` ile maskelenir; uzak IP son okteti maskelenir
    (loopback hariç). Aç/kapa toggle: `mersel.signer.diagnostics
    .trace-recorder.enabled` (default `true`). Kapasite override:
    `mersel.signer.diagnostics.trace-recorder.capacity` (default `200`).
  - **REST uçları**: `GET /diagnostics/traces?limit=&errorOnly=` en yeni →
    en eski sırayla kayıtları + sayaç istatistiklerini döner;
    `DELETE /diagnostics/traces` buffer'ı temizler (sayaçlar korunur);
    `POST /diagnostics/traces/enabled?enabled=true|false` recorder'ı
    runtime'da aç/kapa.
  - **Masaüstü `DiagnosticsPanel` — yeniden tasarım (modern Swing UI)**:
    `MainWindow`'daki "Geliştirici araçları" satırına ve tray menüsüne
    eklenen "Tanılama paneli" item'ı, MainWindow paletine (slate tonları)
    oturan modeless Swing dialog'unu açar. Layout dört bölümdür:
    (1) **Header** — başlık + alt açıklama (PII garanti notu).
    (2) **Toolbar** — surface tonlu sticky bant: arama input'u (path /
    errorCode / traceId üzerinde live filter), "Yalnız hatalar" chip'i,
    "Tanılama kaydı açık" toggle'ı (recorder runtime aç/kapa) ve
    "Buffer'ı temizle" ghost-danger butonu (onay kutulu).
    (3) **Master/detail split** — üst paneldeki yüksek satırlı
    `JTable`'da method (GET=mavi, POST=yeşil, PUT/PATCH=turuncu,
    DELETE=kırmızı) ve status (2xx=yeşil, 3xx=mavi, 4xx=turuncu,
    5xx=kırmızı) için custom **rounded pill renderer**'lar; alternate
    stripe satır arkaplanı; trace ID için monospace; süre sayısal
    sıralanır; hata kodu boş değilse kırmızı bold. Alt panelde 4 sekmeli
    detay: **Özet** (key/value grid: traceId, başlangıç, method, path,
    sanitised query, status, süre, uzak adres, errorCode, errorMessage,
    exceptionType), **Hata zinciri** (her frame için kart: tip + mesaj),
    **İmzalama tanılaması** (`signatureDiagnostics` pretty JSON),
    **Ham JSON** (kopyalanabilir). Buffer ya da filtre sonucu boşsa
    `CardLayout` üzerinden **empty-state overlay** ("Henüz trace kaydı
    yok / health/ping kayıt dışı tutulur") gösterilir.
    (4) **Footer** — sayaç bandı ("N görünür · N toplam · kapasite N ·
    düşürülen N · kayıt AÇIK/KAPALI") ve sağda "JSON'u kopyala",
    "Dışa aktar (.json)", "Tümünü dışa aktar (.ndjson)" eylemleri.
    Klavye kısayolu **Ctrl/Cmd+W** paneli kapatır, **Ctrl/Cmd+C** seçili
    kaydın JSON'unu pano'ya kopyalar. Listener pub/sub ile yeni kayıtlar
    EDT'de canlı olarak tabloya `prepend` edilir; recorder kilidi UI
    thread'ini bloklamaz. Headless ortamda no-op.
  - **Gürültü filtresi — health/ping otomatik skip**: Frontend ya da
    uptime monitor'lar saniyede onlarca `/actuator/health`, `/health`,
    `/ping`, `/favicon.ico`, `/error` isteği atabildiği için bunlar
    artık `TraceRecordingFilter` seviyesinde **kayıt dışında** tutulur;
    ne ring buffer'a ne UI tanılama paneline ne de support bundle'a
    düşer (gerçek API trafiğinin ringden düşürülmesi engellenir).
    Karşılaştırma case-sensitive prefix match'tir ve path boundary'sine
    duyarlıdır (`/healthcare`, `/healthy-records` gibi yollar yanlışlıkla
    skip edilmez). Liste config ile override edilebilir:
    `mersel.signer.diagnostics.trace-recorder.skip-paths` (CSV) veya
    `MERSEL_AGENT_TRACE_RECORDER_SKIP` env değişkeni; `none` veya `off`
    token'ı görüldüğünde tüm filtre devre dışı kalır (her şey kaydedilir).
  - **Destek paketinde recent-traces.json**: `GET /diagnostics/support-bundle`
    çıktısına `recent-traces.json` dosyası eklendi — son 100 kayıt + sayaç
    özeti destek operatörüne giderken pakete dahil olur. PII filtreleri
    aynı sıkılıkta uygulanır.

## [1.0.6] — 2026-06-02

### Fixed

- **Kritik: `IaikPkcs11Signer.locatePrivateKey` yanlış anahtarla imzalama riski
  (dual-key kartlar)** — sahada NES Bulut / Kamu SM dual-key (SIGN0 imzalama +
  SIGN1 anahtar uzlaşımı) setup'larında her iki anahtar aynı CKA_ID ile yazıldığı
  için SunPKCS11 JCA akışı patladıktan sonra IAIK fallback'ine düşülüyordu.
  IAIK tarafında `locatePrivateKey` CKA_ID/CKA_LABEL match'i olmasa bile
  `handles[0]` (ilk rastgele private key) ile devam ediyordu — yani SIGN0 yerine
  SIGN1 anahtarıyla imza üretilebilir, imza geçersiz olabiliyor.

  Artık `firstAnyMatch` (cert ile eşleşmeyen ilk handle) fallback'i kaldırıldı;
  cert ile eşleşmeyen hiçbir private key yoksa `CertificateLookupException`
  fırlatılıyor. CKA_SIGN=TRUE tercih hâlâ korunuyor.

- **`XadesService.certificateDigestBase64` SHA-512 / SHA-1 sessiz düşürme
  bug'ı** — `SignatureProfileResolver.chooseAlgorithms` SHA-512 ve SHA-1
  seçebildiği halde `certificateDigestBase64` sadece SHA-256/SHA-384 biliyor;
  geri kalanlar sessizce SHA-256'ya düşüyordu → digest tutarsız, imza XAdES
  doğrulamasında reddediliyordu. Artık SHA-512 + SHA-1 de destekleniyor.

- **`Pkcs11Session.open` sadece `LoginException` yakaladığı için SunPKCS11
  resource leak** — bazı SunPKCS11 patolojilerinde (CKR_FUNCTION_FAILED vb.)
  `ProviderException` fırlatılıyor, `LoginException` catch bunu yakalamıyordu:
  `Security.removeProvider()` + `silentDelete(configFile)` atlanıyor, kalıcı
  provider registry kirliliği ve `/tmp` dosya sızıntısı oluşuyordu. Catch
  genişletildi.

- **`expose-cause-chain` default `true` → `false`** — error yanıtlarında cause
  zinciri varsayılan artık basılmıyor. Cause zinciri PIN/path/host bilgisi
  sızma riski taşıyordu. Açmak için `MERSEL_AGENT_EXPOSE_CAUSE_CHAIN=true`.

- **Counter-signature try-with-resources yanlış fallback routing** —
  `Pkcs11Session.close()` hatası imza body'sinin üzerinde suppress edildiği
  için outer `catch (RuntimeException)` bunu IAIK fallback değerlendirmesine
  sokabiliyordu. Explicit try/finally ile body ve close hataları izole
  edildi; close hatası sadece log'lanır.

- **`JCEMapper.setProviderId` race condition** — global static state multi-thread
  imzalamada yarışa yol açıyordu. `synchronized (XadesService.class)` ile
  korumaya alındı.

- **`MechanismCapabilityService` ↔ `SignatureProfileResolver` tablo duplikasyonu**
  — iki ayrı CKM→URL mapping tablosu tutuluyordu, biri değişirse diğeri drift
  ediyordu. `MechanismCapabilityService.buildSummary` artık
  `SignatureProfileResolver.chooseAlgorithms`'a delege ediyor; tek otorite.

- **`Pkcs11MechanismProbe.scanCertificates` `Integer.MAX_VALUE` limit** — bazı
  sürücülerde sorun çıkardı; `MAX_OBJECT_LIST = 1000` ile sınırlandı.

- **`TraceRecord.isSensitiveKey` tam eşleme restrictive** — `userPin`,
  `card_pin`, `APIKEY`, `x-token` gibi varyantlar maskelenmiyordu. Substring /
  case-insensitive matching yapıldı, ek olarak `credential` eklendi.

- **`SignerException.diagnostics` `Object` tiplemesi** — `Object` yerine
  `SignatureDiagnostics` tipli alan; runtime `instanceof` kontrolü kaldırıldı.

### Removed

- **Geriye uyumluluk yıktı (major cleanup)**: Kullanılmayan / duplicate
  ctor'lar, dead code ve deprecated API'lar kaldırıldı. Etkilenenler:

  - `IaikPkcs11Signer`: `singleThreaded` field, `isDuplicateCkaIdError`,
    `attributeBytes` helper (inline'a alındı).
  - `Pkcs11Session`: `getPin()`, `newPasswordCallback()`, `invokeOptionalLogout`.
  - `Pkcs11Errors.unwrapInvocationCause`.
  - `Pkcs11Reflection`: `tokenInfoCls`, `mechanismInfoCls`, `classForNameSilent`.
  - `XadesService`: 2-arg ctor, 2-arg `signHrWithSession` overload.
  - `SmartCardController`: 3-arg ctor, `mechanismCapabilityService == null` ISE branch.
  - `SystemTrayManager`: 3-arg ve 5-arg ctor (sadece 6-arg kaldı).
  - `MainWindow`: 4-arg ctor (sadece 5-arg kaldı).
  - `MainWindowLifecycle.show`: 4-arg overload (sadece 5-arg kaldı).
  - `DesktopUiBootstrap`: 6-arg ctor (sadece 7-arg kaldı).
  - `TraceRecorder`: no-arg ctor.
  - `GlobalExceptionHandler`: no-arg ctor.
  - `DiagnosticsPanelPreviewMain` (test fixture duplicate) silindi.

### Changed

- `pom.xml`: `ipkcs11wrapper` versiyonu `<ipkcs11.version>` property'sine
  taşındı; diğer dependency version property'leri ile tutarlı.
- `NOTICE`: pom.xml'de bulunmayan "Apache POI" ve "Jakarta XML Binding"
  atıfları kaldırıldı.

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

[Unreleased]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.1.3...HEAD
[1.1.3]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.6...v1.1.0
[1.0.6]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/mersel-dss/mersel-dss-agent-signer-java/releases/tag/v1.0.1
