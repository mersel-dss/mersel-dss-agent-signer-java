# Mersel Marka Kullanım Politikası

> İngilizce karşılığı için bu sayfanın altına bakın — *English version
> below.*

**"Mersel"**, **"Mersel DSS"**, **"Mersel DSS Agent Signer"** sözcük
markaları, **Mersel logo bloğu (lockup)**, **marka aksanı (renkli ince
çizgi)** ve `**mersel.io*`* alan adı (birlikte "**Marka(lar)**")
Mersel DSS'ye aittir. Markalar, 6769 sayılı Sınai Mülkiyet Kanunu
(SMK) ve uluslararası anlaşmalar kapsamında korunmaktadır.

`[LICENSE](LICENSE)` dosyasında bulunan **Apache Lisansı 2.0 + Mersel
Marka Atıf Eki** (Brand Attribution Addendum), Apache Lisansı 2.0'nin
4. Maddesinin son paragrafında verilen "additional terms" yetkisiyle
eklenmiş tek bir bütün olarak yazılımın **kaynak koduna** uygulanan
bir telif/dağıtım lisansıdır; Markalar üzerinde, bu doküman ile LICENSE
Ek'inde sayılan kullanım biçimleri dışında size **hiçbir** kullanım hakkı
vermez. Marka koruması, lisansın permissive (serbest) doğasından
bağımsız ve onunla paralel işler. Korunması zorunlu atıfların listesi
`[NOTICE](NOTICE)` dosyasında, sözleşmesel arka plan ise LICENSE Ek'in
Önsöz'ünde ayrıntılandırılır.

---

## Özetle (TL;DR)

> **Kaynakta özgürsünüz; derlenmiş jar'ın çalışan arayüzünde Mersel
> atıfları yerinde kalır.** Bu satır, politikanın özüdür.

- **Kaynakta özgürsünüz.** Yazılımı fork edebilir, kapalı kaynaklı bir
ürüne gömebilir, kendi markanız altında **compound** bir başlık ile
ticari olarak satabilir, barındırılan bir hizmet (SaaS) olarak
sunabilirsiniz. Kaynağı paylaşmak ya da kendi değişikliklerinizi
açık kaynak yapmak zorunda değilsiniz.
- **Derlenmiş JAR/uygulamada üç şey değiştirilemez.** Yazılım'ı son
kullanıcıya hangi adla, hangi paketleyiciyle, hangi mimaride
dağıtırsanız dağıtın — çalışan uygulamanın UI'ında şu Mersel
atıfları **görünür, okunabilir ve işlevsel olarak orijinaline
eşdeğer** biçimde kalmak zorundadır:
  1. **Splash penceresi:** "MERSEL DSS" marka işareti + marka aksanı
    - "Agent Signer" başlığı.
  2. **Ana pencere (MainWindow) üstü:** Mersel banner / logo bloğu.
  3. **Ana pencere altbilgisi (footer):**
    *"Türkiye e-İmza süreçleri için mersel.io tarafından açık kaynak
     olarak geliştirilmiştir."* ibaresi (makul çevirileri dahil) ve
     aktif `mersel.io` köprüsü.
- **Yeniden adlandırma yoluyla atıfları silme istisnası YOKTUR.**
"Acme Signer" gibi Mersel referansı içermeyen tamamen yeni bir isim
altında dağıtmak **yasaktır**; "Mersel DSS Agent Signer — Acme
Edition" gibi compound naming ise serbesttir.
- **Türev Eser dağıtırken ek olarak Modifikasyon Bildirimi
zorunludur.** UI'da görünür bir yerde "Bu sürüm [Adınız] tarafından
değiştirilmiştir; orijinal yazar Mersel DSS bu sürümü
onaylamamıştır." tarzı bir bildirim göstermeniz gerekir
(LICENSE Ek § 3).
- `**mersel.io` markasını kendi kimliğiniz yapamazsınız.** Yukarıdaki
üç UI bileşeninin korunması, "Mersel" adını/logosunu/`mersel.io`
alan adını kendi ürününüzün veya kuruluşunuzun kimliği olarak
(web siteniz, sosyal medya hesabınız, ürün etiketiniz, alan adınız
vb.) kullanma hakkı doğurmaz.

---

## Korunan unsurlar — "Marka Atıfları"

LICENSE Ek § 1'de tanımlanan ve § 2 uyarınca her kullanımda görünür,
okunabilir ve işlevsel olarak orijinaline eşdeğer biçimde korunmak
zorunda olan üç UI bileşeni:

