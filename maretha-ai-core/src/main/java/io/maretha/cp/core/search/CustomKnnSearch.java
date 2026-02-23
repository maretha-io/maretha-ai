package io.maretha.cp.core.search;

import io.maretha.cp.core.services.ai.EmbeddingsService;
import io.maretha.cp.core.services.helper.EmbeddingsHelper;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.runtime.api.Framework;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;

/**
 * Custom KNN search page provider for embeddings-based vector similarity
 * search.
 * <p>
 * Uses the Nuxeo 2025 SearchService API with OpenSearch kNN vector fields.
 * The kNN query is built as NXQL with a native OpenSearch hint, leveraging the
 * knn_vector field mapping defined in opensearch2-doc-mapping.json.
 * <p>
 * Requires:
 * - OpenSearch cluster with k-NN plugin enabled
 * - nuxeo-search-client-opensearch1 (or opensearch2) package installed
 * - Custom index mapping with knn_vector fields (see
 * opensearch2-doc-mapping.json)
 * - Full repository re-index after deploying the mapping
 */
public class CustomKnnSearch extends CoreQueryDocumentPageProvider {

    private static final Log log = LogFactory.getLog(CustomKnnSearch.class);

    public static final String RELEVANCE_SCORE = "relevance_score";
    public static final String TEXT_VECTOR_FIELD = "interactionresults:embeddingsText";
    public static final String IMAGE_VECTOR_FIELD = "interactionresults:embeddingsImage";

    private static final int K = 10;
    private static final float DEFAULT_MIN_SCORE = 0.4f;

    private List<DocumentModel> results;

    @Override
    public List<DocumentModel> getCurrentPage() {
        if (results == null) {
            results = executeKnnSearch();
        }
        return results;
    }

    private List<DocumentModel> executeKnnSearch() {
        CoreSession session = getCoreSession();
        if (session == null) {
            throw new IllegalStateException("CoreSession is null in Page Provider");
        }

        DocumentModel searchDoc = getSearchDocumentModel();
        if (searchDoc == null) {
            return getEmptyResult();
        }

        Map<String, String> namedParameters = (Map<String, String>) searchDoc.getContextData(NAMED_PARAMETERS);
        if (MapUtils.isEmpty(namedParameters)) {
            return getEmptyResult();
        }
        if (!namedParameters.containsKey("vectorField") || !namedParameters.containsKey("vectorQuery")) {
            return getEmptyResult();
        }

        float minScore = Float.parseFloat(namedParameters.getOrDefault("min_score", String.valueOf(DEFAULT_MIN_SCORE)));
        String vectorField = namedParameters.get("vectorField");
        String vectorQuery = namedParameters.get("vectorQuery");

        if (StringUtils.isEmpty(vectorQuery)) {
            return getEmptyResult();
        }

        // Build the NXQL base filter query
        if (query == null) {
            buildQuery(session);
        }
        if (query == null) {
            throw new NuxeoException(String.format("Cannot perform null query: check provider '%s'", getName()));
        }

        try {
            // Get the SearchService
            SearchService searchService = Framework.getService(SearchService.class);

            // Build the vector(s) for the query
            double[] queryVector = null;
            String[] targetFields = null;

            if ("text".equalsIgnoreCase(vectorField)) {
                // Text search: generate embedding from the query text
                queryVector = Framework.getService(EmbeddingsService.class)
                        .textToEmbedding(vectorQuery, EmbeddingsHelper.DEFAULT_EMBEDDING_DIMENSIONS);
                targetFields = new String[] { TEXT_VECTOR_FIELD };
            } else if ("image".equalsIgnoreCase(vectorField) || "similarity".equalsIgnoreCase(vectorField)) {
                // Image/similarity search: query both image AND text vector fields
                // (equivalent to the old BoolQuery with should clauses)
                queryVector = parseVectorFromJson(vectorQuery);
                targetFields = new String[] { IMAGE_VECTOR_FIELD, TEXT_VECTOR_FIELD };
            }

            if (queryVector == null || targetFields == null) {
                return getEmptyResult();
            }

            // Build the NXQL query with kNN hints for all target fields
            String nxqlWithFilter = buildNxqlWithKnnHint(targetFields, queryVector, K, minScore);

            SearchQuery searchQuery = SearchQuery.builder(nxqlWithFilter)
                    .limit((int) getPageSize())
                    .offset((int) getCurrentPageOffset())
                    .build();

            SearchResponse searchResponse = searchService.search(searchQuery);
            DocumentModelList docs = searchResponse.loadDocuments(session);

            List<DocumentModel> result = new ArrayList<>(docs);
            setResultsCount(docs.totalSize());
            currentPageDocuments = result;
            return result;

        } catch (Exception e) {
            log.error("Error executing kNN search", e);
            return getEmptyResult();
        }
    }

    /**
     * Build an NXQL query incorporating the base where clause filters.
     * The actual kNN scoring happens at the OpenSearch level via the index mapping.
     * We use the NXQL query as a pre-filter for the kNN search.
     */
    private String buildNxqlWithKnnHint(String[] vectorFields, double[] vector, int k, float minScore) {
        // Pack the kNN parameters into a value string: k|minScore|v1,v2,v3,...
        StringBuilder vectorStr = new StringBuilder();
        vectorStr.append(k).append("|").append(minScore).append("|");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0)
                vectorStr.append(",");
            vectorStr.append((float) vector[i]);
        }
        String packedValue = vectorStr.toString();

        // Build one kNN hint clause per vector field, joined with OR
        // For image/similarity mode this searches both embeddingsImage and
        // embeddingsText
        StringBuilder knnClauses = new StringBuilder();
        for (int i = 0; i < vectorFields.length; i++) {
            if (i > 0)
                knnClauses.append(" OR ");
            knnClauses.append(String.format(
                    "/*+ES: INDEX(%s) OPERATOR(knn) */ %s = '%s'",
                    vectorFields[i], vectorFields[i], packedValue));
        }
        String knnExpression = vectorFields.length > 1
                ? "(" + knnClauses + ")"
                : knnClauses.toString();

        // Combine with base query filters if present
        if (query != null && !query.trim().isEmpty()) {
            String upperQuery = query.toUpperCase();
            int whereIdx = upperQuery.indexOf("WHERE");
            if (whereIdx >= 0) {
                String baseFilter = query.substring(whereIdx + 5).trim();
                String selectFrom = query.substring(0, whereIdx).trim();
                return selectFrom + " WHERE " + knnExpression + " AND " + baseFilter;
            }
        }

        return "SELECT * FROM Document WHERE " + knnExpression;
    }

    /**
     * Parse a vector from a JSON array string like "[0.1, 0.2, 0.3, ...]"
     */
    private double[] parseVectorFromJson(String vectorJson) {
        try {
            String cleaned = vectorJson.trim();
            if (cleaned.startsWith("[")) {
                cleaned = cleaned.substring(1);
            }
            if (cleaned.endsWith("]")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            String[] parts = cleaned.split(",");
            double[] result = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Double.parseDouble(parts[i].trim());
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse vector from JSON: " + vectorJson, e);
            return null;
        }
    }

    public DocumentModelList getEmptyResult() {
        setResultsCount(0);
        return new DocumentModelListImpl();
    }
}
