#!/usr/bin/env bash
# Prepare a package-only Docker context. Does not build, upload or deploy anything.
set -euo pipefail
if [[ $# != 3 ]]; then
  echo 'Usage: bash maretha-ai-web/angular/scripts/prepare-demo-context.sh ADDON_PACKAGE.zip ANGULAR_PACKAGE.zip NEW_CONTEXT_DIRECTORY' >&2
  exit 2
fi
package_path=$1
host_package_path=$2
context_path=$3
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
[[ -f "$package_path" ]] || { echo 'Package does not exist.' >&2; exit 2; }
[[ -f "$host_package_path" ]] || { echo 'Angular package does not exist.' >&2; exit 2; }
[[ ! -e "$context_path" ]] || { echo 'Context directory already exists; choose a new directory.' >&2; exit 2; }
unzip -p "$package_path" package.xml | python3 -c 'import sys,xml.etree.ElementTree as E; p=E.fromstring(sys.stdin.read()); assert p.get("name")=="maretha-ai" and p.get("version")=="1.0.29", "Expected maretha-ai 1.0.29 package"'
unzip -p "$host_package_path" package.xml | python3 -c 'import sys,xml.etree.ElementTree as E; p=E.fromstring(sys.stdin.read()); assert p.get("name")=="maretha-angular-webui" and p.get("version")=="3.21.3", "Expected maretha-angular-webui 3.21.3 package"'
mkdir -p -- "$context_path"
cp -- "$package_path" "$context_path/maretha-ai-package-1.0.29.zip"
cp -- "$host_package_path" "$context_path/maretha-angular-webui-package-3.21.3.zip"
cp -- "$script_dir/Dockerfile.demo-patch" "$context_path/Dockerfile"
echo "Prepared: $context_path"
shasum -a 256 "$context_path/maretha-ai-package-1.0.29.zip"
shasum -a 256 "$context_path/maretha-angular-webui-package-3.21.3.zip"