- **(a) Açılış (splash) penceresi:** "MERSEL DSS" marka işareti, marka
aksanı (renkli ince çizgi) ve ürün başlığı ("Agent Signer").
- **(b) Ana pencere (MainWindow) üst kısmı:** Mersel banner / logo
bloğu (lockup).
- **(c) Ana pencere altbilgi (footer) satırı:**
  > "Türkiye e-İmza süreçleri için mersel.io tarafından açık kaynak
  > olarak geliştirilmiştir."
  makul çevirileri dahil, ve `https://mersel.io` adresine giden etkin
  köprü (hyperlink).

Bu üç bileşen, Apache 2.0'nin permissive yapısı karşılığında talep
edilen orantılı **görünürlük karşılığını** (consideration) oluşturur:
köken (provenance) belirsizliğini önler, güvenlik bildirim kanalını
son kullanıcıya tek tık mesafede tutar ve ekosisteme yatırılan
kümülatif emeğin kamusal izlenebilirliğini korur. Ayrıntılı gerekçe
LICENSE Ek Önsöz'ünde, son kullanıcıya yönelik özet ise NOTICE
dosyasındaki "Marka Atıfları — Korumalı Bildirimler" bloğunda yer
alır.

---

## İzin gerektirmeyen kullanımlar — *serbest*

Aşağıdaki kullanımlar için ayrıca yazılı izin almanıza gerek yoktur:

