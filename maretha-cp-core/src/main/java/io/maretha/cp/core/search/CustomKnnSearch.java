package io.maretha.cp.core.search;

import io.maretha.cp.core.services.APIService;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.api.EsResult;
import org.nuxeo.elasticsearch.provider.ElasticSearchNxqlPageProvider;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.functionscore.ScriptScoreQueryBuilder;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;

public class CustomKnnSearch extends ElasticSearchNxqlPageProvider {
    public static final String TEXT_VECTOR_FIELD = "interactionresults:embeddingsText";
    public static final String IMAGE_VECTOR_FIELD = "interactionresults:embeddingsImage";
    private static final float boost = 1.0F;
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
        if (namedParameters == null || MapUtils.isEmpty(namedParameters)) {
            return getEmptyResult();
        }

        String vectorField = namedParameters.get("vectorField");
        if (StringUtils.isEmpty(vectorField)) {
            return getEmptyResult();
        }

        if (query == null) {
            buildQuery(session);
        }
        if (query == null) {
            throw new NuxeoException(String.format("Cannot perform null query: check provider '%s'", getName()));
        }

        ElasticSearchService esService = Framework.getService(ElasticSearchService.class);
        NxQueryBuilder nxQuery = this.getQueryBuilder(session);

        // Combine NXQL and KNN using "must" (AND logic)
        BoolQueryBuilder combinedQuery = QueryBuilders.boolQuery();

        String vectorQuery = namedParameters.get("vectorQuery");
        if (StringUtils.isNotEmpty(vectorQuery) &&
                StringUtils.isNotEmpty(vectorField) && !"[]".equals(vectorQuery)) {

            float minScore = Float.parseFloat(namedParameters.getOrDefault("min_score", "0.8"));

            if (TEXT_VECTOR_FIELD.equals(vectorField)) {
                APIService apiService = Framework.getService(APIService.class);
                double[] textVectors = apiService.textToEmbeddings(vectorQuery);
                String vectorJson = Arrays.toString(textVectors);

                ScriptScoreQueryBuilder scoreQuery = buildScriptKnnQuery(vectorJson, TEXT_VECTOR_FIELD)
                        .setMinScore(minScore);
                combinedQuery = combinedQuery
                        .must(scoreQuery)
                        .boost(boost);
            }

            if (IMAGE_VECTOR_FIELD.equals(vectorField)) {
                ScriptScoreQueryBuilder scoreQuery = buildScriptKnnQuery(vectorQuery, IMAGE_VECTOR_FIELD)
                        .setMinScore(minScore);
                combinedQuery = combinedQuery
                        .must(scoreQuery)
                        .boost(boost);
            }
        }

        if (searchOnAllRepositories()) {
            nxQuery.searchOnAllRepositories();
        }
        nxQuery.useUnrestrictedSession(useUnrestrictedSession());

        List<String> highlightFields = getHighlights();
        if (highlightFields != null && !highlightFields.isEmpty()) {
            nxQuery.highlight(highlightFields);
        }

        combinedQuery = combinedQuery.filter(getCurrentQueryAsEsBuilder());
        nxQuery = nxQuery.esQuery(combinedQuery)
                .fetchFromElasticsearch() // Force ES query
                .offset((int) this.getCurrentPageOffset())
                .limit(this.getLimit())
                .addAggregates(this.buildAggregates());

        log.debug("ES KNN query: " + nxQuery.makeQuery());

        EsResult ret = esService.queryAndAggregate(nxQuery);
        DocumentModelList dmList = ret.getDocuments();

        currentAggregates = new HashMap<>(ret.getAggregates().size());
        for (Aggregate<Bucket> agg : ret.getAggregates()) {
            currentAggregates.put(agg.getId(), agg);
        }
        setResultsCount(dmList.totalSize());
        currentPageDocuments = dmList;
        return currentPageDocuments;
    }

    public DocumentModelList getEmptyResult() {
        setResultsCount(0);
        return new DocumentModelListImpl();
    }

    private ScriptScoreQueryBuilder buildScriptKnnQuery(String queryVector, String type) {
        Map<String, Object> params = new HashMap<>();
        params.put("query_value", parseVector(queryVector));
        params.put("field", type);
        params.put("space_type", "cosinesimil");

        // Construct the script for cosine similarity
        Script script = new Script(ScriptType.INLINE, "knn", "knn_score", params);

        // Build the script-based query
        return QueryBuilders.scriptScoreQuery(
                QueryBuilders.existsQuery(type).boost(0.1f), // Filter: Only docs with vectors
                script
        );
    }

    private double[] parseVector(String vectorString) {
        JSONArray jsonArray = new JSONArray(vectorString);
        double[] result = new double[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            result[i] = jsonArray.getDouble(i);
        }
        return result;
    }
}