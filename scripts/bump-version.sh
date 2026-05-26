#!/usr/bin/env bash
#
# bump-version.sh
# =============================================================================
# pom.xml'in proje <version> satırını günceller (parent <version>'a dokunmaz).
#
# Kullanım:
#   ./scripts/bump-version.sh <NEW_VERSION>
#   ./scripts/bump-version.sh major
#   ./scripts/bump-version.sh minor
#   ./scripts/bump-version.sh patch
#   ./scripts/bump-version.sh rc        # 3.0.0 → 3.0.0-rc.1 | 3.0.0-rc.1 → 3.0.0-rc.2
#
# Doğrudan SemVer string'i (örn. "3.1.0", "3.1.0-rc.2") veya bump tipi
# (major/minor/patch/rc) verebilirsin.
#
# Bu script SADECE pom.xml'i değiştirir; commit / push / tag yapmaz.
# Onları `release.sh` yapar. Tek başına da çağrılabilir.
#
# Bağımlılık: bash, perl. macOS BSD ve GNU sed/awk farklarını perl ile bypass'liyoruz.
#
# Exit kodları:
#   0 → güncellendi (stdout: yeni sürüm; stderr: debug)
#   1 → kullanım hatası
#   2 → pom.xml bulunamadı / parse hatası
#   3 → invalid SemVer
# -----------------------------------------------------------------------------

set -euo pipefail

usage() {
    cat >&2 <<EOF
Kullanım: $0 <NEW_VERSION | major | minor | patch | rc>

Örnekler:
  $0 3.1.0              # explicit sürüm
  $0 3.1.0-rc.1         # explicit pre-release
  $0 patch              # 3.0.0 → 3.0.1
  $0 minor              # 3.0.0 → 3.1.0
  $0 major              # 3.0.0 → 4.0.0
  $0 rc                 # 3.0.0 → 3.0.0-rc.1   |  3.0.0-rc.1 → 3.0.0-rc.2

Sürüm SemVer 2.0.0'a uymalıdır: MAJOR.MINOR.PATCH[-PRERELEASE]
EOF
    exit 1
}

if [[ $# -ne 1 ]]; then
    usage
fi

ARG="$1"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT="$( cd "${SCRIPT_DIR}/.." && pwd )"
POM="${REPO_ROOT}/pom.xml"

if [[ ! -f "$POM" ]]; then
    echo "HATA: pom.xml bulunamadı: $POM" >&2
    exit 2
fi

# Mevcut sürümü oku — yalnız PROJE <version>'ı (parent değil).
# pom.xml'de <parent>...<version>2.7.18</version>...</parent> ardından
# <version>3.0.0</version> geliyor. perl ile parent block'unu maskeleyip
# kalandaki ilk <version>'ı yakalıyoruz.
CURRENT_VERSION=$(perl -0777 -ne '
    s|<parent>.*?</parent>||s;
    if (/<version>([^<]+)<\/version>/) { print $1; exit }
' "$POM")

if [[ -z "$CURRENT_VERSION" ]]; then
    echo "HATA: pom.xml içinden mevcut sürüm okunamadı." >&2
    exit 2
fi

echo "Mevcut sürüm: $CURRENT_VERSION" >&2

# SemVer parse: "1.2.3-rc.4" → MAJOR=1 MINOR=2 PATCH=3 PRE=rc.4
parse_semver() {
    local v="$1"
    if [[ ! "$v" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-([A-Za-z0-9.\-]+))?$ ]]; then
        echo "HATA: '$v' geçerli bir SemVer 2.0.0 sürümü değil." >&2
        echo "      Beklenen format: MAJOR.MINOR.PATCH[-PRERELEASE]" >&2
        exit 3
    fi
    P_MAJOR="${BASH_REMATCH[1]}"
    P_MINOR="${BASH_REMATCH[2]}"
    P_PATCH="${BASH_REMATCH[3]}"
    P_PRE="${BASH_REMATCH[5]:-}"
}

parse_semver "$CURRENT_VERSION"

case "$ARG" in
    major)
        NEW_VERSION="$((P_MAJOR + 1)).0.0"
        ;;
    minor)
        NEW_VERSION="${P_MAJOR}.$((P_MINOR + 1)).0"
        ;;
    patch)
        NEW_VERSION="${P_MAJOR}.${P_MINOR}.$((P_PATCH + 1))"
        ;;
    rc)
        if [[ "$P_PRE" =~ ^rc\.([0-9]+)$ ]]; then
            NEW_RC="$((BASH_REMATCH[1] + 1))"
            NEW_VERSION="${P_MAJOR}.${P_MINOR}.${P_PATCH}-rc.${NEW_RC}"
        else
            NEW_VERSION="${P_MAJOR}.${P_MINOR}.${P_PATCH}-rc.1"
        fi
        ;;
    *)
        parse_semver "$ARG"
        NEW_VERSION="$ARG"
        ;;
esac

echo "Hedef sürüm:  $NEW_VERSION" >&2

if [[ "$NEW_VERSION" == "$CURRENT_VERSION" ]]; then
    echo "Sürüm zaten $NEW_VERSION — değişiklik yapılmadı." >&2
    echo "$NEW_VERSION"
    exit 0
fi

# pom.xml'i güncelle — parent block'unu koruyup proje <version>'ını değiştir.
# perl in-place edit; macOS BSD ↔ GNU sed farkını by-pass eder.
CUR_VER="$CURRENT_VERSION" NEW_VER="$NEW_VERSION" perl -i -0777 -pe '
    my $cur = $ENV{CUR_VER};
    my $new = $ENV{NEW_VER};
    my $saved_parent;
    if (s|(<parent>.*?</parent>)|__PARENT_BLOCK__|s) {
        $saved_parent = $1;
    }
    s|<version>\Q$cur\E</version>|<version>$new</version>|;
    if (defined $saved_parent) {
        s|__PARENT_BLOCK__|$saved_parent|s;
    }
' "$POM"

# Maven Wrapper ile post-edit doğrulama (varsa).
MVNW="${REPO_ROOT}/mvnw"
if [[ -x "$MVNW" ]]; then
    ACTUAL=$(
        "$MVNW" --batch-mode --no-transfer-progress -q \
            -Dexec.executable=echo \
            -Dexec.args='${project.version}' \
            --non-recursive exec:exec 2>/dev/null | tail -n 1 | tr -d '\r' || true
    )
    if [[ -n "$ACTUAL" && "$ACTUAL" != "$NEW_VERSION" ]]; then
        echo "UYARI: Maven 'project.version' ($ACTUAL) güncel pom.xml ile uyuşmuyor." >&2
        echo "       pom.xml manuel kontrol gerekebilir." >&2
    fi
fi

echo "pom.xml güncellendi: $CURRENT_VERSION → $NEW_VERSION" >&2
echo "$NEW_VERSION"
