package io.maretha.cp.core.search;

import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.services.APIService;
import io.maretha.cp.core.services.RepositoryService;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.nuxeo.ecm.core.api.CoreInstance;
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
import org.opensearch.common.lucene.search.function.CombineFunction;
import org.opensearch.common.lucene.search.function.FunctionScoreQuery;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.index.query.functionscore.ScoreFunctionBuilders;
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
    private static final String BOOST = "1.0";
    private static final String TEXT_WEIGHT = "1.0";
    private static final String IMAGE_WEIGHT = "1.0";
    private static final String SCORE_MODE = "AVG";
    private static final String BOOST_MODE = "AVG";
    private final RepositoryService settingsService = Framework.getService(RepositoryService.class);
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
        float minScore = Float.parseFloat(namedParameters.getOrDefault("min_score", "0.8"));
        String vectorField = namedParameters.get("vectorField");
        String vectorQuery = namedParameters.get("vectorQuery");
        if (StringUtils.isEmpty(vectorQuery) || "[]".equals(vectorQuery)) {
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

        if (StringUtils.isNotEmpty(vectorQuery)) {
            Settings settings = settingsService.getSavedSettings(CoreInstance.getCoreSessionSystem(null));
            Map<String, String> esSettings = settings.getEsSettings();

            if (TEXT_VECTOR_FIELD.equals(vectorField)) {
                APIService apiService = Framework.getService(APIService.class);
                double[] textVectors = apiService.textToEmbeddings(vectorQuery);
                vectorQuery = Arrays.toString(textVectors);
            }

            FunctionScoreQueryBuilder scoreQuery = buildScriptKnnQuery(vectorQuery, esSettings)
                    .setMinScore(minScore);

            combinedQuery = combinedQuery
                    .must(scoreQuery)
                    .boost(Float.parseFloat(BOOST));
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

    private FunctionScoreQueryBuilder buildScriptKnnQuery(String queryVector, Map<String, String> esSettings) {
        Script scriptText = new Script(ScriptType.INLINE, "knn", "knn_score",
                Map.of("query_value", parseVector(queryVector), "field", TEXT_VECTOR_FIELD, "space_type", "cosinesimil"));
        Script scriptImage = new Script(ScriptType.INLINE, "knn", "knn_score",
                Map.of("query_value", parseVector(queryVector), "field", IMAGE_VECTOR_FIELD, "space_type", "cosinesimil"));


        FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
                        QueryBuilders.boolQuery()
                                .should(QueryBuilders.existsQuery(TEXT_VECTOR_FIELD).boost(Float.parseFloat(esSettings.getOrDefault("BOOST", BOOST))))
                                .should(QueryBuilders.existsQuery(IMAGE_VECTOR_FIELD).boost(Float.parseFloat(esSettings.getOrDefault("BOOST", BOOST)))),
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(ScoreFunctionBuilders.scriptFunction(scriptText)
                                        .setWeight(Float.parseFloat(esSettings.getOrDefault("TEXT_WEIGHT", TEXT_WEIGHT)))),
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(ScoreFunctionBuilders.scriptFunction(scriptImage)
                                        .setWeight(Float.parseFloat(esSettings.getOrDefault("IMAGE_WEIGHT", IMAGE_WEIGHT))))
                        }
                )
                .scoreMode(FunctionScoreQuery.ScoreMode.fromString(esSettings.getOrDefault("SCORE_MODE", SCORE_MODE)))
                .boostMode(CombineFunction.fromString(esSettings.getOrDefault("BOOST_MODE", BOOST_MODE)));

        return functionScoreQueryBuilder;
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