#!/usr/bin/env bash
###############################################################################
# package-dist.sh — Bundled-JRE, platforma özel TEK-DOSYA "tıkla & çalıştır"
# paketleri üretir (ZIP değil):
#
#     Windows x64/x86  →  tek .exe   (NSIS self-extracting; kurulum YOK,
#                                     çalışınca geçici klasöre açılıp gömülü
#                                     javaw ile başlar)
#     macOS x64/arm64  →  .dmg       (içinde imzalı/notarize .app; çift tık →
#                                     mount → uygulamaya çift tık)
#     Linux x64/arm64  →  .AppImage  (tek dosya; chmod +x → çift tık)
#
# Çalıştırılabilir `.jar` her pakette gizlidir; gömülü JRE her zaman kullanılır,
# son kullanıcının makinesinde ayrı Java GEREKMEZ.
#
#   runtime kaynağı:
#     - 4 hedef: Eclipse Temurin (Adoptium) JRE 8
#     - macOS arm64: Azul Zulu JRE 8 (Temurin Java 8 arm64 üretmiyor)
#
# Gerekli araçlar (yoksa o hedef güvenli .zip fallback'e düşer):
#     Windows → makensis (NSIS)   |  Linux → mksquashfs (squashfs-tools)
#     macOS   → hdiutil (yerleşik) + codesign/notarytool (imza/notarization)
#
# Kullanım:
#   scripts/package-dist.sh                 # tüm hedefler
#   scripts/package-dist.sh macos-arm64     # tek/seçili hedef(ler)
#
# Ortam değişkenleri:
#   JAR=...        (vars. target/mersel-dss-agent-signer-api.jar)
#   VERSION=...    (vars. jar manifest Implementation-Version)
#   OUT=dist       çıktı dizini
#   CACHE=...      JRE/runtime indirme önbelleği (vars. .tooling/jre-cache)
#
# macOS imzalama / notarization (yalnız macOS host'ta etkilidir; codesign yoksa
# imzasız .app üretilir, codesign varsa kimliksizken ad-hoc "seal" edilir):
#   APPLE_SIGNING_IDENTITY=...  "Developer ID Application: Ad (TEAMID)" → gerçek
#                               imza + hardened runtime + entitlements. Boşsa
#                               ad-hoc (codesign --sign -) ile bundle mühürlenir
#                               (macOS 14+ "damaged" hatasını önler).
#   APPLE_ID / APPLE_TEAM_ID / APPLE_PASSWORD  → üçü de doluysa imzalı .app
#                               notarytool ile notarize edilip stapler'lanır.
#                               (APPLE_PASSWORD = app-specific password.)
#   NOTARY_PROFILE=...          alternatif: notarytool keychain profili adı.
###############################################################################
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ---- sabitler ----
APP_NAME="Mersel DSS Agent Signer"
LAUNCHER_BASE="Mersel DSS Agent"          # başlatıcı dosya adı tabanı
LAUNCHER_SH="Mersel-DSS-Agent.sh"
BUNDLE_ID="io.mersel.dss.agent"
ICON_ICNS="etc/branding/app.icns"
ICON_ICO="etc/branding/app.ico"
ICON_PNG="etc/branding/app.png"
ENTITLEMENTS="etc/branding/entitlements.plist"

JAR="${JAR:-target/mersel-dss-agent-signer-api.jar}"
OUT="${OUT:-dist}"
CACHE="${CACHE:-.tooling/jre-cache}"

[ -f "$JAR" ] || { echo "HATA: jar yok: $JAR  (önce: ./mvnw -DskipTests package)"; exit 1; }

VERSION="${VERSION:-$(unzip -p "$JAR" META-INF/MANIFEST.MF | tr -d '\r' \
  | awk -F': ' '/^Implementation-Version:/{print $2; exit}')}"
[ -n "$VERSION" ] || { echo "HATA: sürüm okunamadı (VERSION= ile verin)"; exit 1; }
JAR_IN_BUNDLE="mersel-dss-agent-signer-api-${VERSION}.jar"