- Yukarı yöndeki (upstream) projeye atıfta bulunmak için Markaları
ad olarak kullanmak: blog yazıları, karşılaştırmalı analizler,
akademik çalışmalar, uyumluluk ifadeleri ("Mersel DSS Agent
Signer ile uyumlu çalışır", "Mersel DSS Agent Signer üzerinde
test edildi"). Bu kullanım hukuken **tanımlayıcı dürüst kullanım /
nominative fair use** sayılır.
- Mersel DSS tarafından yayımlanan **değiştirilmemiş resmi
yapıları** (release binary'lerini) Markalar ve Marka Atıfları
yerinde olacak şekilde dağıtmak.
- LICENSE'ın gerektirdiği ürün-içi **Marka Atıfları'nı**
görüntülemek (zaten korumak zorundasınız).
- Yazılımı kendi kurumunuz içinde dahili (in-house / enterprise)
amaçla çalıştırmak. **Not:** Dahili kullanımda dahi LICENSE Ek § 2
uyarınca Marka Atıfları'nın UI'da yerinde kalması zorunludur; bu
istisna yalnızca ek bir yazılı izne tabi olmadığınız anlamına
gelir.

---

## Önceden yazılı izin gerektiren kullanımlar — *yasak*

Aşağıdaki kullanımlar Mersel DSS'nin önceden yazılı izni olmadan
**yapılamaz**:

- "Mersel", "Mersel DSS" veya karıştırılma ihtimali yaratan
benzer bir adı, bir **fork**, türev ürün, ücretli dağıtım,
barındırılan hizmet (SaaS), pazaryeri listelemesi veya ticari
paket adı olarak kullanmak (compound naming ile ilgili istisna
için aşağıdaki "Değişiklik ve Türev Eserler" bölümüne bakın).
- Mersel logosunu, marka aksanını, "mersel.io" sözcük markasını
veya bu alan adının görsel temsillerini kendi ürün arayüzünüzde,
pazarlama materyalinizde, kurulum sihirbazınızın görsellerinde,
alan adınızda veya sosyal medya hesap adınızda kullanmak.
- Var olmayan bir destekleme, sponsorluk, sertifikasyon, denetim
veya Mersel DSS ile ticari/teknik ortaklık izlenimi yaratmak
("Mersel ortağı", "Mersel onaylı", "Mersel sertifikalı" vb.).
- Markaları yanıltıcı, kötüleyici veya Mersel DSS'nin itibarına
zarar verecek biçimlerde kullanmak.

---

## Değişiklik ve Türev Eserler ("Modifications and Derivatives")

Yazılım'ı değiştirip dağıtmakta — kapalı kaynaklı bir ürüne
gömmek, kendi markanızla compound bir başlık altında satmak,
hosted servis sunmak vb. — serbestsiniz. **Ancak Marka Atıfları
hiçbir koşulda kaldırılamaz.** "Kendi markanıza göre tamamen
yeniden adlandırma" (rebrand) yoluyla atıfları silme istisnası
**YOKTUR**.

Pratik kurallar:

1. **Marka Atıfları yerinde kalır.** Yukarıda (a), (b) ve (c)
  maddelerinde sayılan üç bileşen — değişiklik yapsanız da, kendi
   markanızı eklemiş olsanız da, ürünü farklı bir adla dağıtsanız da
   — görünür, okunabilir ve işlevsel olarak orijinaline eşdeğer
   biçimde korunmak zorundadır (LICENSE Ek § 2).
2. **Kendi adınızı/markanızı ekleyebilirsiniz**, ancak Mersel
  adı/logosunun yanına eklersiniz, yerine değil. Örneğin
   "Mersel DSS Agent Signer — Acme Edition" gibi compound naming
   serbest; "Acme Signer" gibi Mersel referansı içermeyen
   adlandırma ise yasaktır.
3. **Modifikasyon Bildirimi zorunludur** (LICENSE Ek § 3).
  Türev Eser'in kullanıcı arayüzünde görünür ve makul biçimde
   erişilebilir bir yerde (ana pencerenin altbilgisi, ayarlar/
   yardım ekranının ilk satırı veya başlangıç ekranı), son
   kullanıcıya:
  - Yazılım'ı sizin değiştirdiğinizi,
  - Türev Eser'in sizin kimliğinizi taşıdığını (varsa kendi marka
  adınız/sürümünüz),
  - Mersel DSS'nin Türev Eser'i ne ürettiğini ne onayladığını ve
  sonuçlardan sorumlu olmadığını
   net biçimde bildirmek zorundasınız. Önerilen örnek metin:
  > "Bu sürüm [Adınız/Sürümünüz] tarafından değiştirilmiştir.
  > Orijinal yazar Mersel DSS bu sürümü onaylamamıştır ve bu
  > sürümden kaynaklanan sonuçlardan sorumlu değildir."
   Ayrıca Apache Lisansı 2.0 § 4(b) uyarınca her değiştirilen
   kaynak dosyada "bu dosya değiştirilmiştir" şeklinde açıklayıcı
   bir not bırakmalısınız. Yazılım'ı **çağıran veya gömen başka bir
   uygulamanın** arayüzü ise son kullanıcının Mersel DSS ürünü ile
   karıştırmasını önleyecek biçimde açıkça farklı tasarlanmalıdır
   (LICENSE Ek § 3(c)).
4. **Onay/Sponsorluk imasında bulunamazsınız** (LICENSE Ek § 4).
  Marka Atıfları'nın bulundurulması Mersel DSS'nin onay,
   destek, sponsorluk, kalite güvencesi, denetim veya ortaklık
   ima etmez; reklam, pazarlama, satış metinleri, ürün etiketi
   ve son kullanıcı dokümantasyonunuzda bu tür bağlantılardan
   kaçınmalı, gerekirse ilişkinin gerçek niteliğini açıklayan
   ek bir feragatname eklemelisiniz.
5. **Kaynak kod header'ları ve `NOTICE` dosyası** Apache 2.0
  § 4(c) ve 4(d) gereği zaten korunur. NOTICE dosyası içerik
   olarak değiştirilemez; kendi atıflarınızı yalnızca ek olarak
   ekleyebilirsiniz.
6. **Dokümantasyonunuzda yukarı yön kaynağı bildirin**:
  Türev Eser'in upstream Mersel DSS Agent Signer projesinden
   türetildiğini ve depo bağlantısını açıkça belirtin (bu
   "nominative fair use" sayılır, ek izin gerektirmez).

> Marka Atıfları'nın korunması bir kullanım izni DEĞİL, zorunlu
> bir ATIFTIR. Bu yükümlülüğü yerine getirmeniz, "Mersel"
> markası üzerinde size herhangi bir hak doğurmaz; Mersel
> markası, logosu veya `mersel.io` alan adını kendi
> ürününüzün/kuruluşunuzun kimliği olarak (web siteniz, sosyal
> medya hesabınız, ürün etiketiniz vb.) kullanamazsınız.

---

## Esaslı ihlal sayılan davranışlar

LICENSE Ek § 2 (i)–(viii) uyarınca aşağıdaki davranışlar — sınırlayıcı
olmamak üzere — lisans sözleşmesinin **esaslı ihlali** sayılır:

1. Bir Marka Atıfı'nın tamamen kaldırılması veya gizlenmesi.
2. Atıfın, kullanıcının normal görüş alanından çıkarılacak şekilde
  yer değiştirmesi.
3. Atıfın okunmayacak kadar küçültülmesi veya kontrast/renk yoluyla
  görsel olarak silikleştirilmesi.
4. `mersel.io` köprüsünün devre dışı bırakılması, başka bir adrese
  yönlendirilmesi veya işlevinin engellenmesi.
5. Atıfın içeriğinin başka bir markayla, sloganla veya kredi
  ifadesiyle değiştirilmesi.
6. Atıfın yalnızca "Hakkında" diyaloğu, gizli menü veya benzer
  şekilde son kullanıcının normal akışından izole edilmiş bir yere
   taşınması.
7. Yazılım'ı çağıran ancak Marka Atıfları'nı kendi sürecinde
  göstermeyen bir başka uygulamanın içine gömmek (Yazılım kendi
   süreç sınırı içinde gösterimini yapmak zorundadır).
8. Yazılım'ı, Marka Atıfları'nın gösteriminin teknik olarak
  imkânsız olduğu bir biçime dönüştürmek (örn. headless'a
   indirgemek) ve bu dönüştürmenin ardından Yazılım'ı son
   kullanıcıya görsel arayüzlü olarak yeniden sunmak.

---

## SBOM ve uyumluluk metadata'sı

Otomatik tarayıcılar (FOSSA, ScanCode, Black Duck, Snyk, vb.) için
önerilen SPDX referansı:

```
LicenseRef-Mersel-Brand-Attribution
(base: Apache-2.0)
```

`pom.xml` `<licenses>` bloğunda lisans adı
`Apache-2.0 WITH Mersel-Brand-Attribution-Addendum` olarak
bildirilir. CycloneDX SBOM çıktıları (`target/bom.xml` /
`target/bom.json`) bu metadata'yı release artefaktlarıyla birlikte
yayar; downstream uyumluluk denetimi için release sayfasındaki
`*-bom.*` dosyalarına bakın.

---

## İletişim


| Konu                                                                | Adres                        |
| ------------------------------------------------------------------- | ---------------------------- |
| Marka kullanım izni talebi, politika hakkında soru, ihlal bildirimi | `**legal@mersel.io**`        |
| Ortaklık / resmi entegrasyon başvurusu                              | `**partnership@mersel.io**`  |
| Güvenlik açığı bildirimi                                            | `[SECURITY.md](SECURITY.md)` |


İhlal tespit edildiğinde Mersel DSS, sırasıyla (i) iyi niyetli
uyarı (cease-and-desist), (ii) ilgili platformun (GitHub, paket
deposu, mağaza) marka şikâyet kanalı, ve (iii) Türkiye'de SMK
6769 kapsamında yargı yolu adımlarını kullanma hakkını saklı
tutar.

## Uygulanacak hukuk

LICENSE Ek § 7 ile uyumlu olarak, bu politikadan kaynaklanan veya
bu politikayla ilgili tüm uyuşmazlıkların esasına Türkiye
Cumhuriyeti hukuku uygulanır; yetkili mahkemeler İstanbul
(Çağlayan) mahkemeleri ve icra daireleridir.

---

# Mersel Trademark Policy (English)

The names **"Mersel"**, **"Mersel DSS"**, **"Mersel DSS Agent
Signer"**, the **Mersel logo lockup**, the **brand accent rule**,
and the `**mersel.io`** domain (collectively, the "**Marks**") are
trademarks of Mersel DSS, protected under Turkish IP law (SMK No.
6769) and applicable international agreements.

The **Apache License 2.0 with the Mersel Brand Attribution
Addendum** in `[LICENSE](LICENSE)` — attached under the
"additional terms" authority granted by Section 4 of the Apache
License 2.0 — is a copyright/distribution license over the source
code; it does **not** grant you any rights in the Marks beyond
what this document and the Addendum explicitly permit. The list
of mandatory in-product attributions lives in
`[NOTICE](NOTICE)`; the contractual rationale lives in the
Preamble of the Addendum in `LICENSE`.

## At a glance (TL;DR)

> **The source is yours; the running UI of the compiled JAR keeps
> the Mersel attributions.** This sentence is the core of the
> policy.

- **You are free in the source.** You may fork the Software, embed
it in a closed-source product, sell it commercially under a
**compound** title with your own brand, or offer it as a hosted
service (SaaS). You are not required to share your source or
open-source your modifications.
- **Three things in the compiled JAR/application cannot be
changed.** Regardless of the name, packaging, or architecture
under which you ship the Software to end users, the following
Mersel attributions must remain **visible, legible, and
functionally equivalent** in the running UI:
  1. **Splash window:** the "MERSEL DSS" brand mark + brand accent
    rule + "Agent Signer" title.
  2. **Main window header:** the Mersel banner / logo lockup.
  3. **Main window footer:** the credit line
    *"Türkiye e-İmza süreçleri için mersel.io tarafından açık kaynak
     olarak geliştirilmiştir."* (including reasonable translations
     thereof) and the active hyperlink to `mersel.io`.
