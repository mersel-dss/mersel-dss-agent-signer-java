# mersel-dss-agent-signer-api

[![CI](https://github.com/mersel-dss/mersel-dss-agent-signer-java/actions/workflows/ci.yml/badge.svg)](https://github.com/mersel-dss/mersel-dss-agent-signer-java/actions/workflows/ci.yml)
[![License: Apache-2.0 + Brand Attribution](https://img.shields.io/badge/License-Apache--2.0%20%2B%20Brand%20Attribution-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-1.8-blue.svg)](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7-brightgreen.svg)](https://spring.io/projects/spring-boot)

<!-- LATEST_RELEASE:BEGIN -->

> **Son sürüm — [`v1.0.2`](https://github.com/mersel-dss/mersel-dss-agent-signer-java/releases/tag/v1.0.2)** ·
> Doğrudan indir: [`mersel-dss-agent-signer-api-1.0.2.jar`](https://github.com/mersel-dss/mersel-dss-agent-signer-java/releases/download/v1.0.2/mersel-dss-agent-signer-api-1.0.2.jar) ·
> [SHA-256](https://github.com/mersel-dss/mersel-dss-agent-signer-java/releases/download/v1.0.2/SHA256SUMS.txt) ·
> [SBOM](https://github.com/mersel-dss/mersel-dss-agent-signer-java/releases/download/v1.0.2/mersel-dss-agent-signer-api-1.0.2-bom.json) ·
> [Tüm sürümler](https://github.com/mersel-dss/mersel-dss-agent-signer-java/releases)

<!-- LATEST_RELEASE:END -->

> Kullanıcının makinesinde çalışan, akıllı kart üzerinden **PAdES** ve
> **XAdES** imza üreten ve **GİB e-Fatura başvurusu** gönderen yerel
> imza uygulaması. **Tek jar dosyası — macOS, Linux ve Windows'ta aynı,
> çift tıkla çalışır.** Tarayıcı veya masaüstü uygulaman
> `http://localhost:15211` adresine bağlanıp imzalama işlemlerini
> tetikler; karta dokunan kod asla sunucuda değil, hep kullanıcının
> yanında çalışır.

## Nasıl kullanılır?

Kullanıcının makinesinde uygulamayı başlatıyorsunuz; arka planda
`http://localhost:15211` üzerinde dinliyor. Sonra **kendi masaüstü
uygulamanızdan, tarayıcı eklentinizden veya web uygulamanızdan** bu
adrese HTTP istekleri atarak:

- Bağlı **akıllı kart okuyucularını** ve takılı kartları görebilir,
- Karttaki **sertifikaları PIN sormadan** listeleyip kullanıcıya
  seçtirebilir,
- Seçili sertifikayla bir PDF'i **PAdES-B** ile imzalayabilir
  (e-Fatura, e-Arşiv, sözleşme vb.),
- Bir XML'i **XAdES-BES** veya **Counter-Signature** ile imzalayabilir
  (**e-Defter**, **HR-XML müşteri tarafı imzası**, beyanname vb.),
- **GİB e-Fatura başvurusunu** doğrudan gönderebilirsiniz.

### Uygulamayı başlatma

Java 8 makinede kuruluysa **`.jar` dosyasına çift tıklamak yeterli** —
macOS Finder, Linux file manager (Nautilus / Dolphin / Files) ve Windows
Explorer üçü de standart Java Runtime association'ı varsayar. Çift
tıkladığında:

1. Kısa bir splash penceresi açılır,
2. Uygulama `http://localhost:15211` adresinde dinlemeye başlar,
3. Sistem tepsisinde (macOS menubar / Windows tray / Linux indicator)
   Mersel simgesi belirir — "API dökümanını aç", "Sağlık kontrolünü aç"
   ve "Çıkış" menüsüyle.

Terminal alternatifi (CI / sunucu / Docker için):

```bash
# X.Y.Z yerine GitHub Releases'ten indirdiğin sürümün adı gelir.
java -jar mersel-dss-agent-signer-api-X.Y.Z.jar
```

> **Önkoşul:** Java 8+ kurulu olmalı. Native installer
> (`.msi` / `.pkg` / `.deb` / `.rpm` — bundled JRE'li, Java aramayan)
> yol haritasında — JDK 17 portu kalemi tamamlanınca jpackage tabanlı
> paketler eklenecek; o noktada "Java kurulu mu?" sorusu da ortadan
> kalkar.

### 3 adımda imza akışı

```bash
# 1) Uygulamayı başlat — çift tıkla veya CLI ile.
java -jar mersel-dss-agent-signer-api-X.Y.Z.jar

# 2) Bağlı okuyucuları ve kart tipini al.
curl http://localhost:15211/smartcard

# 3) Sertifikaları PIN istemeden listele, kullanıcıya seçtir.
curl 'http://localhost:15211/smartcard/certificate?terminalName=ACR39U%20ICC%20Reader&purpose=SIGNING'
```

Kullanıcı sertifikayı seçtikten sonra PIN'i alıp imzayı tetikleyin:

```bash
# PDF imzası — PAdES-B (e-Fatura, e-Arşiv, sözleşme vb.)
curl -X POST http://localhost:15211/pades/sign \
  -F 'content=@invoice.pdf' \
  -F 'terminalName=ACR39U ICC Reader' \
  -F 'certificateId=6180884538SIGN0' \
  -F 'pin=123456'

# XML imzası — XAdES-BES (e-Defter, beyanname vb.)
curl -X POST http://localhost:15211/xades/sign \
  -F 'content=@yevmiye-defteri.xml' \
  -F 'terminalName=ACR39U ICC Reader' \
  -F 'certificateId=6180884538SIGN0' \
  -F 'pin=123456' \
  -F 'contentType=XADES_BES'

# XML counter-imza — HR-XML müşteri tarafı imzası
curl -X POST http://localhost:15211/xades/sign \
  -F 'content=@hrxml-server-signed.xml' \
  -F 'terminalName=ACR39U ICC Reader' \
  -F 'certificateId=6180884538SIGN0' \
  -F 'pin=123456' \
  -F 'contentType=COUNTER_SIGNATURE'
```

Tüm endpoint'leri tarayıcıdan canlı denemek için
<http://localhost:15211/> adresindeki **Scalar UI**'ya bakın.

> **UX ipucu:** PIN'i en başta değil, **yalnız imza ekranında** sorun.
> Kart tak → sertifikaları listele → kullanıcı seçsin → PIN'i o an iste
> → imzala. PIN sayacı boşa harcanmaz, kart kilitlenme riski düşer,
> kullanıcı PIN'i frontend'de gereksiz uzun süre tutulmaz.

## Neyi çözüyor?

Türkiye e-İmza ekosisteminde akıllı kart (Mali Mühür, e-İmza, Kamu SM
QES) **fiziksel olarak kullanıcıya** bağlıdır; sunucudan PKCS#11
token'a erişmenin yolu yoktur. Bu nedenle her web tabanlı muhasebe /
ön muhasebe / e-Belge çözümü, _zorunlu olarak_ kullanıcı makinesinde
bir yerel ajan çalıştırır.

Pazardaki yerleşik ajanlar genellikle (i) tek bir özel entegratöre
sıkı bağımlı, (ii) kapalı kaynak, (iii) Türkçe Kamu SM kartlarının
"sadece listelemek için bile PIN ister" davranışını çözmemiş, (iv)
hata mesajları opak ve (v) tek mimari (x86_64) odaklıdır. Bu yerel
uygulama bu beş noktayı doğrudan hedef alır: **standartlara sadık,
vendor-neutral, açık kaynak, PIN'siz sertifika listeleme, yapısal hata
cevapları ve ARM64 dahil çoklu mimari**.

Sunucu tarafı kardeşi
[`mersel-dss-server-signer-java`](https://github.com/mersel-dss/mersel-dss-server-signer-java)
aynı `ErrorModel` sözleşmesini paylaşır; frontend tarafında tek bir
hata işleme katmanı yeter.

## Öne çıkan yetenekler

- **PAdES-B (e-Fatura, e-Arşiv için PDF imza)** — RSA ve ECDSA cert'ler
  için otomatik digest algoritması; PDF imza panelinde görünen "Sebep" /
  "Konum" satırları opsiyonel parametrelerle özelleştirilebilir.
- **XAdES-BES + XAdES Counter-Signature (e-Defter, HR-XML müşteri
  imzası)** — ETSI TS 101 903 uyumlu XML imza üretimi.
- **GİB e-Fatura başvurusu** — tüzel kişi ve sorumlu kişi bilgilerini
  taşıyan tam form gönderimi; sertifikadan ad/soyad otomatik türetilir,
  isteğe bağlı override.
- **PIN'siz sertifika listeleme** — Kamu SM kartlarının _listelemek için
  bile PIN iste_ davranışını bypass eden yerel PKCS#11 okuyucu sayesinde
  kullanıcı PIN'i yalnızca imza ekranında girer. PIN sayacı boşa
  harcanmaz, kart kilitlenme riski azalır.
- **Akıllı sertifika seçimi** — kart üzerindeki birden fazla sertifika
  arasından (imza / şifreleme / kimlik doğrulama) frontend'in tek tık ile
  doğru olanı pre-select etmesi için her sertifikaya `purpose` ve tek bir
  `recommended` bayrağı türetilir. Türkçe Kamu SM politikalarını
  (Mali Mühür İmza/Şifreleme, KamuSM QES Bireysel/Kurumsal) tanır ve
  RFC 5280 generic karar ağacının önüne geçer.
- **Gerçek revocation kontrolü** — AIA → OCSP, fallback CDP → CRL;
  yanıt frontend'e `key=value` segmentleri ile structured döner
  (ReasonCode, producedAt, responder URL).
- **20+ kart vendor desteği** — AKIS, SafeNet/Aladdin eToken, Gemalto
  IDPrime, Kobil, A-Trust SafeSign, T-Kart, Net iD, nCipher, SoftHSMv2
  vb. `smartcard-config.xml` üzerinden genişletilebilir. ATR ya da
  vendor parmak izi bilinmiyorsa kullanıcıya kart tipi seçtiren bir
  frontend modal akışı vardır.
- **ARM64 desteği** — Apple Silicon Linux VM (Parallels/UTM/OrbStack),
  Raspberry Pi 4/5, AWS Graviton, Pardus ARM ve diğer aarch64
  platformlarda PKCS#11 lib otomatik bulunur.
- **Modern API dökümanı** — Swagger UI yerine `/` adresinde
  [Scalar](https://scalar.com); OpenAPI 3 spec `/v3/api-docs`.
- **Masaüstü deneyimi** — Swing splash + sistem tepsisi ikonu (macOS
  menubar / Windows tray / Linux); headless ortamlarda otomatik devre
  dışı.
- **Otomatik güncelleme kontrolü** — GitHub Releases üzerinden yeni
  sürüm algılama; sistem tepsisinde "Yeni sürüm — indir" öğesi belirir.
- **Vendor-neutral güvenlik** — PIN log'a basılmaz (otomatik
  maskeleme), her `close()` PIN array'ini zero-out eder, CORS varsayılan
  olarak yalnızca loopback origin'lere açıktır, hiçbir telemetri yoktur.

## Bağımsızlık ve nötralite

Mersel DSS, GİB nezdinde yetkilendirilmiş bir e-Belge **özel
entegratörü değildir** ve bu yazılım, herhangi bir özel
entegratörle ticari ortaklık, anlaşma veya endorsement ilişkisi
içinde geliştirilmemiştir. Yazılım, Türkiye e-İmza ekosisteminde
**tüm özel entegratörlere eşit mesafede** duran, vendor-neutral
bir yerel altyapı olarak tasarlanmıştır.

Pratik anlamı:

- **Entegratör seçimi tamamen kullanıcıya aittir.** Yazılım,
  kullanıcının seçtiği herhangi bir özel entegratör — veya
  doğrudan GİB — ile çalışabilecek standart bir PKCS#11 + PAdES
  / XAdES / UBL-TR üretim katmanıdır; belirli bir tarafın iş
  akışına optimize edilmemiştir.
- **Standartlara sadıkız, "tatlı yol" varyantlarına değil.**
  PAdES-B (ETSI EN 319 142), XAdES-BES + Counter-Signature
  (ETSI TS 101 903), UBL-TR 1.2 ve PKCS#11 v2.40 spesifikasyonları
  birebir uygulanır; bir entegratörün özel "kabul ettiği ama
  standart dışı" varyantına dönük bias yoktur.
- **Hidden routing / arka kanal yok.** Yazılım, hiçbir özel
  entegratöre arka planda trafik yönlendirmez. Tüm dış çağrılar
  kullanıcı tarafından açıkça yapılandırılır
  (`application.yml` ve REST DTO alanları üzerinden); imzalanmış
  belge yalnızca çağıran istemciye geri döner.
- **Telemetry / phone-home yok.** Yazılım, ne Mersel'e ne de
  başka bir tarafa kullanım verisi göndermez (yalnızca GitHub
  Releases güncelleme kontrolü hariç; bu da
  `application.yml`'den kapatılabilir). Bu, nötralitenin teknik
  garantisidir.
- **Eş mesafe sözü ortaklık durumunda da sürer.** Bir özel
  entegratörün Mersel ile resmi entegrasyon kurmak istemesi
  halinde değerlendirme kanalı `partnership@mersel.io`
  adresinden açıktır; ancak böyle bir ilişki kurulsa dahi,
  yazılımdaki diğer entegratörler için sağlanan eşit mesafeli
  destek ve standart uyumu **azaltılmayacak**, hiçbir
  entegratöre fiilen ayrıcalık tanınmayacaktır.

Bu nötralite tesadüfi bir tasarım değil, ürünün ekosistem
rolünün temelidir: amaç yeni bir ticari kanal yaratmak değil,
KOBİ'ler ile serbest mali müşavirler ile geliştiriciler için
**paylaşılan bir ortak iyi (commons)** sunmaktır. Aşağıda
[Lisans ve marka](#lisans-ve-marka) bölümünde detaylandırılan
sözleşmesel karşılık (Marka Atıf Eki Önsöz'ü), aynı bağımsızlık
ilkesinin hukuki uzantısıdır.

## Lisans ve marka

**Apache License 2.0** + **Mersel Marka Atıf Eki** (Brand
Attribution Addendum). Tam metin için [`LICENSE`](LICENSE), marka
kullanım politikası için [`TRADEMARK.md`](TRADEMARK.md), korunması
zorunlu atıfların listesi için [`NOTICE`](NOTICE) dosyalarını
inceleyin.

### Kısaca ne demek?

- **Kullanabilirsiniz.** Yazılımı; kaynak veya nesne formunda,
  değiştirerek ya da değiştirmeden, kapalı kaynaklı bir ürünün
  içine gömerek, ticari olarak satarak ya da barındırılan bir
  hizmet (SaaS) olarak sunarak kullanabilirsiniz.
- **Kaynağı açmak zorunda değilsiniz.** Apache 2.0 permissive bir
  lisans; AGPL/GPL benzeri "kaynak paylaşma" yükümlülüğü
  **yoktur**.
- **Bir tek kayıtsız şart:** Yazılım'ın çoğaltıldığı,
  dağıtıldığı, değiştirildiği veya başka herhangi bir biçimde
  kullanıldığı HER durumda, uygulamanın kullanıcı arayüzündeki
  üç Marka Atfı yerinde, görünür ve işlevsel olarak eşdeğer
  kalmak zorundadır:
  1. Açılış (splash) penceresindeki "MERSEL DSS" marka işareti
     + "Agent Signer" başlığı.
  2. Ana pencere (MainWindow) üst kısmındaki Mersel banner /
     logo.
  3. Altbilgi (footer) satırı: _"Türkiye e-İmza süreçleri için
     mersel.io tarafından açık kaynak olarak
     geliştirilmiştir."_ + aktif `mersel.io` köprüsü.
- **Yeniden adlandırma yoluyla atıfları silme istisnası
  YOKTUR.** Yazılım'ı kendi markanız altında dağıtmak, bir
  başka ürüne gömmek veya compound bir başlık altında satmak
  (örn. "Mersel DSS Agent Signer — Acme Edition") serbesttir;
  ancak hiçbir durumda Marka Atıfları kaldırılamaz, gizlenemez
  veya başka bir markayla değiştirilemez.
- **Değiştirip dağıtmak isteyenler** (forklar dahil) için: Marka
  Atıfları yerinde kalır; AYRICA UI'da görünür bir yerde
  "Bu sürüm [Adınız] tarafından değiştirilmiştir; orijinal yazar
  Mersel DSS bu sürümü onaylamamıştır" tarzı bir Modifikasyon
  Bildirimi göstermek zorundasınız (LICENSE Ek § 3). Marka
  Atıfları'nın bulundurulması Mersel DSS'nin onay/sponsorluğu
  anlamına gelmez (LICENSE Ek § 4); bu tür imalardan
  kaçınmalısınız. Ayrıntı: [`TRADEMARK.md`](TRADEMARK.md).

### Neden bu küçük şartı koyuyoruz?

Türkiye e-İmza ekosistemi tarihsel olarak özel mülk, kapalı kaynak
ve yüksek lisans ücretli çözümlerle çevrili kaldı; KOBİ, serbest
mali müşavir ve geliştiriciler için açık kaynaklı, ücretsiz,
denetlenebilir bir alternatif uzun süre eksikti. Mersel DSS bu
boşluğu kapatmak için sistemik bir ortak iyi (commons) sunuyor:
yerel imza, sertifika yönetimi, revocation kontrolü ve GİB
entegrasyonu kapsayan uçtan uca açık bir katman.

Yazılım'ı permissive bırakmayı bilinçli olarak seçtik: ister
kapalı kaynağa göm, ister hosted SaaS yap, ister kapsayıcı ürüne
entegre et — hepsi serbest. Karşılığında talep ettiğimiz tek şey
üç küçük arayüz unsuru ile sınırlı bir **görünürlük**. Bu, açık
kaynak değer zincirinde köken belirsizliğini önler, güvenlik
bildirim kanalını son kullanıcıya tek tık mesafede tutar ve
ekosisteme yatırılan kümülatif emeğin kamusal izlenebilirliğini
korur. Lisansın sözleşmesel karşılığı (consideration) tam olarak
budur ve [`LICENSE`](LICENSE) dosyasındaki Ek'in Önsöz'ünde
ayrıntılandırılır.

### Neden saf Apache-2.0 değil?

Saf Apache-2.0, NOTICE dosyasındaki attribution'ı korumayı şart
koşar ama **UI içindeki marka göstergelerini bağlamaz**. Bu
projenin kimliği `mersel.io` markasıyla birlikte taşındığı için,
Apache 2.0'nin 4. Maddesinin son paragrafının açıkça izin
verdiği "additional terms" yetkisiyle bir **Brand Attribution
Addendum** ekledik. Sözleşmesel zemin saf, niyet net: kullanan
herkes serbest, marka korumalı.

### Üçüncü taraf kaynaklar

`src/main/java/tr/com/cs/` altındaki kaynaklar ve `pom.xml`
içinde bildirilen runtime bağımlılıkları kendi orijinal
lisansları altında dağıtılır; detay için ilgili dosyaların
header'larına ve [`NOTICE`](NOTICE) dosyasına bakın.

## REST API

| Method | Path                     | Ne işe yarar |
|--------|--------------------------|---|
| GET    | `/smartcard`             | Bağlı okuyucular + ATR + tanınan kart tipi + host OS/JRE metadata'sı |
| GET    | `/smartcard/diagnostics` | PC/SC ortam tanılama (OS, JDK, native lib yolu, terminal durumu) |
| GET    | `/smartcard/certificate` | **PIN'siz** sertifika listesi + revocation + iş amacı bayrakları; bilinmeyen kartta 503 + kart tipi adayları |
| POST   | `/pades/sign`            | PDF'i PAdES-B ile imzala (multipart) |
| POST   | `/xades/sign`            | XAdES-BES veya Counter-Signature (multipart) |
| GET    | `/gibApplication`        | GİB e-Fatura başvuru sorgusu (VKN/TCKN) |
| POST   | `/gibApplication`        | GİB e-Fatura başvurusu imzala ve gönder |
| GET    | `/update/status`         | Güncelleme durumu (cache'li) |
| POST   | `/update/check`          | Cache bypass, yeni kontrol |
| GET    | `/actuator/health`       | Spring Actuator health |
| GET    | `/v3/api-docs`           | OpenAPI 3 spec |
| GET    | `/`                      | Scalar API reference UI |

Tam parametre listesi ve canlı deneme için Scalar UI'a bakın
(<http://localhost:15211/>).

### Sertifika seçim mantığı

Bir akıllı kart genelde birden fazla sertifika taşır (imza ayrı,
şifreleme ayrı, bazen kimlik doğrulama ayrı). Frontend her birini ayrı
ayrı incelemek zorunda kalmasın diye `/smartcard/certificate` her
sertifika için **iş amacını** (`purpose`) türetir ve listede tam olarak
bir tanesini `recommended=true` ile işaretler.

| Alan | Anlam |
|---|---|
| `purpose` | `SIGNING` / `ENCRYPTION` / `AUTHENTICATION` / `MIXED` / `OTHER` |
| `qualified` | ETSI QES işareti (QCStatements extension'ı varsa) |
| `turkishCertificatePolicies` | Mali Mühür İmza/Şifreleme, KamuSM QES Bireysel/Kurumsal tanıma |
| `eligibleForSignature` | PAdES/XAdES için kullanılabilir mi (geçerli + imza amaçlı + REVOKED değil) |
| `recommended` | Frontend'in pre-select etmesi gereken tek sertifika |

Tipik frontend kullanımı:

```http
GET /smartcard/certificate?terminalName=ACR39U&purpose=SIGNING
```

→ Sadece imza sertifikaları döner, `recommended=true` olanı varsayılan
seçili getir. Birden fazla varsa öncelik: **qualified > Türkçe SIGNING
policy > saf SIGNING > en yeni `notBefore`**.

Audit / debug için `?eligibleOnly=false` parametresi geçersiz veya
süresi dolmuş sertifikaları da döndürür.

Örnek yanıt (bir Mali Mühür kartı — `*ENCR0` şifreleme, `*SIGN0` imza):

```json
[
  {
    "id": "6180884538ENCR0",
    "subject": "MERSEL YAZILIM ANONİM ŞİRKETİ",
    "purpose": "ENCRYPTION",
    "qualified": false,
    "turkishCertificatePolicies": ["MALI_MUHUR_ENCRYPTION"],
    "eligibleForSignature": false,
    "recommended": false,
    "status": "ACTIVE"
  },
  {
    "id": "6180884538SIGN0",
    "subject": "MERSEL YAZILIM ANONİM ŞİRKETİ",
    "purpose": "SIGNING",
    "qualified": true,
    "turkishCertificatePolicies": ["MALI_MUHUR_SIGNING"],
    "eligibleForSignature": true,
    "recommended": true,
    "status": "ACTIVE"
  }
]
```

Frontend `id` (`6180884538SIGN0`) değerini imza çağrısına `certificateId`
olarak geçirir. Tam alan listesi için Scalar UI'a bakın.

### PAdES — Sebep / Konum (opsiyonel)

PDF okuyucuların imza panelinde görünen iki alan:

| Alan | Default | PDF'de görünür yer |
|---|---|---|
| `reason` | `"e-Belge imzalama"` | "Sebep" / "Reason" |
| `location` | `""` (boş) | "Konum" / "Location" |

```bash
curl -X POST http://localhost:15211/pades/sign \
  -F 'content=@invoice.pdf' \
  -F 'terminalName=ACR39U ICC Reader' \
  -F 'certificateId=6180884538SIGN0' \
  -F 'pin=123456' \
  -F 'reason=Sözleşme imzası' \
  -F 'location=İstanbul / TR'
```

### GİB e-Fatura başvurusu

Zorunlu alanlar: VKN/TCKN, telefon, e-posta, adres, şirket merkezi,
terminal adı, PIN, sertifika ID'si. Opsiyonel olarak tüzel kişi bloğu
(ticaret sicil, oda bilgileri, kuruluş tarihi, web sitesi) ve sorumlu
kişi bloğu (TCKN, ad, soyad, telefon, e-posta) eklenebilir. TCKN
başvurularında sorumlu ad/soyad sertifikadan otomatik türetilir; DTO'da
verilirse override edilir, boş gönderilirse sertifika değerine fallback.

Tam alan listesi ve deneme için Scalar UI'daki
`POST /gibApplication` sayfasına bakın.

### Revocation yanıtı

`CertificateResponse.status` (`ACTIVE` / `REVOKED` / `UNKNOWN`) yanındaki
`revocationStatusMessage` alanı OCSP/CRL yanıtının yapısal özetini
`key=value` segmentleri olarak taşır. Frontend bunu parse edip RFC 5280
ReasonCode etiketi ile kullanıcıya gösterebilir.

Örnek (OCSP REVOKED):

```
OCSP REVOKED; at=Fri Aug 02 14:21:00 GMT+03:00 2024;
reason=keyCompromise (1); responder=http://ocsp.kamusm.gov.tr
```

ReasonCode etiketleri RFC 5280 §5.3.1'i birebir taşır: `keyCompromise`,
`cACompromise`, `affiliationChanged`, `superseded`,
`cessationOfOperation`, `certificateHold`, `removeFromCRL`,
`privilegeWithdrawn`, `aACompromise`. Bilinmeyen kod için
`unknown(N)` döner. CRL'de `reasonCode` extension yoksa `reason=…`
segmenti yer almaz (KamuSM eski Class 3 CRL'leri için graceful).

## Repo düzeni

```
mersel-dss-agent-signer-java/
├── resources/test-certs/         # Kamu SM publicly published test PFX'leri
├── etc/                          # Build yardımcıları (license header vb.)
├── src/main/java/io/mersel/dss/agent/api/
│   ├── controllers/              # REST endpoint'ler
│   ├── dtos/                     # request DTO'ları
│   ├── exceptions/               # SignerException hiyerarşisi
│   ├── models/                   # response model'leri + ErrorModel
│   ├── services/
│   │   ├── certificate/          # CertificateInspector, RevocationChecker
│   │   ├── keystore/             # Pkcs11Session, Pkcs11LibraryResolver
│   │   ├── signature/            # PadesService, XadesService
│   │   └── smartcard/            # SmartCardManager, CardTypeRegistry
│   └── ui/                       # Splash + tray (Swing)
├── src/main/java/tr/com/cs/      # 3. parti kaynak (Cybersoft GİB SDK'sı)
├── src/main/resources/           # application.yml, logback, smartcard-config.xml
├── src/test/                     # birim testler
└── pom.xml
```

## Konfigürasyon

`application.yml` tek kaynak; çoğu değer environment variable ile
override edilir (Spring Boot relaxed binding).

| Env var | Varsayılan | Anlam |
|---|---|---|
| `MERSEL_AGENT_PORT` | `15211` | HTTP bağlanma portu |
| `MERSEL_AGENT_BIND` | `127.0.0.1` | Bind adresi (loopback dışı için `0.0.0.0`) |
| `MERSEL_AGENT_CORS_ORIGINS` | loopback | CORS origin pattern'leri (virgülle ayrılmış) |
| `MERSEL_AGENT_AIA_ENABLED` | `true` | Sertifika zinciri AIA ile tamamlansın mı |
| `MERSEL_AGENT_PCSC_LIBRARY` | (otomatik) | Native PC/SC kütüphane yolu override |
| `MERSEL_AGENT_UI` | `true` | Tüm masaüstü UI (splash + ana pencere + tray) aç/kapat; tamamen kapatmak için `false` |
| `MERSEL_AGENT_UI_SPLASH` | `true` | Yalnız splash penceresini aç/kapat; kapatmak için `false` |
| `MERSEL_AGENT_UI_WINDOW` | `true` | Yalnız ana pencereyi aç/kapat; kapatmak için `false` |
| `MERSEL_AGENT_UI_TRAY` | `true` | Yalnız sistem tepsisi (tray) ikonunu aç/kapat; kapatmak için `false` |
| `MERSEL_AGENT_UPDATE` | `true` | Otomatik güncelleme alt sistemini aç/kapat; kapatmak için `false` |
| `MERSEL_AGENT_UPDATE_PRERELEASE` | `false` | Prerelease'leri de hesaba kat |
| `MERSEL_AGENT_EXTRA_LIB_PATH` | — | Ek PKCS#11 lib arama dizini |

Tam liste ve yapısal default'lar için `src/main/resources/application.yml`
ve [`SignerProperties`](src/main/java/io/mersel/dss/agent/api/config/SignerProperties.java)
sınıfına bakın.

### Çoklu mimari (x86_64 + aarch64)

PKCS#11 lib arama yolları her iki mimariyi de tarar; **Apple Silicon
Linux VM**, **Raspberry Pi 4/5**, **AWS Graviton**, **Pardus ARM** vb.
ortamlarda vendor sürücüsü otomatik bulunur. `GET /smartcard` yanıtındaki
`osArch` alanı (`"aarch64"` / `"x86_64"`) frontend'in mimariye duyarlı
UI göstermesini sağlar.

## Geliştirme

- **Bytecode JRE 1.8 hedefler.** Build hem JDK 1.8 hem JDK 9+ ile yapılır
  (`maven-compiler-plugin` 9+'da otomatik `--release 8`).
- **Runtime için JDK 1.8 zorunludur** (JDK 9+ PKCS#11 oturumu için
  gereken reflection erişimini kapatır).
- Hardware token gerekmez — tüm testler Kamu SM'in publicly published
  test PFX'leri ile çalışır.

```bash
./mvnw -DskipTests compile       # hızlı derleme
./mvnw test                      # birim testler
./mvnw package                   # fat-jar (~54 MB)
./mvnw spotless:apply            # kod format düzelt
./mvnw spotless:check            # CI'da diff varsa kırar
```

### Profile rehberi

| Profile | Ne ekler? | Ne zaman? |
|---|---|---|
| (default) | compile + test + fat-jar | her zaman |
| `quality` | JaCoCo coverage (%15 baseline) + license header check | CI / PR |
| `security` | OWASP Dependency-Check (CVSS ≥ 8 fail) + CycloneDX SBOM (`target/bom.xml` + `target/bom.json`) | opsiyonel — sürüm öncesi manuel `mvn -P security verify` |
| `sbom` | Yalnız CycloneDX SBOM (OWASP'sız, NVD çekimi yok → hızlı); release workflow'undan çağrılır | release tag |

### Yeni sürüm yayınlamak

Release akışı `vX.Y.Z` formatındaki tag push'u ile **otomatik tetiklenir**
([`.github/workflows/release.yml`](.github/workflows/release.yml)):

```bash
# 1) pom.xml ve CHANGELOG'u bump'la (BREAKING varsa MAJOR, feature ekiyse
#    MINOR, fix ise PATCH).
./mvnw versions:set -DnewVersion=3.1.0 -DgenerateBackupPoms=false
# CHANGELOG.md → [Unreleased] başlığını [3.1.0] — YYYY-MM-DD olarak rename.

# 2) Değişiklikleri commit + push.
git add pom.xml CHANGELOG.md && git commit -m "chore: bump version to 3.1.0"
git push

# 3) Annotated tag at + push (release workflow tetiklenir).
git tag -a v3.1.0 -m "Release v3.1.0"
git push origin v3.1.0
```

Workflow şunları üretir ve **DRAFT** olarak GitHub Release'e attach eder
(yayınlamadan önce notları gözden geçirmek için draft):

- `mersel-dss-agent-signer-api-X.Y.Z.jar` — executable fat-jar
  (`MERSEL_AGENT_UPDATE_ASSET_PATTERN` ile uyumlu; tray otomatik
  güncelleme bunu bekler)
- `mersel-dss-agent-signer-api-X.Y.Z-bom.xml` ve `-bom.json` — CycloneDX
  SBOM (schema v1.6)
- `SHA256SUMS.txt` — tüm asset'lerin SHA-256 checksum'ı

Workflow tag'in `pom.xml` sürümü ile eşleştiğini doğrular; eşleşmezse build
durur (drift koruması). README'de hard-coded jar sürüm referansı da
yasaktır — `X.Y.Z` placeholder dışına çıkılırsa workflow kırar.

### Prerelease (release candidate) yayınlamak

Beta / RC sürümler için tag suffix'i kullan. Workflow tag'de `-` görürse
GitHub Release'i otomatik **prerelease** olarak işaretler ve tray
güncelleme akışı (`MERSEL_AGENT_UPDATE_PRERELEASE=false` default) bu
release'i son kullanıcıya **göstermez** — sadece `MERSEL_AGENT_UPDATE_PRERELEASE=true`
çalıştıranlar haberdar olur.

```bash
# RC sürüm bump'ı — pom.xml'e -SNAPSHOT yazma, RC ekini koy (kalıcı sürüm).
./mvnw versions:set -DnewVersion=3.1.0-rc.1 -DgenerateBackupPoms=false

git add pom.xml CHANGELOG.md
git commit -m "chore: bump version to 3.1.0-rc.1"
git push

git tag -a v3.1.0-rc.1 -m "Release candidate v3.1.0-rc.1"
git push origin v3.1.0-rc.1
```

Workflow tag regex'i kabul ettiği prerelease suffix biçimleri:
`-rc.1`, `-beta.2`, `-alpha.3`, `-snapshot.20260601`. Final yayında ekini
çıkarıp `3.1.0` olarak normal akışı tekrar et.

## Güvenlik notları

- **PIN log'a basılmaz.** Logback custom converter PIN/PAN benzeri rakam
  gruplarını otomatik maskeler; her PKCS#11 session kapanışında PIN
  array'i zero-out edilir.
- **Yanlış PIN** → HTTP 401, mesaj kasten generic (PIN-blocking saldırı
  yüzeyini düşürmek için).
- **OCSP/CRL fetch**: 5sn connect, 10sn read timeout — hang yok.
- **CORS** varsayılan olarak yalnız loopback origin'lere açık; dışarı
  açmak için `MERSEL_AGENT_CORS_ORIGINS`.
- Uygulama HTTP yayın yapar; loopback-only kullanım için TLS gerekmez. TLS
  gerekiyorsa önüne `caddy` / `nginx` / `traefik` koy.

Güvenlik açığı bildirmek için: [SECURITY.md](SECURITY.md).

## Yol haritası

- [ ] PKIX path validation (kök CA'lara karşı zincir doğrulama)
- [ ] OCSP signature verification
- [ ] XAdES-T / XAdES-X-L (timestamping) — e-Defter LTV zorunluluğu için
- [ ] PAdES-B-LT / PAdES-LTV (DSS dict) — Adobe Acrobat long-term
      validation
- [ ] CAdES desteği (e-İrsaliye / e-Reçete varyantları için)
- [ ] PKCS#11 session pooling
- [ ] JDK 17+ hattı (reflection'sız PKCS#11) — bu kalemle birlikte
      native installer'lar (jpackage MSI / PKG / DEB) açılır; şu an JDK 8
      runtime constraint'i jpackage'ın modular runtime gereksinimi ile
      uyumsuz
- [ ] CSRF guard (`X-Requested-By` header zorunluluğu) + per-terminal
      rate-limit (PIN-blocking koruması)

## Server-Signer ile ilişki

| Boyut | server-signer-api | agent-signer-api (bu) |
|-------|-------------------|-----------------------|
| Çalıştığı yer | Sunucu (Linux container) | Kullanıcı makinesi (Win/Mac/Linux) |
| Key storage | PFX dosyaları / Software HSM | PKCS#11 akıllı kart |
| API kontratı | Ortak `ErrorModel`, benzer endpoint isimleri | (eşit) |

## Sorun giderme

İlk durak her zaman `GET /smartcard/diagnostics`. Yanıt yapısı PC/SC
ortamı (OS, native lib yolu, provider), terminal listesi ve kart
durumlarını tek bir JSON olarak verir.

### Kart algılanmıyor

| Belirti | Olası sebep | Çözüm |
|---|---|---|
| `pcscProvider: (yüklenemedi)` | Native PC/SC yüklenemedi | macOS: SIP/yetki kontrol. Linux: `pcsc-lite` paketi + `pcscd` servisi. Gerekirse `MERSEL_AGENT_PCSC_LIBRARY` ile manuel path |
| `terminalCount: 0` | Okuyucu bağlı değil / tanınmıyor | macOS `system_profiler SPUSBDataType`, Linux `pcsc_scan`, Windows Aygıt Yöneticisi |
| `cardPresent: false` | Kart takılı değil | Okuyucu LED'i ve kartın oturumunu kontrol et |
| `matchedCardType: null` | ATR tanınmadı | GitHub'da issue aç: ATR + üretici/model bilgisini ekle |

### Sertifika listelenmiyor — kart tanıma akışı

Uygulama kart vendor'unu dört katmanlı bir akışta çözer; ilk başarılı
katman PKCS#11 lib'ini seçer:

1. **Manuel override** — request'te `cardType=…` veya `pkcs11LibraryPath`.
2. **ATR exact match** — `smartcard-config.xml`'de bilinen ATR.
3. **Vendor parmak izi (regex)** — örn. AKIS NES kartlarının bütün
   gelecek sürümleri otomatik tanınır.
4. **PKCS#11 module probe** — diskteki vendor lib'leri sırayla
   slot'a denenir.

Hepsi başarısız olursa uygulama **HTTP 503** ile `cardTypeCandidates`
listesi + `userSelectionRequired: true` döner; frontend kart tipi
seçtiren bir modal açar ve seçimi `cardType` query param'ı olarak geri
gönderir.

```ts
async function listCerts(terminalName: string) {
  let res = await fetch(`/smartcard/certificate?terminalName=${terminalName}`);
  if (res.status === 503) {
    const err = await res.json();
    if (err.userSelectionRequired) {
      const cardType = await showCardTypePicker(err.cardTypeCandidates);
      if (!cardType) return;
      res = await fetch(`/smartcard/certificate?terminalName=${terminalName}&cardType=${cardType}`);
    }
  }
  return res.ok ? res.json() : Promise.reject(await res.json());
}
```

> **UX patterni:** PIN'i yalnız imzalama ekranında sor. Pazardaki
> yerleşik çözümlerin çoğu PIN'i en başta isteyip kullanıcıyı yorar;
> biz iki adımlı flow ile (kart tak → listele → seç → PIN gir → imzala)
> hem UX hem güvenlik kazanırız.

### PKCS#11 sürücüsü yok

Kart algılandı ama `/smartcard/certificate` 503 + `PKCS11_LIBRARY_NOT_FOUND`
dönüyorsa sürücü kurulu değildir. Yanıt body'sinde `cardType`,
`requiredLibrary`, `searchedPaths` ve `downloadHint` (vendor indirme
sayfası) alanları gelir. Sürücüyü kurup uygulamayı yeniden başlatın. Özel
konuma kurduysanız `MERSEL_AGENT_EXTRA_LIB_PATH` ile path ekleyebilirsiniz.

### Desteklenen kartlar ve indirme rehberi

| Kart tipi | Üretici / Distribütör | İndirme |
|---|---|---|
| **AKIS / AKIS_KK / ATIK** | TÜBİTAK Kamu SM | [Kamu SM PKCS#11](https://kamusm.bilgem.tubitak.gov.tr/islemler/uretici_pkcs11_kart_yazilimlari.jsp) |
| **DIRAKHSM / TKART** | TÜBİTAK | [Kamu SM PKCS#11](https://kamusm.bilgem.tubitak.gov.tr/islemler/uretici_pkcs11_kart_yazilimlari.jsp) |
| **ALADDIN / SafeNet eToken** | Thales (Gemalto SafeNet) | [Thales SafeNet SAC](https://safenet.gemalto.com/sas/sac-download/) |
| **GEMALTO / GEMPLUS** | Thales | [Thales Support](https://supportportal.thalesgroup.com/csm) |
| **SAFESIGN** | A.E.T. Europe B.V. | [SafeSign IC](https://www.aeteurope.com/products/safesign-identity-client/) |
| **NETID** | SecMaker | [SecMaker Net iD](https://secmaker.com/en/products/net-id/) |
| **KOBIL** | KOBIL | [KOBIL IAM](https://www.kobil.com/products/identity-access-management/) |
| **CARDOS** | Atos | [Atos CardOS](https://atos.net/en/solutions/cyber-security/data-protection-and-governance/cardos) |
| **ACS** | Advanced Card Systems | [ACS Drivers](https://www.acs.com.hk/en/drivers/) |
| **NCIPHER / nShield** | Entrust | [Entrust nShield](https://www.entrust.com/products/hsm/nshield) |
| **SAFENET Luna** | Thales | [Thales Support](https://supportportal.thalesgroup.com/csm) |
| **UTIMACO** | Utimaco | [Utimaco HSM](https://utimaco.com/products/categories/general-purpose-hsm) |
| **PROCENNEHSM** | Procenne | [Procenne HSM](https://www.procenne.com.tr/cozumler/donanim-guvenlik-modulu/) |
| **OPENDNSSOFTHSM** | OpenDNSSEC | [SoftHSMv2 (geliştirici)](https://github.com/opendnssec/SoftHSMv2/releases) |

Tablo dışında bir kart algılanmıyorsa GitHub'da issue açın — ATR +
üretici / model bilgisini ekleyin; `smartcard-config.xml`'e ekleyip
yeni sürüm yayınlarız.

## Katkıda bulunmak

[CONTRIBUTING.md](CONTRIBUTING.md) — branch politikası, commit
konvansiyonu, PR şablonu ve kodlama standartları.

Lisans ve marka çerçevesi yukarıda [Bağımsızlık ve nötralite](#bağımsızlık-ve-nötralite)
ve [Lisans ve marka](#lisans-ve-marka) bölümlerinde anlatılmıştır.
