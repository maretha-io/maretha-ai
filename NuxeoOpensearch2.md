# Nuxeo 2025 + OpenSearch 2 Migration

## Overview

Migration from Nuxeo LTS 2023 → Nuxeo 2025 (version `2025.15.9`) with OpenSearch 2.x native kNN vector search integration.

---

## New Features

### Native kNN Vector Search (OpenSearch 2.x)

Uses OpenSearch's native `knn` query via a custom NXQL ES hint system, fully integrated with Nuxeo's `SearchService` abstraction.

#### Architecture

```
┌──────────────┐     NXQL + hint      ┌──────────────────────────┐
│CustomKnnSearch├─────────────────────►│    SearchService.search() │
│(PageProvider) │                      └────────┬─────────────────┘
└──────────────┘                                │
                                                ▼
                                   ┌────────────────────────┐
                                   │ OpenSearchQueryTransformer│
                                   │  (parses NXQL + hints)   │
                                   └────────┬───────────────┘
                                            │ OPERATOR(knn)
                                            ▼
                                ┌───────────────────────────────┐
                                │ KnnOpenSearchHintQueryBuilder    │
                                │  → builds native KnnQuery       │
                                │  → field, vector, k, minScore   │
                                └────────┬──────────────────────┘
                                         │
                                         ▼
                                ┌──────────────────┐
                                │  OpenSearch 2.x   │
                                │  (kNN plugin)     │
                                └──────────────────┘
```

#### Search Modes

| Mode | `vectorField` param | Fields searched | Behavior |
|------|-------------------|-----------------|----------|
| **Text** | `text` | `embeddingsText` only | Generates embedding from query text via `EmbeddingsService.textToEmbedding()` |
| **Image** | `image` | `embeddingsImage` + `embeddingsText` | Expects pre-computed vector as JSON array. Queries both fields with OR |
| **Similarity** | `similarity` | `embeddingsImage` + `embeddingsText` | Same as image mode — queries both fields with OR |

**Multi-field query example (image/similarity mode):**
```sql
SELECT * FROM Document WHERE
  (/*+ES: INDEX(interactionresults:embeddingsImage) OPERATOR(knn) */
      interactionresults:embeddingsImage = '10|0.4|0.1,0.2,...'
   OR
   /*+ES: INDEX(interactionresults:embeddingsText) OPERATOR(knn) */
      interactionresults:embeddingsText = '10|0.4|0.1,0.2,...')
  AND ecm:primaryType = 'InteractionResult'
```

**Single-field query example (text mode):**
```sql
SELECT * FROM Document WHERE
  /*+ES: INDEX(interactionresults:embeddingsText) OPERATOR(knn) */
  interactionresults:embeddingsText = '10|0.4|0.1,0.2,...'
  AND ecm:primaryType = 'InteractionResult'
```

#### Value Packing Format

The vector data and search parameters are packed into a single pipe-separated string:

```
k|minScore|v1,v2,v3,...,vN
```

| Part | Type | Description |
|------|------|-------------|
| `k` | int | Number of nearest neighbors to return |
| `minScore` | float | Minimum similarity score threshold (0.0–1.0) |
| `v1,v2,...` | float[] | Comma-separated vector components (1024 dimensions) |

Example: `10|0.4|0.023,-0.118,0.045,...` → k=10, minScore=0.4, 1024-dim vector

#### Files

| File | Role |
|------|------|
| `KnnOpenSearchHintQueryBuilder.java` | Implements `OpenSearchHintQueryBuilder` — parses value string, builds `KnnQuery` with field/vector/k/minScore |
| `opensearch-knn-hint-contrib.xml` | OSGI contribution: registers `knn` hint name with opensearch2 extension point `hint` |
| `CustomKnnSearch.java` | Page provider: gets vector from `EmbeddingsService` or input, packs value, emits NXQL with `OPERATOR(knn)` hint(s) |
| `opensearch2-doc-mapping.json` | Index mapping: defines `knn_vector` fields (1024 dims, cosine similarity, HNSW algorithm, nmslib engine) |
| `opensearch2-doc-settings.json` | Index settings: enables `index.knn: true`, sets `knn.algo_param.ef_search: 100` |

### Audit API (Nuxeo 2025)

Migrated from old `org.nuxeo.ecm.platform.audit.api.LogEntry` to new `org.nuxeo.audit.api.LogEntry` builder pattern:

```java
// Before (Nuxeo 2023)
LogEntry entry = new LogEntryImpl();
entry.setEventId("AI");
entry.setCategory("document");

// After (Nuxeo 2025)
LogEntry entry = LogEntry.builder("AI", new Date())
        .category("document")
        .docUUID(doc.getId())
        .build();
```

### SearchService API (Nuxeo 2025)

Migrated from deprecated `ElasticSearchService` to the new `SearchService`:

```java
// Before
SearchQuery.builder(session, nxql)  // session required
// After
SearchQuery.builder(nxql)           // session-free builder
```

