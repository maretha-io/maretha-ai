# Maretha AI Angular extension release 1.0.29

Source baseline: `maretha-io/maretha-ai`, `nuxeo-2025-lab`,
`c8af220840ef56ebbaf71a3fd3266dd1ed487894`. The existing dirty `demo`
checkout is independent and must be preserved.

## Build

Use Node 22/24, Java 21 and Maven with the existing Nuxeo repository
authentication. Install the core checkout dependencies first. Do not copy
credentials into either checkout. From the add-on checkout, assemble both
releases with:

```sh
node maretha-ai-web/angular/scripts/assemble-host.mjs \
  /path/to/maretha-angular-webui /tmp/maretha-cp-1.0.29-assembly
```

The source host must be version 3.21.3. The script builds its SDK, creates an
isolated library build and host source copy, links shared dependencies without
altering their source directory, and adds the CP provider only in the staged
`custom/index.ts`. The stage allowlists `@maretha/cp-webui`. Its production build
and Maven package produce
`host/nuxeo/package/target/maretha-angular-webui-package-3.21.3.zip` under the
assembly directory. The original core customization and package files stay
unchanged. Current uncommitted core implementation changes are included in the
staged source and covered by `coreSourceSha256`.

The script tests and builds the CP library, embeds its small distribution in the
add-on web JAR under `META-INF/maretha/angular`, and verifies the add-on Maven
reactor. `release-manifest.json` records source revisions, versions and both
package SHA-256 hashes. Build output remains in the chosen assembly directory
for inspection; it never deploys anything.

To inspect and prepare the image context afterward:

```sh
mvn -B verify
shasum -a 256 maretha-ai-package/target/maretha-ai-package-1.0.29.zip
unzip -p maretha-ai-package/target/maretha-ai-package-1.0.29.zip package.xml
bash maretha-ai-web/angular/scripts/prepare-demo-context.sh \
  maretha-ai-package/target/maretha-ai-package-1.0.29.zip \
  /tmp/maretha-cp-1.0.29-assembly/host/nuxeo/package/target/maretha-angular-webui-package-3.21.3.zip \
  /tmp/maretha-ai-1.0.29-context
```

The add-on ZIP supplies the server bundles and its packaged Angular extension.
The composed Angular host build must also be deployed so its registered
contributions are active. Merely installing an extension library does not alter
an already compiled Angular application.

## Preserve the current demo image

The shared `hetzner-remote` workflow rebuilds from `2025-lab-full2`, while the
running demo has received subsequent Web UI and authoring package updates.
Build this patch from the actual running image rather than dispatching that
workflow against its older base.

Read the live image immediately before preparing the image and retain this exact
value as the rollback target:

```sh
kubectl -n nuxeo get deployment nuxeo \
  -o jsonpath='{.spec.template.spec.containers[?(@.name=="nuxeo")].image}'
```

At the 2026-10-24 preflight, this returned
`nuxeo-maretha:webui-2025.21.0-3ad7fe2-20261024`. Its Docker image ID was
`sha256:e771a7d682a059fb0fc1c37e0ca7cb69fd558fd9991a93c819b7ac1310ca7c5c`.
These are historical observations; re-read before rollout.

On the demo build host, after transferring the prepared context and verifying its
checksum, build a new, unique tag with the captured image as `BASE_IMAGE`:

```sh
docker build --build-arg BASE_IMAGE=CAPTURED_LIVE_IMAGE \
  -t nuxeo-maretha:ai-1.0.29-UNIQUE_BUILD_ID \
  /tmp/maretha-ai-1.0.29-context
docker save nuxeo-maretha:ai-1.0.29-UNIQUE_BUILD_ID \
  -o /tmp/maretha-ai-1.0.29-image.tar
sudo k3s ctr -n k8s.io images import /tmp/maretha-ai-1.0.29-image.tar
```

The Dockerfile uninstalls exactly `maretha-ai` and `maretha-angular-webui`, then
installs the coordinated 1.0.29 and 3.21.3 packages offline. These names were
verified in the live package manifests. Other packages remain inherited from
the captured live image. It restores the inherited `nuxeo.conf`; it does not
consume the legacy repository Dockerfile. Both packages must be in the prepared
context, so the rolled out image always includes the composed host.

## Rollout and acceptance

The release operator updates only the Nuxeo container image after the combined
package checks pass:

```sh
kubectl -n nuxeo set image deployment/nuxeo \
  nuxeo=FINAL_COMBINED_IMAGE
kubectl -n nuxeo rollout status deployment/nuxeo --timeout=10m
curl --fail --silent --output /dev/null \
  http://localhost:32326/nuxeo/runningstatus
```

Check installed `maretha-ai` version 1.0.29, retained Angular/Web UI and Studio
package versions, and the Angular AI tab, metadata menu, saved responses, search,
document layouts and settings. Test with permitted and unpermitted users.

## Rollback

Restore the image captured immediately before this rollout:

```sh
kubectl -n nuxeo set image deployment/nuxeo nuxeo=CAPTURED_LIVE_IMAGE
kubectl -n nuxeo rollout status deployment/nuxeo --timeout=10m
curl --fail --silent --output /dev/null \
  http://localhost:32326/nuxeo/runningstatus
```

At preflight, Helm revision 76 recorded image `lab-71970a9-73` while the running
Deployment used the newer Web UI image above. Consequently, rolling back to that
Helm revision would not restore the pre-patch runtime. Reconcile the eventual
Helm source of truth separately before any future shared workflow rollout.

This release does not migrate repository schemas. Saved AI output and metadata
changes created during acceptance remain repository content after image rollback.

The CP-composed host has an initial-bundle ceiling of 6.5 MB (measured candidate
6.17 MB); this setting is applied only in the isolated assembly. The plain core
keeps its existing 6 MB ceiling. The authenticated content renderer stays lazy,
so its PDF/media implementation does not enter the initial bundle solely because
an add-on can request it.