# hedef tanımı: id|jre_kind|os|arch
TARGETS_ALL=(
  "windows-x64|temurin|windows|x64"
  "windows-x86|temurin|windows|x86"
  "macos-x64|temurin|mac|x64"
  "macos-arm64|zulu|mac|aarch64"
  "linux-x64|temurin|linux|x64"
  "linux-arm64|temurin|linux|aarch64"
)

if [ "$#" -gt 0 ]; then SELECTED=("$@"); else
  SELECTED=(); for t in "${TARGETS_ALL[@]}"; do SELECTED+=("${t%%|*}"); done
fi

mkdir -p "$OUT" "$CACHE"
OUT="$(cd "$OUT" && pwd)"   # mutlak yap (relative/absolute fark etmesin)

# ---- yardımcılar ----
copy_tree() { tar -C "$1" -cf - . | tar -C "$2" -xf - ; }   # symlink/perm korur

# Sabit (pinned) JRE 8 indirme linkleri + SHA-256. Script bunları DOĞRUDAN kullanır;
# çalışma anında bir API'ye sorup "nereden indireyim" araması YAPMAZ. Bu, build'i
# deterministik + tekrarlanabilir yapar ve indirilen arşivin bütünlüğünü doğrular.
# Sürüm yükseltmek için sadece bu tabloyu (URL + sha256) güncelle. Adoptium GA
# release asset'leri ve Azul CDN'in sürümlü dosyaları değişmezdir (immutable),
# bu yüzden sha sabit kalır.
#   Temurin: https://adoptium.net   |   Azul Zulu: https://www.azul.com/downloads/
jre_pin() {  # target_id -> "URL|SHA256"
  case "$1" in
    windows-x64) echo "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u492-b09/OpenJDK8U-jre_x64_windows_hotspot_8u492b09.zip|bb25b002556afc7ef158cd95ec6270dddb3eecba69acdd7abb9d28b2e9ff0f5e";;
    windows-x86) echo "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u472-b08/OpenJDK8U-jre_x86-32_windows_hotspot_8u472b08.zip|21a2c5af684a658f1484daa85eabf4961ab9de28c0efbf31da2381d77fce3b5f";;
    macos-x64)   echo "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u492-b09/OpenJDK8U-jre_x64_mac_hotspot_8u492b09.tar.gz|e4acfc82781f0a4ec1fb9785afee41dcb4bb444655d445a2b31edbacbe6bf040";;
    macos-arm64) echo "https://cdn.azul.com/zulu/bin/zulu8.94.0.17-ca-jre8.0.492-macosx_aarch64.tar.gz|1042604675870d658da6fdf74160f095cae68c37d042e98a33a2462b89ad4108";;
    linux-x64)   echo "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u492-b09/OpenJDK8U-jre_x64_linux_hotspot_8u492b09.tar.gz|8eef3d4a837bb7a9e45d30a7579d84d5b76a4321f4376573311e6bf89e48f9b0";;
    linux-arm64) echo "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u492-b09/OpenJDK8U-jre_aarch64_linux_hotspot_8u492b09.tar.gz|d5e50cb002600007dbdfac523605d26196607fa5212db0942ef05cdce9fe2892";;
    *) return 1;;
  esac
}

sha256_of() {  # file -> hex digest
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'
  else sha256sum "$1" | awk '{print $1}'; fi
}

# JRE'yi (önbellekli, pinned URL'den, SHA-256 doğrulamalı) indir+çıkar; JAVA_HOME yaz
fetch_jre() {  # target_id
  local id="$1" dest pin url sha ext got
  dest="$CACHE/$id"
  pin="$(jre_pin "$id")" || { echo "HATA: $id için pinned JRE linki tanımlı değil" >&2; return 1; }
  url="${pin%%|*}"; sha="${pin##*|}"
  if [ ! -d "$dest/x" ]; then
    echo "    JRE indiriliyor: $id  (${url##*/})" >&2
    rm -rf "$dest"; mkdir -p "$dest/x"
    case "$url" in *.zip) ext=zip;; *) ext=tgz;; esac
    curl -fsSL "$url" -o "$dest/jre.$ext"
    got="$(sha256_of "$dest/jre.$ext")"
    if [ "$got" != "$sha" ]; then
      echo "HATA: SHA-256 uyuşmadı ($id)" >&2
      echo "      beklenen: $sha" >&2
      echo "      gelen   : $got" >&2
      rm -rf "$dest"; return 1
    fi
    if [ "$ext" = zip ]; then unzip -q "$dest/jre.$ext" -d "$dest/x"; else tar -xzf "$dest/jre.$ext" -C "$dest/x"; fi
  fi
  local javabin
  javabin="$(find "$dest/x" \( -name java -o -name java.exe \) -path '*/bin/*' | head -1)"
  [ -n "$javabin" ] || { echo "HATA: java binary yok: $id" >&2; return 1; }
  (cd "$(dirname "$javabin")/.." && pwd -P)
}

