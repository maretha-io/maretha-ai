# OpenSearch 3.1 & AI Integration in Maretha AI Core

This document outlines the Native OS 3.1 AI Capabilities introduced to the Nuxeo Search Client backend and how they specifically relate to our Semantic/Similarity use cases.

## Core Usecase: External Provider Vector Search (k-NN)

Because our architecture relies on extracting document embeddings via **External APIs (Google Vertex AI / AWS Bedrock)** rather than hosting ML models locally inside the OpenSearch backend, our primary interaction with the OpenSearch 3.1 AI stack revolves around the **k-NN (k-Nearest Neighbors) Plugin**.

### How we use it
1. **Embedding Pipeline**: We do not send raw text directly to OpenSearch. We generate float-array mathematical representations (e.g., dense 1024-dimension vectors) inside the Nuxeo JVM using `EmbeddingsService.textToEmbedding(...)`.
2. **Execution**: The `CustomKnnSearch` page provider parses these external embeddings into a dense numerical array and injects it into a native `/*+ES: OPERATOR(knn) */` hint.
3. **OpenSearch Matching**: OpenSearch 3.1 parses this NXQL query dynamically into a standard `KnnQuery` using the Nuxeo `KnnOpenSearchHintQueryBuilder` mapping layer, determining embedding space distance/similarity to `interactionresults:embeddingsText` documents!

---

## The New OpenSearch 3 AI Native Operators

We successfully added direct wrapper layers representing pure OpenSearch 3 native operators. While we bypass the `neural` operator for our standard searches due to our Google/Bedrock separation, these features are explicitly available in the framework to mix with future use cases!

### 1. Hybrid Search (`hybrid`)
OpenSearch 3 enables Hybrid Search, fusing semantic similarity searches with traditional lexical/keyword mapping (`BM25`) using normalization techniques (like Reciprocal Rank Fusion) for far better hit relevance.

Since we compute vectors manually, Hybrid Search can still be explicitly triggered in the future if we decide to execute a compound Lexical + Remote Vector algorithm instead of pure kNN!
**Usage Syntax via UI/API:**
```sql
SELECT * FROM Document WHERE /*+ES: OPERATOR(hybrid) */ dummyField = 'dc:title|title_embedding|remote_google_bedrock_model_alias|10|0.7|Full Query String'
```

### 2. Neural Vectorization (`neural`)
Fully bypasses JVM operations and expects OpenSearch to maintain your ML Text embedding models locally on its nodes. We have technically mapped this operator securely if you ever migrate off external ML APIs.
**Usage Syntax via UI/API:**
```sql
SELECT * FROM Document WHERE /*+ES: INDEX(target_embeddings_field) OPERATOR(neural) */ dummyField = 'modelId_in_opensearch|k_limit|minScore|Search Query Text'
```

### Summary
For similarity and semantic search using Google and AWS Bedrock API extracts, you should exclusively target the `CustomKnnSearch` provider. Nuxeo securely wraps and manages your arrays dynamically against OpenSearch 3.1's advanced array serializers.