### PDFBox 3.x (Nuxeo 2025)

Nuxeo 2025 ships PDFBox 3.x which removed `PDDocument.load()`. Replaced langchain4j's `ApachePdfBoxDocumentParser` with native PDFBox 3.x API:

```java
// Before (PDFBox 2.x via langchain4j)
ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
var document = parser.parse(inputStream);
String text = document.text();

// After (PDFBox 3.x native)
PDDocument pdfDoc = Loader.loadPDF(inputStream.readAllBytes());
PDFTextStripper stripper = new PDFTextStripper();
String text = stripper.getText(pdfDoc);
```

---

## Issues Encountered

### 1. OpenSearch 2 Artifact Not Found

**Problem:** `org.nuxeo.ecm.core:nuxeo-search-client-opensearch2:2025.15.9` not found in any Maven repo.

**Root Cause:** OpenSearch 2 is a separate Nuxeo project with its own groupId and versioning:
- **Wrong:** `org.nuxeo.ecm.core` / version `2025.15.9`
- **Correct:** `org.nuxeo.opensearch2` / version `2025.1.8`
- The artifact is also available as a Nuxeo **Marketplace package** (version `2025.1.31`) for runtime installation

**Resolution:** Use `org.nuxeo.opensearch2:nuxeo-search-client-opensearch2:2025.1.8` for compile-time.

### 2. Nuxeo Maven Repository 401 Unauthorized

**Problem:** `nuxeo-search-client-opensearch2` returned 401 from `maven-internal` repository.

**Root Cause:** Missing/invalid credentials for `maven-internal` in `~/.m2/settings.xml`.

**Resolution:** Fix repository authentication credentials. Use `-U` flag after fixing to clear cached failures.

### 3. Audit LogEntry ClassCastException

**Problem:** `ClassCastException: LogEntryImpl cannot be cast to org.nuxeo.audit.api.LogEntry` in all `RunInteractionServiceImplTest` tests.

**Root Cause:** `AuditUtils.java` used the old `org.nuxeo.ecm.platform.audit.impl.LogEntryImpl` which implements the old `org.nuxeo.ecm.platform.audit.api.LogEntry` interface. Nuxeo 2025 runtime expects the new `org.nuxeo.audit.api.LogEntry` interface.

**Resolution:** Migrated to `LogEntry.builder("AI", new Date()).category(...).build()` using the new API.

### 4. PDFBox 3.x NoSuchMethodError

**Problem:** `NoSuchMethod 'PDDocument.load(InputStream)'` in `InteractionServiceImplTest`.

**Root Cause:** `langchain4j-document-parser-apache-pdfbox:1.4.0-beta10` calls `PDDocument.load()` which was removed in PDFBox 3.x (shipped by Nuxeo 2025).

**Resolution:** Replaced with native PDFBox 3.x `Loader.loadPDF()` + `PDFTextStripper`. Removed the `langchain4j-document-parser-apache-pdfbox` dependency entirely.

### 5. AuditFeature Ordering

**Problem:** `RunInteractionServiceImplTest` failed with deployment errors.

**Root Cause:** `AuditFeature` must be deployed before `AutomationFeature` in `@Features`. Also had a redundant `@Deploy("org.nuxeo.ecm.platform.audit")` causing class loading conflicts.

**Resolution:** Moved `AuditFeature.class` first in `@Features`, removed redundant `@Deploy`.

### 6. Document Type Count Changed

**Problem:** `GetDocumentTypesOperationTest` expected 39 document types.

**Root Cause:** Nuxeo 2025 added 4 new built-in document types.

**Resolution:** Updated expected count from 39 → 43.

### 7. Query.Builder Type Mismatch

**Problem:** `Query.Builder.knn(KnnQuery)` returns `ObjectBuilder<Query>`, not `Query.Builder`.

**Root Cause:** OpenSearch Java client uses typed builder pattern where setter methods return `ObjectBuilder<T>` (supertype), not the concrete builder.

**Resolution:** Store builder reference separately, call `.knn()` as void-like, return the stored builder reference.

---

## Dependencies

```xml
<!-- OpenSearch 2.x client (for compilation) -->
<dependency>
  <groupId>org.nuxeo.opensearch2</groupId>
  <artifactId>nuxeo-search-client-opensearch2</artifactId>
  <version>2025.1.8</version>
  <scope>provided</scope>
</dependency>
```

**Runtime:** Install `nuxeo-search-client-opensearch2` Marketplace package (`2025.1.31`) on Nuxeo server.

---

## Deployment Checklist

1. ☐ Install Nuxeo 2025 (`2025.15.9`)
2. ☐ Install `nuxeo-search-client-opensearch2` Marketplace package
3. ☐ Ensure OpenSearch cluster has **k-NN plugin** enabled
4. ☐ Deploy this bundle (contains custom mapping + kNN hint builder)
5. ☐ Full repository re-index
