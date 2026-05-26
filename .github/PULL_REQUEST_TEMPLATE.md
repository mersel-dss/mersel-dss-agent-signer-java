<!--
  PR'ın için teşekkürler. Aşağıdakileri doldur — her satır 30 saniye sürer
  ama review'ı saatlerle hızlandırır.
-->

## Özet

<!-- Bir paragrafta: ne yapıyor, neden? İlgili issue varsa link'le (Fixes #123). -->

## Değişiklik tipi

- [ ] feat — yeni özellik
- [ ] fix — bug düzeltme
- [ ] docs — sadece dokümantasyon
- [ ] refactor — davranış değişmiyor
- [ ] perf — performans
- [ ] test — test ekleme/değiştirme
- [ ] build / ci / chore

## Davranışsal etki

<!--
  Davranış değişiyor mu? Cevap "Evet" ise mevcut entegrasyonu kıracak
  herhangi bir kontrat değişikliği var mı? (endpoint path, request
  parametre adı, response field adı, error code...)
-->

- [ ] Hayır, davranış aynı kalıyor
- [ ] Evet (aşağıda detay ver):

## Test planı

<!--
  Manuel mi otomatik mi test ettin? Yeni testler eklediysen kapsamı yaz.
  PFX/akıllı kart gerektiren senaryoları nasıl çalıştırdın?
-->

- [ ] `mvn verify` lokalde yeşil
- [ ] `mvn spotless:check` temiz
- [ ] Yeni testler eklendi (varsa)
- [ ] PFX-bağımlı testler graceful skip ediyor (`Assumptions.assumeTrue`)

## Güvenlik kontrol-listesi

- [ ] PIN/X.509 private key/PIN-içeren PFX commit edilmedi
- [ ] Hassas veri yeni log mesajlarına sızmadı
- [ ] OCSP/CRL davranışı değişmedi (değiştiyse SECURITY.md güncellendi)
- [ ] CORS politikası değişmedi (değiştiyse README + SECURITY.md güncellendi)

## Ek notlar (opsiyonel)

<!-- Reviewer'a hatırlatmak istediğin bir şey, çelişkili karar, üzerinde
     tartışmak istediğin design noktası... -->
