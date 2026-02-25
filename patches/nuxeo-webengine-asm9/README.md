# Nuxeo WebEngine ASM9 Patch

## Bug

**Error:** `PermittedSubclasses requires ASM9`

When the `nuxeo-search-client-opensearch2` package is installed, the Nuxeo WebEngine servlet
fails to initialize. The WebEngine class scanner (`Scanner.java`) uses `AnnotationReader` — a
`ClassVisitor` subclass — to scan all JARs for REST annotations (`@Path`, `@Provider`).

The `opensearch-java-2.26.0.jar` (installed by the opensearch2 package) contains JDK 17+
**sealed classes** which use the `PermittedSubclasses` class file attribute. When ASM's
`ClassReader` encounters this attribute, it calls `ClassVisitor.visitPermittedSubclass()`, which
checks that the visitor's API version is at least `ASM9`. The original `AnnotationReader` passes
`Opcodes.ASM7` (458752), causing the crash.

### Stack Trace

```
java.lang.UnsupportedOperationException: PermittedSubclasses requires ASM9
  at org.objectweb.asm.ClassVisitor.visitPermittedSubclass(ClassVisitor.java:281)
  at org.objectweb.asm.ClassReader.accept(ClassReader.java:707)
  at org.nuxeo.ecm.webengine.rest.scan.Scanner.scan(Scanner.java:77)
  at org.nuxeo.ecm.webengine.app.WebEngineModule.scan(WebEngineModule.java:115)
```

### Affected Version

- `nuxeo-webengine-rest-2025.0.157.jar` (Nuxeo LTS 2025)
- Triggered by `nuxeo-search-client-opensearch2` (any version shipping `opensearch-java >= 2.x`)

## Fix

Changed `super(Opcodes.ASM7)` → `super(Opcodes.ASM9)` in `AnnotationReader`.

ASM 9.7.1 (already bundled with Nuxeo) fully supports `ASM9`, so this is a safe change.

## Applying

```bash
./apply-patch.sh /path/to/nuxeo-server-tomcat-2025.0
```

Or manually:

```bash
# Compile
javac -cp "nxserver/bundles/nuxeo-webengine-rest-2025.0.157.jar:nxserver/lib/asm-9.7.1.jar" \
      -d . AnnotationReader.java

# Inject into JAR
jar uf nxserver/bundles/nuxeo-webengine-rest-2025.0.157.jar \
      org/nuxeo/ecm/webengine/rest/scan/AnnotationReader.class
```

Restart Nuxeo after applying.

## Files

| File | Description |
|---|---|
| `AnnotationReader.java` | Patched source (ASM7 → ASM9) |
| `apply-patch.sh` | Automated apply script |
