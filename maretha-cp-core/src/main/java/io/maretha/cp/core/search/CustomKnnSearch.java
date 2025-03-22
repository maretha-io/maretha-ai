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
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.index.query.functionscore.ScoreFunctionBuilders;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORE_ID_SCHEMA_PATH;
import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;

public class CustomKnnSearch extends ElasticSearchNxqlPageProvider {
    public static final String TEXT_VECTOR_FIELD = "interactionresults:embeddingsText";
    public static final String IMAGE_VECTOR_FIELD = "interactionresults:embeddingsImage";
    public static final String RELEVANCE_SCORE = "relevance_score";
    private static final String BOOST = "1.0";
    private static final String K = "10";
    private static final String TEXT_WEIGHT = "1.0";
    private static final String IMAGE_WEIGHT = "1.0";
    private static final String SCORE_MODE = "AVG";
    private static final String BOOST_MODE = "AVG";
    private static final boolean VERTESIA_SEARCH = true; //todo make configurable
    private final RepositoryService settingsService = Framework.getService(RepositoryService.class);
    private final APIService apiService = Framework.getService(APIService.class);
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

        Map<String, Double> idsAndScores = new HashMap<>();

        if (StringUtils.isNotEmpty(vectorQuery) && !"[]".equals(vectorQuery)) {
            if (TEXT_VECTOR_FIELD.equals(vectorField)) {
                APIService apiService = Framework.getService(APIService.class);
                double[] textVectors = apiService.textToEmbeddings(vectorQuery);
                vectorQuery = Arrays.toString(textVectors);
            }

            if (!VERTESIA_SEARCH) {
                Settings settings = settingsService.getSavedSettings(CoreInstance.getCoreSessionSystem(null));
                Map<String, String> esSettings = settings.getEsSettings();
                FunctionScoreQueryBuilder knnQuery = knnQuery(vectorQuery, minScore, esSettings);
                combinedQuery = combinedQuery.must(knnQuery);
            } else {
                Map<String, Double> docIdScoreMapTextSearch = apiService.search(vectorQuery, "text", 100);
                Map<String, Double> docIdScoreMapImageSearch = apiService.search(vectorQuery, "image", 100);
                Map<String, Double> docIdScoreMapPropertiesSearch = apiService.search(vectorQuery, "properties", 100);
                docIdScoreMapTextSearch.forEach((id, score) -> {
                    if (score >= minScore)
                        idsAndScores.merge(id, score, Math::max);
                });
                docIdScoreMapImageSearch.forEach((id, score) -> {
                    if (score >= minScore)
                        idsAndScores.merge(id, score, Math::max);
                });
                docIdScoreMapPropertiesSearch.forEach((id, score) -> {
                    if (score >= minScore)
                        idsAndScores.merge(id, score, Math::max);
                });
                combinedQuery.must(QueryBuilders.termsQuery(RESULTS_STORE_ID_SCHEMA_PATH, idsAndScores.keySet()));
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
        List<DocumentModel> dmList = ret.getDocuments();

        if (VERTESIA_SEARCH && StringUtils.isNotEmpty(vectorQuery) && !"[]".equals(vectorQuery)) {
            dmList.forEach(dcm -> {
                String id = (String) session.getDocument(dcm.getRef()).getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
                if (StringUtils.isNotEmpty(id) && idsAndScores.containsKey(id)) {
                    dcm.putContextData(RELEVANCE_SCORE, idsAndScores.get(id));
                }
            });
            dmList.sort((doc1, doc2) -> Double.compare((double) doc2.getContextData(RELEVANCE_SCORE), (double) doc1.getContextData(RELEVANCE_SCORE)));
        }

        currentAggregates = new HashMap<>(ret.getAggregates().size());
        for (Aggregate<Bucket> agg : ret.getAggregates()) {
            currentAggregates.put(agg.getId(), agg);
        }
        setResultsCount(dmList.size());
        currentPageDocuments = dmList;
        return currentPageDocuments;
    }

    public DocumentModelList getEmptyResult() {
        setResultsCount(0);
        return new DocumentModelListImpl();
    }

    private FunctionScoreQueryBuilder knnQuery(String vector, float minScore, Map<String, String> esSettings) {
        float imageWeight = Float.parseFloat(esSettings.getOrDefault("IMAGE_WEIGHT", IMAGE_WEIGHT));
        float textWeight = Float.parseFloat(esSettings.getOrDefault("TEXT_WEIGHT", TEXT_WEIGHT));
        float boost = Float.parseFloat(esSettings.getOrDefault("BOOST", BOOST));
        float k = Float.parseFloat(esSettings.getOrDefault("K", K));
        FunctionScoreQuery.ScoreMode scoreMode = FunctionScoreQuery.ScoreMode.fromString(esSettings.getOrDefault("SCORE_MODE", SCORE_MODE));
        CombineFunction boostMode = CombineFunction.fromString(esSettings.getOrDefault("BOOST_MODE", BOOST_MODE));

        // Construct the KNN portion manually as a JSON string
        String textKnnQueryJson = "{ \"knn\": { \"" + TEXT_VECTOR_FIELD + "\": { \"vector\": " + vector + ", \"k\": " + k + " } } }";
        String imageKnnQueryJson = "{ \"knn\": { \"" + IMAGE_VECTOR_FIELD + "\": { \"vector\": " + vector + ", \"k\": " + k + " } } }";

        // Wrap KNN queries in QueryBuilder
        QueryBuilder textKnnQuery = QueryBuilders.wrapperQuery(textKnnQueryJson);
        QueryBuilder imageKnnQuery = QueryBuilders.wrapperQuery(imageKnnQueryJson);

        // Build `bool` query with `should` (OR condition for multiple KNN fields)
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .should(textKnnQuery)
                .should(imageKnnQuery);


        // Assign different weights to each KNN query
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] functionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.existsQuery(TEXT_VECTOR_FIELD),
                        ScoreFunctionBuilders.weightFactorFunction(textWeight)),
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.existsQuery(IMAGE_VECTOR_FIELD),
                        ScoreFunctionBuilders.weightFactorFunction(imageWeight))
        };

        // Apply function score to each KNN query
        FunctionScoreQueryBuilder functionScore = new FunctionScoreQueryBuilder(boolQuery, functionBuilders)
                .scoreMode(scoreMode)
                .boostMode(boostMode)
                .boost(boost)
                .setMinScore(minScore);

//        FunctionScoreQueryBuilder imageFunctionScore = new FunctionScoreQueryBuilder(imageKnnQuery,
//                ScoreFunctionBuilders.weightFactorFunction(imageWeight))
//                .scoreMode(scoreMode)
//                .boostMode(boostMode)
//                .boost(boost)
//                .setMinScore(minScore);

        // Combine queries using BoolQueryBuilder
        return functionScore;
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