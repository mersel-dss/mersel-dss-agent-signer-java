# Katkıda Bulunma Rehberi

Mersel DSS Agent Signer'a katkı yapmak istediğin için teşekkürler. Bu döküman
hem PR sürecini hem de kodlama standartlarımızı toparlar; lütfen ilk PR'ından
önce hızlıca üzerinden geç.

## Kısa özet

1. Issue aç (yoksa) ve yapacağın değişikliği açıkla — özellikle akıllı kart
   vendor ekleme veya OCSP/CRL yolu gibi geniş çaplı işler için.
2. Feature branch aç: `feat/<short-topic>`, `fix/<short-topic>`,
   `chore/<short-topic>`.
3. Commit'leri [Conventional Commits](#commit-konvansiyonu) ile yaz.
4. `mvn verify` lokalde geçsin (yeşil testler, JaCoCo eşiği aşılmasın).
5. `mvn spotless:apply` çalıştır → diff yoksa CI'da `spotless:check` geçer.
6. PR aç — PR template'i doldur, ilgili issue'yu link'le.

## Geliştirme ortamı

- **JDK 1.8** (JDK 9+ `sun.security.pkcs11.SunPKCS11` ctor'una reflection
  erişimi kapatıyor — bu repo Spring Boot 2.7 + Java 8 hattında).
- **Maven 3.6.3+**.
- Donanım token'ı **gerekmez** — testler Kamu SM'in publicly published
  PFX'leri ile çalışır.

İlk derleme:

```bash
mvn -DskipTests compile   # hızlı sanity
mvn test                  # birim testler
mvn verify                # + JaCoCo coverage gate
```

## Kodlama standartları

### Format

- **Google Java Format**, Spotless ile yönetilir.
- Lokalde otomatik düzeltmek için: `mvn spotless:apply`.
- CI'da `mvn spotless:check` PR'ı kırarsa, yukarıdaki komutu çalıştır ve
  diff'i commit'le.
- 4-space indent, max satır uzunluğu Spotless default'u (100). Tab kullanma.

### Paket düzeni

Yeni sınıflar `io.mersel.dss.agent.api.*` altında olmalı:

```
io.mersel.dss.agent.api
├── controllers/   # REST controller'lar
├── services/      # iş mantığı (alt paketlere böl)
├── dtos/          # HTTP request DTO'ları (input)
├── models/        # HTTP response model'leri (output) + ErrorModel
├── exceptions/    # SignerException hiyerarşisi
├── config/        # @Configuration sınıfları
└── util/          # framework-agnostic yardımcılar
```

3. parti kaynak (`src/main/java/tr/com/cs/**`) **dokunulmaz** —
binary-uyumlu reverse-engineered vendor SDK'sıdır. Düzeltme gerekirse
adapter sınıfı yaz.

### Exception handling

- Her domain hatası `SignerException` alt sınıfı olmalı; bir `errorCode`
  taşıyan constructor üzerinden inşa edilmeli.
- `GlobalExceptionHandler` zaten HTTP statü map'lemesini yapıyor — yeni
  exception sınıfı eklerken handler da güncellenmeli.
- Mesajlar **Türkçe**, son kullanıcıya gösterilecek tonda; PIN/PAN gibi
  hassas veri **asla** mesajda olmamalı.

### Logging

- SLF4J kullan (`LoggerFactory.getLogger(...)`).
- Mesajlarda `{}` placeholder; string concat etme.
- PIN, X.509 private key veya OCSP/CRL ham byte'ları **asla** log'a
  basılmaz.
- `%mskmsg` Logback converter ek savunma katmanıdır; ilk savunma "log'a
  KOYMA" prensibidir.

### Test

- JUnit 5 (`org.junit.jupiter`).
- Mockito 4.x.
- PFX gerektiren testler için `PfxTestKey.isAvailable()` ile
  `Assumptions.assumeTrue(...)` kullan — repo'da PFX yoksa test
  graceful skip yapsın.
- Yeni `services/` sınıfı eklenirse karşılığında test sınıfı zorunlu
  (line coverage gate'i düşürmeyelim).

### Smartcard config eklemek

Yeni bir kart vendor'ı için `src/main/resources/smartcard-config.xml`'e
`<card-type>` bloğu ekle:

- `name` — büyük harf vendor adı (`AKIS`, `SAFENET`, …)
- `<lib>` — bare PKCS#11 lib adı (uzantısız, `lib` öneksiz)
- `<lib32>` / `<lib64>` — 32/64-bit ayrımı varsa
- `<lib-alt>` — alternatif case varyantı (Linux case-sensitive)
- `<atr>` — bilinen ATR'ler (HEX, boşluksuz, büyük harf)
- `<atr-pattern regex="...">` — historical-bytes regex (vendor parmak izi)
- `<hint os="windows|macos|linux">` — tipik kurulum dizini

## Commit konvansiyonu

[Conventional Commits 1.0.0](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

`type`:

| Tip | Ne için |
|---|---|
| `feat` | Yeni özellik |
| `fix` | Bug düzeltme |
| `docs` | Sadece dokümantasyon |
| `refactor` | Davranış değiştirmeyen yeniden düzenleme |
| `perf` | Performans iyileştirmesi |
| `test` | Test ekleme/değiştirme |
| `build` | Build sistemi (pom.xml, plugin) |
| `ci` | CI yapılandırması |
| `chore` | Bunların hiçbiri (sürüm yükseltme, dosya yeniden adlandırma vb.) |

`scope` (opsiyonel): `pades`, `xades`, `smartcard`, `pkcs11`, `gib`,
`security`, `logging`, …

**Örnek:**

```
fix(logging): SensitiveMaskingConverter sınıf adını doğru pakete bağla

logback-spring.xml içindeki conversionRule yanlış paketi gösteriyordu
(cloud.mersel.* → io.mersel.dss.agent.api.util.*); runtime'da
ClassNotFoundException ile %mskmsg geçişi başarısız oluyordu.

Refs: #42
```

## PR check-list

- [ ] `mvn verify` lokalde yeşil
- [ ] `mvn spotless:check` temiz
- [ ] Yeni kod için test eklendi (veya gerekçe açıklandı)
- [ ] README / SECURITY / CHANGELOG güncellendi (gerekiyorsa)
- [ ] Commit mesajları Conventional Commits formatında
- [ ] Issue link'i veya PR description'da gerekçe var
- [ ] Hassas veri (PIN, gerçek sertifika, gerçek key) commit'lenmedi
- [ ] 3. parti kaynak (`tr/com/cs/**`) değiştirilmedi (ya da gerekçe var)

## Davranış kuralları

Bu projeye katkı vermek ile [Code of Conduct](CODE_OF_CONDUCT.md)
kurallarını kabul etmiş olursun.