- **No rebrand exception.** Distributing the Software under an
entirely new name without any Mersel reference (e.g. "Acme
Signer") is **prohibited**; compound naming such as "Mersel DSS
Agent Signer — Acme Edition" is permitted.
- **Modification Disclosure is also required for Derivative
Works.** You must display, in a visible UI location, a notice
along the lines of "This version has been modified by [Your
Name]; the original author Mersel DSS has not endorsed this
version." (LICENSE Addendum § 3).
- **You may not adopt `mersel.io` as your own identity.**
Preserving the three UI elements above does not give you the
right to use the "Mersel" name/logo/`mersel.io` domain as the
identity of your product or organization (website, social handle,
product label, domain name, etc.).

## Protected elements — "Brand Notices"

The three UI elements defined in Section 1 of the Addendum, which
Section 2 requires to remain present, visible, legible, and
functionally equivalent in every use of the Software:

- **(a) Splash window:** the "MERSEL DSS" brand mark, the brand
accent rule, and the product title ("Agent Signer").
- **(b) Main window header:** the Mersel banner / logo lockup.
- **(c) Main window footer:** the credit line
  > "Türkiye e-İmza süreçleri için mersel.io tarafından açık kaynak
  > olarak geliştirilmiştir."
  (including reasonable translations thereof) and the active
  hyperlink to `https://mersel.io`.

