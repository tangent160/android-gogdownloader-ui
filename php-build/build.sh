#!/usr/bin/env bash
#
# Builds the self-executing gog-downloader binaries bundled into the app.
#
# Pipeline:
#   1. Download official static-php-cli prebuilt PHP (MIT-licensed project,
#      https://github.com/crazywhalecc/static-php-cli) for the host (cli) and
#      each Android ABI (micro.sfx).
#   2. composer install + build the gog-downloader phar from vendor/GogDownloader
#      (same as upstream's release workflow, using clue/phar-composer).
#   3. Concatenate micro.sfx + phar into a self-executing binary per ABI and
#      install it as app/src/main/jniLibs/<abi>/libgogdownloader.so.
#
# Everything is cached under php-build/cache; re-runs are fast.

set -euo pipefail

PHP_VERSION="${PHP_VERSION:-8.4.23}"
STATIC_PHP_BASE="https://dl.static-php.dev/static-php-cli/bulk"
COMPOSER_URL="https://getcomposer.org/download/latest-stable/composer.phar"
PHAR_COMPOSER_URL="https://github.com/clue/phar-composer/releases/download/v1.4.0/phar-composer-1.4.0.phar"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
SRC_DIR="$ROOT_DIR/vendor/GogDownloader"
CACHE_DIR="$SCRIPT_DIR/cache"
OUT_DIR="$SCRIPT_DIR/out"
JNILIBS_DIR="$ROOT_DIR/app/src/main/jniLibs"

# ABI -> static-php arch
declare -A ABIS=(
    [arm64-v8a]=aarch64
    [x86_64]=x86_64
)

HOST_ARCH="$(uname -m)"

if [[ ! -f "$SRC_DIR/composer.json" ]]; then
    echo "error: $SRC_DIR is empty — run 'git submodule update --init' first" >&2
    exit 1
fi

mkdir -p "$CACHE_DIR" "$OUT_DIR"

fetch() {
    local url="$1" dest="$2"
    if [[ ! -f "$dest" ]]; then
        echo ">> downloading $url"
        curl -fsSL --retry 3 -o "$dest.tmp" "$url"
        mv "$dest.tmp" "$dest"
    fi
}

# 1. Host PHP CLI (to run composer + phar-composer)
fetch "$STATIC_PHP_BASE/php-$PHP_VERSION-cli-linux-$HOST_ARCH.tar.gz" "$CACHE_DIR/php-cli-host.tar.gz"
tar -xzf "$CACHE_DIR/php-cli-host.tar.gz" -C "$CACHE_DIR"
PHP="$CACHE_DIR/php"
"$PHP" -v | head -1

fetch "$COMPOSER_URL" "$CACHE_DIR/composer.phar"
fetch "$PHAR_COMPOSER_URL" "$CACHE_DIR/phar-composer.phar"

# 2. Build the phar
echo ">> installing composer dependencies"
(cd "$SRC_DIR" && "$PHP" "$CACHE_DIR/composer.phar" install --no-dev --no-interaction --quiet)

VERSION="$(git -C "$SRC_DIR" describe --tags 2>/dev/null | sed 's/^v//' || echo dev)"
echo "$VERSION" > "$SRC_DIR/bin/appversion"

echo ">> building phar"
(cd "$OUT_DIR" && "$PHP" -d phar.readonly=off "$CACHE_DIR/phar-composer.phar" build "$SRC_DIR" gog-downloader.phar)

# micro.sfx executes whatever is appended to it; the phar's shebang must go,
# and the phar has to be re-signed afterwards.
"$PHP" "$SCRIPT_DIR/fix-phar.php" "$OUT_DIR/gog-downloader.phar"

# 3. Combine per ABI
for abi in "${!ABIS[@]}"; do
    arch="${ABIS[$abi]}"
    micro_tar="$CACHE_DIR/php-micro-$arch.tar.gz"
    fetch "$STATIC_PHP_BASE/php-$PHP_VERSION-micro-linux-$arch.tar.gz" "$micro_tar"
    tar -xzf "$micro_tar" -C "$CACHE_DIR" micro.sfx
    out="$OUT_DIR/gog-downloader-$arch"
    cat "$CACHE_DIR/micro.sfx" "$OUT_DIR/gog-downloader.phar" > "$out"
    chmod +x "$out"
    mkdir -p "$JNILIBS_DIR/$abi"
    cp "$out" "$JNILIBS_DIR/$abi/libgogdownloader.so"
    echo ">> built $JNILIBS_DIR/$abi/libgogdownloader.so ($(du -h "$out" | cut -f1))"
done

echo ">> done"
