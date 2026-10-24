# Maretha AI Web UI package

This Maven module owns both Web UI implementations:

- `src/main/resources/web/nuxeo.war/ui/`: the Polymer layouts, custom elements, icons and contributions.
- `angular/`: the `@maretha/cp-webui` Angular SDK library, its tests and assembly tools.

The Angular source follows the feature/layout/shared organization used by the Nuxeo admin console. It remains a library consumed through the public Web UI SDK; it does not introduce a separate application or runtime.

Build and test from the repository root:

```sh
npm --prefix maretha-ai-web/angular test
```

The Maven resource configuration embeds `angular/dist/` in this web JAR at `META-INF/maretha/angular`. The Polymer contribution paths remain unchanged. A composed Angular host build is still required to activate the Angular library.

See [the Angular library guide](angular/README.md) and [the release procedure](../docs/angular-release-1.0.29.md).
