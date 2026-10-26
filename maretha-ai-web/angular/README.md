# Maretha CP Angular layouts

`@maretha/cp-webui@1.0.31` owns the Angular implementation of the
`io.maretha.cp` add-on. It targets Angular 21.2 and `@maretha/webui-sdk` 0.1.2
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

## Source organization

Both UI implementations are owned by `maretha-ai-web`: the Polymer source remains under `src/main/resources/web/nuxeo.war/ui/`, alongside this `angular/` library.

```text
src/lib/
  features/
    interactions/  # AI tab, chat, execution and saved responses
    search/        # Embedding search, similar documents and saved searches
    settings/      # Administration editors and settings drafts
  layouts/         # Document layouts, fields and layout widgets
  shared/
    services/      # Host-authenticated Nuxeo API service
    types/         # Transport and interaction model interfaces
    utils/         # Pure response, permission and result helpers
  cp-providers.ts  # Public SDK contribution assembly
src/public-api.ts  # Consumer entry point
scripts/           # Isolated host assembly, sanitizer verification and demo context
```

This follows the admin console's feature/layout/shared organization while retaining SDK library packaging and a single host Angular runtime.

Run `npm test` from this directory after installing compatible Angular/SDK peers, or use `npm --prefix maretha-ai-web/angular test` from the repository root. The isolated
`scripts/assemble-host.mjs` assembly script builds and tests the library, composes its provider into a copy
of the host, and builds both Nuxeo marketplace packages. See
[the release procedure](../../docs/angular-release-1.0.31.md). Installing only the
library-bearing add-on JAR cannot change an already compiled Angular host.