write_readme() {  # stage os
  local stage="$1" os="$2" click
  case "$os" in
    mac)     click="\"${LAUNCHER_BASE}.app\" dosyasına çift tıklayın. İlk açılışta macOS güvenlik uyarısı çıkarsa: dosyaya SAĞ TIK → \"Aç\" → \"Aç\".";;
    windows) click="\"${LAUNCHER_BASE}.exe\" (yoksa \"${LAUNCHER_BASE}.cmd\") dosyasına çift tıklayın.";;
    linux)   click="\"${LAUNCHER_SH}\" dosyasını çalıştırın (gerekirse: chmod +x \"${LAUNCHER_SH}\").";;
  esac
  cat > "$stage/OKUBENI.txt" <<EOF
${APP_NAME} — sürüm ${VERSION}
==================================================================

KURULUM GEREKMEZ. Bu paket kendi Java çalışma ortamını (JRE) içerir;
makinenizde Java kurulu OLMASINA GEREK YOKTUR.

ÇALIŞTIRMA
  ${click}

Uygulama açıldığında:
  • Masaüstünde bir pencere ve sistem tepsisinde (saatin yanında) bir
    simge belirir.
  • Yerel servis http://127.0.0.1:15212 adresinde çalışır.

ÖNEMLİ
  • "app/" klasöründeki .jar dosyasına DOĞRUDAN ÇİFT TIKLAMAYIN. O dosya
    sistemdeki (uyumsuz olabilecek) Java ile açılmaya çalışır. HER ZAMAN
    bu klasördeki başlatıcıyı kullanın.
  • "runtime/" klasörü gömülü Java ortamıdır; silmeyin/taşımayın.

Lisans: LICENSE ve NOTICE dosyalarına bakınız.
EOF
}

# Pinned AppImage type2 runtime (Linux tek-dosya .AppImage başlığı). Sürümlü değil
# (continuous) olduğundan SHA ile sabitlenir; değişirse buradan güncelle.
APPIMAGE_RT_X64_URL="https://github.com/AppImage/type2-runtime/releases/download/continuous/runtime-x86_64"
APPIMAGE_RT_X64_SHA="a2419dce47568395ae79c01ffa9a5a341dd339581352ff104d073527543177e5"
APPIMAGE_RT_ARM64_URL="https://github.com/AppImage/type2-runtime/releases/download/continuous/runtime-aarch64"
APPIMAGE_RT_ARM64_SHA="7f27a8c15bf20a2e46342ea4a977047a69d8b4d64a123fe0b5e23b20fd290c85"

dl_verify() {  # url sha out
  local url="$1" sha="$2" out="$3" got
  curl -fsSL "$url" -o "$out"
  got="$(sha256_of "$out")"
  [ "$got" = "$sha" ] || {
    echo "HATA: SHA-256 uyuşmadı: $url" >&2
    echo "      beklenen: $sha" >&2; echo "      gelen   : $got" >&2
    rm -f "$out"; return 1; }
}

# Tek-dosya üretilemediğinde (gerekli araç yok) güvenli ZIP fallback.
fallback_zip() {  # dir id
  local dir="$1" id="$2" name
  name="mersel-dss-agent-signer-${VERSION}-${id}.zip"
  rm -f "$OUT/$name"
  ( cd "$dir" && zip -ry -q "$OUT/$name" . )
  ARTIFACT="$OUT/$name"
}

