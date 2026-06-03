#!/usr/bin/env bash
###############################################################################
# package-dist.sh — Bundled-JRE dağıtım paketleri üretir.
#
# Her hedef için (Windows x64/x86, macOS arm64/x64, Linux x64) bir ZIP üretir.
# ZIP'in kökünde SADECE çift-tıklanacak başlatıcı bulunur; çalıştırılabilir
# `.jar` `app/` alt klasöründe gizlidir, böylece kullanıcı yanlışlıkla jar'a
# tıklayıp sistemdeki (uyumsuz olabilecek) Java ile açmaz. Gömülü JRE her
# zaman `runtime/` altındadır ve başlatıcı doğrudan onu kullanır.
#
#   runtime kaynağı:
#     - 4 hedef: Eclipse Temurin (Adoptium) JRE 8
#     - macOS arm64: Azul Zulu JRE 8 (Temurin Java 8 arm64 üretmiyor)
#
# Kullanım:
#   scripts/package-dist.sh                 # tüm hedefler
#   scripts/package-dist.sh macos-arm64     # tek/seçili hedef(ler)
#
# Ortam değişkenleri:
#   JAR=...        (vars. target/mersel-dss-agent-signer-api.jar)
#   VERSION=...    (vars. jar manifest Implementation-Version)
#   OUT=dist       çıktı dizini
#   CACHE=...      JRE indirme önbelleği (vars. .tooling/jre-cache)
#   LAUNCH4J=...   launch4j CLI yolu (varsa Windows için gerçek .exe üretir)
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
)

if [ "$#" -gt 0 ]; then SELECTED=("$@"); else
  SELECTED=(); for t in "${TARGETS_ALL[@]}"; do SELECTED+=("${t%%|*}"); done
fi

mkdir -p "$OUT" "$CACHE"

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

stage_macos() {  # stage jhome
  local stage="$1" jhome="$2" app C
  app="$stage/${LAUNCHER_BASE}.app"; C="$app/Contents"
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
  write_readme "$stage" mac
  cp LICENSE NOTICE "$stage"/ 2>/dev/null || true
}

