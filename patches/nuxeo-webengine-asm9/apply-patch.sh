#!/bin/bash
# Patch nuxeo-webengine-rest to fix ASM9 PermittedSubclasses bug
#
# Usage: ./apply-patch.sh /path/to/nuxeo-server
#
# This patches the AnnotationReader class in nuxeo-webengine-rest to use
# Opcodes.ASM9 instead of ASM7, fixing the "PermittedSubclasses requires ASM9"
# error that occurs when opensearch2 is installed.

set -euo pipefail

NUXEO_HOME="${1:?Usage: $0 /path/to/nuxeo-server}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET_JAR="$NUXEO_HOME/nxserver/bundles/nuxeo-webengine-rest-2025.0.157.jar"
ASM_JAR="$NUXEO_HOME/nxserver/lib/asm-9.7.1.jar"

if [ ! -f "$TARGET_JAR" ]; then
    echo "ERROR: Target JAR not found: $TARGET_JAR"
    echo "Check your Nuxeo version — this patch targets nuxeo-webengine-rest-2025.0.157"
    exit 1
fi

if [ ! -f "$ASM_JAR" ]; then
    echo "ERROR: ASM JAR not found: $ASM_JAR"
    exit 1
fi

WORK_DIR=$(mktemp -d)
trap "rm -rf $WORK_DIR" EXIT

echo "Compiling patched AnnotationReader..."
javac -cp "$TARGET_JAR:$ASM_JAR" -d "$WORK_DIR" "$SCRIPT_DIR/AnnotationReader.java"

echo "Patching $TARGET_JAR..."
jar uf "$TARGET_JAR" -C "$WORK_DIR" org/nuxeo/ecm/webengine/rest/scan/AnnotationReader.class

echo "Done! AnnotationReader patched to use Opcodes.ASM9."
echo "Restart Nuxeo for changes to take effect."