These three small elements form the proportionate **visibility
consideration** requested in exchange for the permissive Apache 2.0
base license: they prevent provenance ambiguity, keep the
vulnerability-disclosure channel one click away from every end user,
and preserve the public traceability of the cumulative work Mersel
DSS has invested in the ecosystem. The full rationale is in the
Preamble of the Addendum in `LICENSE`; the end-user-facing summary
is in the "Brand Notices — Mandatory" block of `NOTICE`.

## You MAY (no permission required)

- Refer to the upstream project by its name in blog posts,
comparisons, academic work, or compatibility statements
("works with Mersel DSS Agent Signer"). This is **nominative
fair use**.
- Distribute unmodified upstream releases with the Marks and
Brand Notices intact.
- Display the in-product Brand Notices as required by the
`LICENSE` (in fact, you MUST keep them).
- Operate the Software internally inside your organization for
in-house use. **Note:** internal use still requires the Brand
Notices to remain in place per Section 2 of the Addendum; this
exception only means no additional written permission is needed.

## You MAY NOT (without prior written permission)

- Use "Mersel", "Mersel DSS", or any confusingly similar name
for a fork, derivative product, paid distribution, hosted
service, marketplace listing, or commercial bundle (see
the "Modifications and Derivatives" section below for the
compound-naming carve-out).
- Use the Mersel logo, brand accent rule, or "mersel.io"
wordmark in your own product UI, marketing material, installer
chrome, documentation, domain name, or social handle.
- Imply endorsement, sponsorship, certification, audit, or any
commercial/technical partnership with Mersel DSS that does not
exist ("Mersel partner", "Mersel certified", "Mersel approved",
etc.).
- Use the Marks in misleading, disparaging, or reputation-
damaging ways.

## Modifications and Derivatives

You may modify and redistribute the Software — embed it in a
closed-source product, sell it under your own brand using a
compound title, run it as a hosted service, and so on. **However,
the Brand Notices may NOT be removed under any circumstance.**
There is **no** "rebrand exception" that allows removing the
Brand Notices by renaming the product entirely.

Practical rules:

1. **Brand Notices stay in place.** The three elements listed in
  (a), (b), and (c) above must remain visible, legible, and
   functionally equivalent in every distributed copy and Derivative
   Work, regardless of whether you modify the Software, add your own
   brand, or distribute it under a different name (LICENSE Addendum
   § 2).
2. **You may add your own name/brand**, but only alongside the
  Mersel identity, not in place of it. Compound naming like
   "Mersel DSS Agent Signer — Acme Edition" is permitted; a
   name without any Mersel reference such as "Acme Signer" is
   prohibited.