stage_windows() {  # stage jhome arch
  local stage="$1" jhome="$2" arch="$3"
  mkdir -p "$stage/app" "$stage/runtime"
  copy_tree "$jhome" "$stage/runtime"
  cp "$JAR" "$stage/app/$JAR_IN_BUNDLE"
  if [ -n "${LAUNCH4J:-}" ] && [ -f "$ICON_ICO" ] && build_win_exe "$stage" "$arch"; then
    :   # Mersel ikonlu gerçek .exe üretildi (taskbar ikonu ayrıca uygulama-içi setIconImages ile)
  else
    [ -n "${LAUNCH4J:-}" ] || echo "    (launch4j yok → .cmd fallback başlatıcı)" >&2
    cat > "$stage/${LAUNCHER_BASE}.cmd" <<EOF
@echo off
rem ${APP_NAME} başlatıcı — gömülü JRE ile çalıştırır (sistem Java'sını kullanmaz)
start "" "%~dp0runtime\\bin\\javaw.exe" -jar "%~dp0app\\${JAR_IN_BUNDLE}" %*
EOF
  fi
  write_readme "$stage" windows
  cp LICENSE NOTICE "$stage"/ 2>/dev/null || true
}

build_win_exe() {  # stage arch -> 0 başarı / 1 başarısız (LAUNCH4J set + app.ico gerekli)
  local stage="$1" arch="$2" cfg bits b64 stage_abs ico_abs
  stage_abs="$(cd "$stage" && pwd)"          # launch4j relative yolları config'e göre çözer → mutlak ver
  ico_abs="$ROOT/$ICON_ICO"
  if [ "$arch" = x64 ]; then bits=64; b64=true; else bits=32; b64=false; fi
  cfg="$(mktemp).xml"
  # <jar> ve <jre><path> RUNTIME yolları (Windows'ta exe'ye göre) → relative kalır.
  cat > "$cfg" <<EOF
<launch4jConfig>
  <dontWrapJar>true</dontWrapJar>
  <headerType>gui</headerType>
  <jar>app\\${JAR_IN_BUNDLE}</jar>
  <outfile>${stage_abs}/${LAUNCHER_BASE}.exe</outfile>
  <chdir>.</chdir>
  <errTitle>${APP_NAME}</errTitle>
  <icon>${ico_abs}</icon>
  <jre>
    <path>runtime</path>
    <bundledJre64Bit>${b64}</bundledJre64Bit>
    <runtimeBits>${bits}</runtimeBits>
  </jre>
</launch4jConfig>
EOF
  if "$LAUNCH4J" "$cfg" >/dev/null 2>&1; then rm -f "$cfg"; return 0; fi
  rm -f "$cfg"; return 1
}

stage_linux() {  # stage jhome
  local stage="$1" jhome="$2"
  mkdir -p "$stage/app" "$stage/runtime"
  copy_tree "$jhome" "$stage/runtime"
  cp "$JAR" "$stage/app/$JAR_IN_BUNDLE"
  cp "$ICON_PNG" "$stage/Mersel-DSS-Agent.png"   # görünür Mersel ikonu (install.sh kullanır)
  # Doğrudan başlatıcı (çift tık / terminalden çalıştır)
  cat > "$stage/${LAUNCHER_SH}" <<EOF
#!/bin/sh
# ${APP_NAME} başlatıcı — gömülü JRE ile çalıştırır (sistem Java'sını kullanmaz)
DIR="\$(cd "\$(dirname "\$0")" && pwd)"
exec "\$DIR/runtime/bin/java" -jar "\$DIR/app/${JAR_IN_BUNDLE}" "\$@"
EOF
  chmod +x "$stage/${LAUNCHER_SH}"
  # Menüye ekleme: .desktop'ı MUTLAK yollarla kurar → menüde Mersel adı + Mersel ikonu görünür.
  # (Taşınabilir bir klasörde .desktop'ın Icon= alanı mutlak yol gerektirdiğinden, gömülü gevşek
  #  .desktop yerine bu yöntem kullanılır; kurulum yapılmadan da çalışan pencere ikonu zaten
  #  uygulama içi setIconImages ile Mersel'dir.)
  cat > "$stage/install.sh" <<EOF
#!/bin/sh
# ${APP_NAME}'ı uygulama menüsüne ekler (Mersel ikonuyla). Kaldırmak için en alttaki komut.
DIR="\$(cd "\$(dirname "\$0")" && pwd)"
APPS="\$HOME/.local/share/applications"
mkdir -p "\$APPS"
chmod +x "\$DIR/${LAUNCHER_SH}"
cat > "\$APPS/mersel-dss-agent.desktop" <<DESK
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Comment=Yerel PKCS#11 imzalama agent'ı
Exec="\$DIR/${LAUNCHER_SH}"
Icon=\$DIR/Mersel-DSS-Agent.png
Terminal=false
Categories=Utility;Security;
DESK
update-desktop-database "\$APPS" 2>/dev/null || true
echo "Eklendi: menüde 'Mersel DSS Agent Signer' (Mersel ikonuyla)."
echo "Kaldırmak icin: rm \"\$APPS/mersel-dss-agent.desktop\""
EOF
  chmod +x "$stage/install.sh"
  write_readme "$stage" linux
  cat >> "$stage/OKUBENI.txt" <<EOF

UYGULAMA MENÜSÜNE EKLEME (opsiyonel)
  ./install.sh  →  başlat menüsüne "Mersel DSS Agent Signer" girişini
  Mersel ikonuyla ekler. (Çalışan uygulamanın taskbar ikonu zaten Mersel'dir.)
EOF
  cp LICENSE NOTICE "$stage"/ 2>/dev/null || true
}

build_one() {  # id kind os arch
  local id="$1" kind="$2" os="$3" arch="$4" jhome stage zipname
  echo ">>> $id  ($kind  $os/$arch)"
  jhome="$(fetch_jre "$id")"
  stage="$OUT/.stage/$id"; rm -rf "$stage"; mkdir -p "$stage"
  case "$os" in
    mac)     stage_macos   "$stage" "$jhome" ;;
    windows) stage_windows "$stage" "$jhome" "$arch" ;;
    linux)   stage_linux   "$stage" "$jhome" ;;
  esac
  zipname="mersel-dss-agent-signer-${VERSION}-${id}.zip"
  rm -f "$OUT/$zipname"
  ( cd "$stage" && zip -ry -q "$ROOT/$OUT/$zipname" . )
  ( cd "$OUT" && shasum -a 256 "$zipname" >> SHA256SUMS.txt )
  echo "    -> $OUT/$zipname  ($(du -h "$OUT/$zipname" | cut -f1))"
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
ls -lh "$OUT"/*.zip 2>/dev/null || true
echo "--- SHA256SUMS.txt ---"; cat "$OUT/SHA256SUMS.txt"