# ============================ macOS → .dmg ===================================
# .app bundle'ı kurar (gömülü JRE + Dock ikonu + launcher).
build_mac_app() {  # appdir jhome
  local app="$1" jhome="$2" C="$1/Contents"
  mkdir -p "$C/MacOS" "$C/Resources" "$C/app" "$C/runtime"
  copy_tree "$jhome" "$C/runtime"
  cp "$JAR" "$C/app/$JAR_IN_BUNDLE"
  cp "$ICON_ICNS" "$C/Resources/app.icns"
  cat > "$C/MacOS/launcher" <<EOF
#!/bin/bash
DIR="\$(cd "\$(dirname "\$0")/.." && pwd)"
exec "\$DIR/runtime/bin/java" -Xdock:icon="\$DIR/Resources/app.icns" -Xdock:name="${APP_NAME}" -jar "\$DIR/app/${JAR_IN_BUNDLE}" "\$@"
EOF
  chmod +x "$C/MacOS/launcher"
  cat > "$C/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleName</key><string>${APP_NAME}</string>
  <key>CFBundleDisplayName</key><string>${APP_NAME}</string>
  <key>CFBundleIdentifier</key><string>${BUNDLE_ID}</string>
  <key>CFBundleVersion</key><string>${VERSION}</string>
  <key>CFBundleShortVersionString</key><string>${VERSION}</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleExecutable</key><string>launcher</string>
  <key>CFBundleIconFile</key><string>app.icns</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>LSMinimumSystemVersion</key><string>10.9</string>
</dict></plist>
EOF
}