3. **Modification Disclosure is mandatory** (LICENSE Addendum
  § 3). In the Derivative Work's user interface, in a place
   visible and reasonably accessible to the end user (the footer
   of the main window, the first line of the settings/help screen,
   or the startup screen), you must clearly state that:
  - you modified the Software;
  - the Derivative Work carries your identity (your brand name /
  edition, if any);
  - Mersel DSS neither produced nor endorsed this version and
  is not responsible for its outcomes.
   Recommended example:
  > "This version has been modified by [Your Name / Edition].
  > The original author Mersel DSS has not endorsed this
  > version and is not responsible for any outcomes arising
  > from this version."
   Per Apache License 2.0 § 4(b) you must also leave a prominent
   notice in every modified source file stating that the file has
   been changed. Any **other application that calls or embeds the
   Software** must visibly distinguish its own user interface to
   prevent the end user from confusing it with the Mersel DSS
   product (LICENSE Addendum § 3(c)).
4. **No endorsement implied** (LICENSE Addendum § 4). The
  presence of the Brand Notices does not mean Mersel DSS
   endorses, sponsors, audits, or partners with your Derivative
   Work; avoid such associations in advertising, marketing, sales
   copy, product labelling, and end-user documentation, and add a
   clarifying disclaimer where necessary.
5. **Source headers and `NOTICE`** remain preserved per Apache
  2.0 §§ 4(c) and 4(d). The contents of `NOTICE` may not be
   changed; you may only append your own attribution notices
   alongside it.
6. **Disclose upstream origin** in your documentation: clearly
  state that the Derivative Work is derived from the upstream
   Mersel DSS Agent Signer project and link to the upstream
   repository (this is nominative fair use and does not require
   additional permission).

> Preserving the Brand Notices is a MANDATORY ATTRIBUTION, not
> a use license. Complying with this obligation does not grant
> you any right in the Mersel Marks; you may not use the
> Mersel name, logo, or `mersel.io` domain as the identity of
> your own product or organization (website, social handle,
> product label, etc.).

## Material breaches

Per LICENSE Addendum § 2 (i)–(viii), the following behaviours —
without limitation — constitute a **material breach** of the
license:

1. Removing or hiding any Brand Notice.
2. Relocating a Brand Notice outside the user's normal field of
  view.
3. Shrinking it below legibility or visually obscuring it through
  contrast or colour manipulation.
4. Disabling, redirecting, or otherwise impairing the `mersel.io`
  hyperlink.
5. Replacing the content of a Brand Notice with another brand,
  slogan, or credit.
6. Moving a Brand Notice into an "About" dialog, hidden menu, or
  similar isolated location separated from the user's ordinary
   flow.
7. Embedding the Software inside another application that calls
  the Software but does not display the Brand Notices within its
   own process boundary (the Software must render the Brand
   Notices within its own process).
8. Transforming the Software into a form in which the Brand
  Notices technically cannot be displayed (e.g. reducing it to
   headless mode) and then re-exposing it to end users with a
   graphical interface.

## SBOM and compliance metadata

Recommended SPDX reference for automated scanners (FOSSA,
ScanCode, Black Duck, Snyk, etc.):

```
LicenseRef-Mersel-Brand-Attribution
(base: Apache-2.0)
```

The `<licenses>` block in `pom.xml` declares the license name as
`Apache-2.0 WITH Mersel-Brand-Attribution-Addendum`. CycloneDX
SBOM artefacts (`target/bom.xml` / `target/bom.json`) carry this
metadata and are attached to every GitHub Release as
`*-bom.xml` / `*-bom.json` for downstream compliance audit.

## Contact


| Subject                                                               | Address                      |
| --------------------------------------------------------------------- | ---------------------------- |
| Trademark permission requests, policy questions, infringement reports | `**legal@mersel.io**`        |
| Partnership / official integration inquiries                          | `**partnership@mersel.io**`  |
| Security vulnerability reports                                        | `[SECURITY.md](SECURITY.md)` |


Where an infringement is detected, Mersel DSS reserves the right
to pursue, in order, (i) good-faith notice (cease-and-desist),
(ii) the trademark-complaint channel of the relevant platform
(GitHub, package registry, app store), and (iii) judicial remedy
under Turkish IP law (SMK No. 6769).

## Governing law

Consistent with Section 7 of the Addendum in `LICENSE`, the laws
of the Republic of Türkiye govern this policy and any dispute
arising out of or relating to it. The courts of Istanbul
(Çağlayan) shall have exclusive jurisdiction.