## Maretha + Composable Prompts Add-on

- Requires a private key with read access to GET the interactions
 https://docs.composableprompts.com/api/interactions#list-interactions

```
cp.auth.token=sk-
cp.api.url=https://api.becomposable.com
cp.upload.url=https://zeno-server-production.api.becomposable.com
# dev -> cp.api.url=https://studio-server-preview.api.becomposable.com
cp.supported.mime-types=text/plain,application/pdf,image/jpeg,image/png,image/webp
```

#### DEV macOS fix - Ignoring file '.DS_Store' in package store
```
find . -name '.DS_Store' -type f -delete
```

### Settings Document 
http://localhost:8080/nuxeo/ui/#!/browse/CPSettings