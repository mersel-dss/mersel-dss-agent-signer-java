# `resources/test-certs/` — Kamu SM test sertifikaları

Bu dizin, Kamu Sertifikasyon Merkezi (Kamu SM) test ortamında **publicly
published** mali mühür test sertifikalarını barındırır. Repo'ya commit edilirler
çünkü test suite'i bunlara bağımlıdır.

> Üretim mali mühür PFX'i bu repo'ya **asla** girmez — production'da
> HSM/PKCS#11 üzerinden referans alınır. Burada bulunan PFX'ler test
> dünyasında public olarak yayımlanmış sertifikalardır; şifreleri dosya
> adının son segmentinde **bilerek açıkta** tutulur.

## Naming convention

```
{kurum}_{algo}@{domain}_{password}.pfx
```

- `kurum` — `testkurum01`, `testkurum02`, ...
- `algo` — `rsa2048` veya `ec384`
- `domain` — Kamu SM ZIP'inde geçen sahiplik adı (`test.com.tr`, `sm.gov.tr`)
- `password` — PKCS#12 password (son `_` ile başlayan segment)

Alias her PFX için sabit: `"1"` (`PfxTestKey.DEFAULT_ALIAS`).

## Mevcut envanter

| Dosya | Algoritma | Notlar |
|-------|-----------|--------|
| `testkurum01_rsa2048@test.com.tr_614573.pfx` | RSA-2048 | PAdES SHA-256/RSA için |
| `testkurum02_ec384@test.com.tr_825095.pfx`   | EC-P384  | PAdES SHA-384/ECDSA için |

## Kaynak

PFX'ler `/Users/erdembas/mersel-dss-server-signer-java/resources/test-certs/`
deposundan kopyalanmıştır. Yeni sertifika türleri (revoked/expired/suspended)
gerekirse aynı kaynaktan eklenebilir — bkz. o repo'daki README.

## Şifrenin dosya adında olmasının nedeni

`PfxTestKey` enum constructor'ı dosya adının son `_` segmentinden PKCS#12
şifresini parse eder (yani enum'a şifre **yazmaz**). Bu yaklaşım yeni PFX
eklemek için kod değişikliği gerektirmez ve test sertifikaları zaten public
olduğu için güvenlik gerekçesi yoktur.
