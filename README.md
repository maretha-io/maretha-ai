## Maretha.io + Becomposable.com Integration Add-on

- Requires a private key with read access to GET the interactions
 https://docs.becomposable.com/api/interactions#list-interactions

```
cp.auth.token=sk-
cp.api.url=https://api.becomposable.com
cp.upload.url=https://zeno-server-production.api.becomposable.com
# dev -> cp.api.url=https://studio-server-preview.api.becomposable.com
cp.supported.mime-types=text/plain,application/pdf,image/jpeg,image/png,image/webp
```

### Settings Document 
- Admin Settings Page: http://localhost:8080/nuxeo/ui/#!/maretha-cp-interactions
- Settings Document: http://localhost:8080/nuxeo/ui/#!/browse/InteractionSettings

### Schemas and Facets
```
<schema name="interactionSettings" prefix="interactionsettings" src="data/schemas/interaction-settings.xsd"/>
<schema name="interactionResults" prefix="interactionresults" src="data/schemas/interaction-results.xsd"/>

<facet name="InteractionResultsFacet">
    <schema name="interactionResults" />
</facet>
```

#### DEV macOS fix - Ignoring file '.DS_Store' in package store
```
find . -name '.DS_Store' -type f -delete
```