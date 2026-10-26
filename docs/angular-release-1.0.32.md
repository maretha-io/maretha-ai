# Maretha AI Angular extension release 1.0.32

Coordinated patch: Angular host `3.21.6`, SDK `0.1.2`, add-on `1.0.32`.
This patch marks PDF preview ready when its first page renders, so slow full-file downloads do not replace a visible preview with the readiness timeout.

Build the current coordinated source checkouts. The assembly manifest records
both source revisions and the staged core source hash, including uncommitted edits.
The existing independent `demo` checkout must be preserved.

## Build

Use Node 22/24, Java 21 and Maven with the existing Nuxeo repository
authentication. Install the core checkout dependencies first. Do not copy
credentials into either checkout. From the add-on checkout, assemble both
releases with:

```sh
node maretha-ai-web/angular/scripts/assemble-host.mjs \
  /path/to/maretha-angular-webui /tmp/maretha-cp-1.0.32-assembly
```

The source host must be version 3.21.6 with SDK 0.1.2. The CP library requires
`@maretha/webui-sdk` ^0.1.2 for its component-owned administration heading contract. The script builds its SDK, creates an
isolated library build and host source copy, links shared dependencies without
altering their source directory, and adds the CP provider only in the staged
`custom/index.ts`. The stage allowlists `@maretha/cp-webui`. Its production build
and Maven package produce
`host/nuxeo/package/target/maretha-angular-webui-package-3.21.6.zip` under the
assembly directory. The original core customization and package files stay
unchanged. Current uncommitted core implementation changes are included in the
staged source and covered by `coreSourceSha256`.

The script tests and builds the CP library, embeds its small distribution in the
add-on web JAR under `META-INF/maretha/angular`, and verifies the add-on Maven
reactor. `release-manifest.json` records source revisions, versions and both
package SHA-256 hashes. Build output remains in the chosen assembly directory
for inspection; it never deploys anything.

Inspect the resulting artifact before promoting it through the pipeline below:

```sh
shasum -a 256 maretha-ai-package/target/maretha-ai-package-1.0.32.zip
unzip -p maretha-ai-package/target/maretha-ai-package-1.0.32.zip package.xml
```

The add-on ZIP supplies the server bundles and its packaged Angular extension.
The composed Angular host build must also be deployed so its registered
contributions are active. Merely installing an extension library does not alter
an already compiled Angular application.

## Deploy through the preserved-backend pipeline

The active `maretha-lab` deployment workflow in `maretha-io/hetzner-remote` was
repaired on 2026-10-26. It captures the currently deployed image, pins its immutable
local Docker ID, preserves Helm values and verifies protected package payloads.
Do not use an older workflow or rebuild from the historical `2025-lab-full2` base.

Promote the three verified marketplace ZIPs together in `deploy/releases/`:
Web UI `2025.21.3`, Angular `3.21.6` and CP `1.0.32`. Update every matching
`packages.lock.json` identity/version/path/SHA-256 entry, run
`python3 -m unittest discover -s deploy/scripts/tests -v`, and push the release
commit to `maretha-lab`. The path-filtered workflow builds and rolls out the new
image only after package inventory and payload checks pass.

Routine deployment changes only the container image. The workflow reuses existing
Helm values unless `apply_helm_values` is explicitly requested. It refuses stale
source packages, downgrades, conflicting protected bundles, and a rollout whose
live base changed while the build was running.

## Acceptance and rollback

Record the successful workflow run, resulting image ID, package versions and
runtime bundle hashes. Verify `/ui` and `/ng` after a fresh reload and recheck
account navigation, search/PDF asset loading, stock document actions and CP
settings/results/search. The screenshot audit records which actions actually ran
and which were unavailable; it does not imply coverage of other users or roles.

Use the pipeline rollback workflow with the exact pre-deployment image or Helm
revision captured by this run. Never choose a historical revision solely from
an older release document. The deployment and rollback workflows share their
concurrency lock and retain containerd images for recovery.

This release does not migrate repository schemas. Saved AI output and metadata
changes created during acceptance remain repository content after image rollback.

The CP-composed host has an initial-bundle ceiling of 6.5 MB (the final assembly log records its measured size); this setting is applied only in the isolated assembly. The plain core
keeps its existing 6 MB ceiling. The authenticated content renderer stays lazy,
so its PDF/media implementation does not enter the initial bundle solely because
an add-on can request it.
