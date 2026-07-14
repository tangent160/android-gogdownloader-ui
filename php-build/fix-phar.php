<?php

/**
 * Strips the shebang line from a phar's stub and re-signs it (SHA256).
 *
 * php-micro executes the bytes appended directly after micro.sfx, so the phar
 * must not start with a shebang. Removing it invalidates the phar signature,
 * which spans the whole file from the stub to the signature block:
 *   [content][hash (32 bytes for SHA256)][sig type (4 bytes)]["GBMB"]
 */
if ($argc !== 2) {
    fwrite(STDERR, "usage: php fix-phar.php <file.phar>\n");
    exit(1);
}

$file = $argv[1];
$data = file_get_contents($file) ?: exit(1);

if (str_starts_with($data, '#!')) {
    $data = substr($data, strpos($data, "\n") + 1);
    echo ">> stripped shebang\n";
}

if (substr($data, -4) !== 'GBMB') {
    fwrite(STDERR, "not a signed phar (missing GBMB magic)\n");
    exit(1);
}

$sigType = unpack('V', substr($data, -8, 4))[1];
if ($sigType !== 0x0003) { // PHAR_SIG_SHA256
    fwrite(STDERR, sprintf("unsupported phar signature type 0x%04x\n", $sigType));
    exit(1);
}

$content = substr($data, 0, -40); // strip 32-byte hash + 8-byte trailer
$signed = $content . hash('sha256', $content, true) . substr($data, -8);
file_put_contents($file, $signed);
echo ">> re-signed phar (sha256)\n";