# .app içindeki tüm mach-o'ları + bundle'ı imzalar (Developer ID varsa hardened
# runtime + entitlements; yoksa ad-hoc "seal" → macOS 14+ "damaged" hatasını önler).
sign_macos_app() {  # app
  local app="$1" id ents
  command -v codesign >/dev/null 2>&1 || {
    echo "    (codesign yok → macOS dışı host; .app imzasız)" >&2; return 0; }
  id="${APPLE_SIGNING_IDENTITY:-}"
  ents="$ROOT/$ENTITLEMENTS"
  if [ -n "$id" ]; then
    echo "    codesign (Developer ID): $id" >&2
    while IFS= read -r f; do
      file "$f" 2>/dev/null | grep -q 'Mach-O' || continue
      case "$f" in
        */runtime/bin/*) codesign --force --options runtime --timestamp \
                           --entitlements "$ents" --sign "$id" "$f" >/dev/null ;;
        *)               codesign --force --options runtime --timestamp \
                           --sign "$id" "$f" >/dev/null ;;
      esac
    done < <(find "$app/Contents/runtime" -type f)
    codesign --force --options runtime --timestamp --entitlements "$ents" \
      --sign "$id" "$app" >/dev/null
    codesign --verify --deep --strict --verbose=2 "$app"
  else
    echo "    codesign (ad-hoc; gerçek imza için APPLE_SIGNING_IDENTITY ver)" >&2
    codesign --force --deep --sign - "$app" >/dev/null
  fi
}

# Verilen dosyayı (.dmg) Apple'a notarize ettirip ticket'ı staple eder.
# Kimlik/credential yoksa atlanır (imzalı .app yine "sağ tık → Aç" ile çalışır).
notarize_macos() {  # file
  local f="$1"
  [ -n "${APPLE_SIGNING_IDENTITY:-}" ] || return 0
  command -v xcrun >/dev/null 2>&1 || return 0
  if [ -n "${NOTARY_PROFILE:-}" ]; then
    echo "    notarytool submit (profile: $NOTARY_PROFILE)…" >&2
    xcrun notarytool submit "$f" --keychain-profile "$NOTARY_PROFILE" --wait
  elif [ -n "${APPLE_ID:-}" ] && [ -n "${APPLE_TEAM_ID:-}" ] && [ -n "${APPLE_PASSWORD:-}" ]; then
    echo "    notarytool submit (apple-id: $APPLE_ID)…" >&2
    xcrun notarytool submit "$f" \
      --apple-id "$APPLE_ID" --team-id "$APPLE_TEAM_ID" --password "$APPLE_PASSWORD" --wait
  else
    echo "    (notarization atlandı: APPLE_ID/TEAM_ID/PASSWORD ya da NOTARY_PROFILE yok)" >&2
    return 0
  fi
  xcrun stapler staple "$f"
}

make_macos() {  # stage jhome id
  local stage="$1" jhome="$2" id="$3" app dmgsrc name
  app="$stage/${LAUNCHER_BASE}.app"
  build_mac_app "$app" "$jhome"
  sign_macos_app "$app"
  name="mersel-dss-agent-signer-${VERSION}-${id}.dmg"
  if command -v hdiutil >/dev/null 2>&1; then
    dmgsrc="$stage/dmgsrc"; mkdir -p "$dmgsrc"
    cp -R "$app" "$dmgsrc/"
    ln -s /Applications "$dmgsrc/Applications"      # sürükle-bırak kurulum (opsiyonel)
    write_readme "$dmgsrc" mac
    cp LICENSE NOTICE "$dmgsrc"/ 2>/dev/null || true
    rm -f "$OUT/$name"
    hdiutil create -volname "$APP_NAME" -srcfolder "$dmgsrc" -ov -format UDZO -quiet "$OUT/$name"
    if [ -n "${APPLE_SIGNING_IDENTITY:-}" ] && command -v codesign >/dev/null 2>&1; then
      codesign --force --timestamp --sign "$APPLE_SIGNING_IDENTITY" "$OUT/$name" >/dev/null
    fi
    notarize_macos "$OUT/$name"
    ARTIFACT="$OUT/$name"
  else
    echo "    (hdiutil yok → .dmg yerine .zip fallback; .app içeride)" >&2
    local zdir="$stage/zip"; mkdir -p "$zdir"; cp -R "$app" "$zdir/"
    write_readme "$zdir" mac; cp LICENSE NOTICE "$zdir"/ 2>/dev/null || true
    fallback_zip "$zdir" "$id"
  fi
}

# ============================ Windows → tek .exe =============================
# NSIS ile, kurulum YAPMAYAN, çalışınca kendini geçici klasöre açıp gömülü
# javaw ile uygulamayı başlatan tek self-extracting .exe üretir.
write_nsi() {  # nsifile payloaddir outexe
  local nsi="$1" payload="$2" outexe="$3"
  cat > "$nsi" <<EOF
Unicode true
Name "${APP_NAME}"
OutFile "${outexe}"
Icon "${ROOT}/${ICON_ICO}"
SilentInstall silent
RequestExecutionLevel user
Section
  InitPluginsDir
  SetOutPath "\$PLUGINSDIR"
  File /r "${payload}/*"
  ExecWait '"\$PLUGINSDIR\\runtime\\bin\\javaw.exe" -jar "\$PLUGINSDIR\\app\\${JAR_IN_BUNDLE}"'
SectionEnd
EOF
}

make_windows() {  # stage jhome arch id
  local stage="$1" jhome="$2" arch="$3" id="$4" payload name nsi
  payload="$stage/payload"; mkdir -p "$payload/app" "$payload/runtime"
  copy_tree "$jhome" "$payload/runtime"
  cp "$JAR" "$payload/app/$JAR_IN_BUNDLE"
  name="mersel-dss-agent-signer-${VERSION}-${id}.exe"
  if command -v makensis >/dev/null 2>&1 && [ -f "$ICON_ICO" ]; then
    nsi="$stage/app.nsi"; write_nsi "$nsi" "$payload" "$OUT/$name"
    rm -f "$OUT/$name"
    makensis -V2 "$nsi" >/dev/null
    ARTIFACT="$OUT/$name"
  else
    echo "    (makensis yok → tek .exe yerine .zip fallback + .cmd başlatıcı)" >&2
    cat > "$payload/${LAUNCHER_BASE}.cmd" <<EOF
@echo off
start "" "%~dp0runtime\\bin\\javaw.exe" -jar "%~dp0app\\${JAR_IN_BUNDLE}" %*
EOF
    write_readme "$payload" windows; cp LICENSE NOTICE "$payload"/ 2>/dev/null || true
    fallback_zip "$payload" "$id"
  fi
}

# ============================ Linux → tek .AppImage ==========================
# AppImage = [type2 runtime ELF] + [AppDir'in squashfs'i]. appimagetool'u
# çalıştırmaya gerek yok; mksquashfs + cat ile birleştirilir (deterministik).
make_linux() {  # stage jhome arch id
  local stage="$1" jhome="$2" arch="$3" id="$4" ad name rt rturl rtsha
  ad="$stage/AppDir"; mkdir -p "$ad/app" "$ad/runtime"
  copy_tree "$jhome" "$ad/runtime"
  cp "$JAR" "$ad/app/$JAR_IN_BUNDLE"
  cp "$ICON_PNG" "$ad/mersel-dss-agent.png"
  cat > "$ad/AppRun" <<EOF
#!/bin/sh
HERE="\$(dirname "\$(readlink -f "\$0")")"
exec "\$HERE/runtime/bin/java" -jar "\$HERE/app/${JAR_IN_BUNDLE}" "\$@"
EOF
  chmod +x "$ad/AppRun"
  cat > "$ad/mersel-dss-agent.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Comment=Yerel PKCS#11 imzalama agent'ı
Exec=AppRun
Icon=mersel-dss-agent
Terminal=false
Categories=Utility;Security;
EOF
  name="mersel-dss-agent-signer-${VERSION}-${id}.AppImage"
  case "$arch" in
    x64)     rturl="$APPIMAGE_RT_X64_URL";   rtsha="$APPIMAGE_RT_X64_SHA";;
    aarch64) rturl="$APPIMAGE_RT_ARM64_URL"; rtsha="$APPIMAGE_RT_ARM64_SHA";;
    *)       rturl="";;
  esac
  if command -v mksquashfs >/dev/null 2>&1 && [ -n "$rturl" ]; then
    rt="$CACHE/appimage-runtime-${arch}"
    [ -f "$rt" ] || dl_verify "$rturl" "$rtsha" "$rt"
    mksquashfs "$ad" "$stage/app.sqfs" -root-owned -noappend -quiet
    cat "$rt" "$stage/app.sqfs" > "$OUT/$name"
    chmod +x "$OUT/$name"
    ARTIFACT="$OUT/$name"
  else
    echo "    (mksquashfs/runtime yok → .AppImage yerine .zip fallback)" >&2
    cp "$ICON_PNG" "$ad/Mersel-DSS-Agent.png"
    write_readme "$ad" linux; cp LICENSE NOTICE "$ad"/ 2>/dev/null || true
    fallback_zip "$ad" "$id"
  fi
}

build_one() {  # id kind os arch
  local id="$1" kind="$2" os="$3" arch="$4" jhome stage
  echo ">>> $id  ($kind  $os/$arch)"
  jhome="$(fetch_jre "$id")"
  stage="$OUT/.stage/$id"; rm -rf "$stage"; mkdir -p "$stage"
  ARTIFACT=""
  case "$os" in
    mac)     make_macos   "$stage" "$jhome" "$id" ;;
    windows) make_windows "$stage" "$jhome" "$arch" "$id" ;;
    linux)   make_linux   "$stage" "$jhome" "$arch" "$id" ;;
  esac
  [ -n "$ARTIFACT" ] && [ -f "$ARTIFACT" ] || { echo "HATA: artifact üretilemedi ($id)" >&2; return 1; }
  printf '%s  %s\n' "$(sha256_of "$ARTIFACT")" "$(basename "$ARTIFACT")" >> "$OUT/SHA256SUMS.txt"
  echo "    -> $ARTIFACT  ($(du -h "$ARTIFACT" | cut -f1))"
}

# ---- ana akış ----
echo "Sürüm   : $VERSION"
echo "Jar     : $JAR"
echo "Hedefler: ${SELECTED[*]}"
echo
: > "$OUT/SHA256SUMS.txt"
for id in "${SELECTED[@]}"; do
  match=""
  for t in "${TARGETS_ALL[@]}"; do
    if [ "${t%%|*}" = "$id" ]; then
      IFS='|' read -r tid kind os arch <<< "$t"
      build_one "$tid" "$kind" "$os" "$arch"; match=1; break
    fi
  done
  [ -n "$match" ] || echo "UYARI: bilinmeyen hedef atlandı: $id"
done
rm -rf "$OUT/.stage"
echo
echo "=== Tamamlandı ==="
ls -lh "$OUT"/*.exe "$OUT"/*.dmg "$OUT"/*.AppImage "$OUT"/*.zip 2>/dev/null || true
echo "--- SHA256SUMS.txt ---"; cat "$OUT/SHA256SUMS.txt"
