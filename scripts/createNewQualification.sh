#!/bin/sh
# Create package/<issuer>/<code>/ from templates/.
#
#   sh scripts/createNewQualification.sh <issuer> <code> <qualificationCode> "<name>"
#   sh scripts/createNewQualification.sh isc2 cissp ISC2.CISSP "Certified Information Systems Security Professional"
#
# <issuer> is the issuer's vendor code. <code> is lowercase [a-z0-9_].
# The id is UUIDv5(nil namespace, <qualificationCode>).

set -eu

if [ $# -ne 4 ]; then
  echo "Usage: $0 <issuer> <code> <qualificationCode> \"<name>\"" >&2
  exit 1
fi

ISSUER=$1
CODE=$2
QCODE=$3
NAME=$4
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIR="$ROOT/package/$ISSUER/$CODE"

echo "$ISSUER" | grep -Eq '^[a-z0-9_]+$' || { echo "issuer must match ^[a-z0-9_]+$" >&2; exit 1; }
echo "$CODE" | grep -Eq '^[a-z0-9_]+$' || { echo "code must match ^[a-z0-9_]+$" >&2; exit 1; }
[ -e "$DIR" ] && { echo "$DIR already exists" >&2; exit 1; }

ID=$(python3 -I -c 'import sys, uuid; print(uuid.uuid5(uuid.UUID(int=0), sys.argv[1]))' "$QCODE")

mkdir -p "$DIR"
cp "$ROOT/.npmrc" "$DIR/.npmrc"
echo 'plugins { id("zb.content") }' > "$DIR/build.gradle.kts"

python3 -I - "$ROOT/templates" "$DIR" "$ISSUER" "$CODE" "$QCODE" "$NAME" "$ID" <<'PY'
import os, sys
src, dst, issuer, code, qcode, name, uid = sys.argv[1:]
values = {"{issuer}": issuer, "{code}": code, "{qualificationCode}": qcode,
          "{name}": name, "{id}": uid, "{description}": name}
for fn in ("index.yml", "package.json"):
    text = open(os.path.join(src, fn)).read()
    for k, v in values.items():
        text = text.replace(k, v)
    open(os.path.join(dst, fn), "w").write(text)
PY

echo "Created $DIR (id $ID)"
echo "Next: fill index.yml, add dependencies, then: unset ZB_SLOT; zbb :$ISSUER:$CODE:gate"
