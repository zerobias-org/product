#! /bin/sh
# Scaffold a new product package from templates/.
# Supports both parent types:
#   package/<vendor>/<code>            vendor-parented (depth 2)
#   package/<vendor>/<suite>/<code>    suite-parented  (depth 3)
# Portable: sed -i differs between GNU and BSD/macOS, so we use -i.bak + rm.

if [ $# -ne 1 ]; then
    echo "Usage: $0 <folder_path>"
    echo "  vendor-parented: $0 package/acme/widget"
    echo "  suite-parented:  $0 package/acme/platform/widget"
    exit 1
fi

FOLDER_PATH=${1%/}
BASE_DIR=$(dirname "$0")

REL=${FOLDER_PATH#package/}
if [ "$REL" = "$FOLDER_PATH" ]; then
    echo "Error: path must start with package/"
    exit 1
fi

CODE=$(basename "$REL")
PARENT=$(dirname "$REL")
case "$PARENT" in
    */*)
        VENDOR=${PARENT%%/*}
        SUITE=${PARENT#*/}
        case "$SUITE" in */*)
            echo "Error: too deep — use package/<vendor>/<code> or package/<vendor>/<suite>/<code>"
            exit 1
        esac
        ;;
    .|"")
        echo "Error: path must be package/<vendor>/<code> or package/<vendor>/<suite>/<code>"
        exit 1
        ;;
    *)
        VENDOR=$PARENT
        SUITE=""
        ;;
esac

mkdir -p "$FOLDER_PATH"

# templates/. (not templates/*) so dotfiles like the REQUIRED .npmrc copy too
cp -r "$BASE_DIR"/../templates/. "$BASE_DIR/../$FOLDER_PATH"

PKG="$BASE_DIR/../$FOLDER_PATH/package.json"
IDX="$BASE_DIR/../$FOLDER_PATH/index.yml"
CAT="$BASE_DIR/../$FOLDER_PATH/catalog.yml"

if [ -n "$SUITE" ]; then
    # Rewrite the vendor-parented template into its suite-parented shape
    # BEFORE substituting values (tokens are easier to match than values).
    sed -i.bak \
        -e 's|{vendor}/{code}|{vendor}/{suite}/{code}|g' \
        -e 's|{vendor}-{code}|{vendor}-{suite}-{code}|g' \
        -e 's|{vendor}\.{code}|{vendor}.{suite}.{code}|g' \
        -e 's|vendor-{vendor}|suite-{vendor}-{suite}|g' \
        -e 's|\.\./\.\./\.\./scripts|../../../../scripts|g' \
        "$PKG"
    sed -i.bak \
        -e 's|{vendor}-{code}|{vendor}-{suite}-{code}|g' \
        -e 's|^parentType: vendor|parentType: suite|' \
        -e '/^vendorCode: /a\
suiteId: {suiteId}\
suiteCode: {suite}' \
        "$IDX"
    sed -i.bak -e 's|{vendor}\.{code}|{vendor}.{suite}.{code}|g' "$CAT"
fi

UUID=$(uuidgen | tr '[:upper:]' '[:lower:]')
for f in "$PKG" "$IDX" "$CAT"; do
    sed -i.bak \
        -e "s/{id}/$UUID/g" \
        -e "s/{code}/$CODE/g" \
        -e "s/{vendor}/$VENDOR/g" \
        -e "s/{suite}/$SUITE/g" \
        "$f"
    rm -f "$f.bak"
done

IDS="{vendorId}"
[ -n "$SUITE" ] && IDS="{vendorId} and {suiteId}"
echo "Scaffolded $FOLDER_PATH (vendor: $VENDOR${SUITE:+, suite: $SUITE}, code: $CODE, id: $UUID)"
echo "Next: fill in the {name}/{description}/{url} placeholders in index.yml and"
echo "      catalog.yml, set $IDS from the parent package(s),"
echo "      add the official product logo, and create build.gradle.kts:"
echo "      echo 'plugins { id(\"zb.content\") }' > $FOLDER_PATH/build.gradle.kts"
