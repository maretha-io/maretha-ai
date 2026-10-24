# Maretha CP Angular layouts

`@maretha/cp-webui@1.0.29` owns the Angular implementation of the
`io.maretha.cp` add-on. It targets Angular 21.2 and `@maretha/webui-sdk` 0.1.1
or a compatible later version. Nuxeo operations and schemas remain in the
same add-on reactor.

The host's custom entry point calls the public provider:

```typescript
import { provideCpWebUi } from '@maretha/cp-webui';
export function provideCustomExtensions() {
  return provideCpWebUi();
}
```

The host must provide its standard layout gateway, content renderer, result
commands and saved-search gateway. The library uses the host HttpClient and
current user; it does not own credentials, a second Angular runtime or private
application service imports.

The package supplies AI settings (interactions, metadata schemas, events and
AI/search configuration), document chat and interactions, saved responses,
metadata update/enhancement, embedding search, similar documents and saved
searches. The original `AI-CHAT`, `AI-INTERACTION`, `AI-METADATA` and `AI-SEARCH`
group conditions are retained, with administrator access.

All 17 source layout resources are represented in `CP_LAYOUT_SOURCES`:

| Family | Modes | Integration |
| --- | --- | --- |
| InteractionResults | edit, metadata, view | CP layouts and related source link |
| ai facet | create, edit, metadata, view | Facet-aware CP layouts |
| File, Picture | create, edit, metadata | Native forms plus attached AI schema fields |
| File, Picture | import, view | Inherit unchanged native content layouts |

AI is a schema/facet layout family, not a fabricated document type. Native
File/Picture forms keep their existing save transaction, permissions, schema
constraints, previews and uploads. Dynamic facet schema definitions come from
Nuxeo introspection before fields can be edited.

Run `npm test` after installing compatible Angular/SDK peers. The isolated
assembly script builds and tests the library, composes its provider into a copy
of the host, and builds both Nuxeo marketplace packages. See
[the release procedure](../docs/angular-release-1.0.29.md). Installing only the
library-bearing add-on JAR cannot change an already compiled Angular host.
