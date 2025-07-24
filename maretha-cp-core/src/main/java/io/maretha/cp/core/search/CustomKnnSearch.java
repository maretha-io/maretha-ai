package io.maretha.cp.core.search;

import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.services.APIService;
import io.maretha.cp.core.services.RepositoryService;
import org.apache.commons.collections.MapUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.elasticsearch.aggregate.AggregateEsBase;
import org.nuxeo.elasticsearch.api.ESClient;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.fetcher.VcsFetcher;
import org.nuxeo.elasticsearch.provider.ElasticSearchNxqlPageProvider;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.lucene.search.function.CombineFunction;
import org.opensearch.common.lucene.search.function.FunctionScoreQuery;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.index.query.functionscore.ScoreFunctionBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;

public class CustomKnnSearch extends ElasticSearchNxqlPageProvider {
    public static final String RELEVANCE_SCORE = "relevance_score";
    public static final String TEXT_VECTOR_FIELD = "interactionresults:embeddingsText";
    public static final String IMAGE_VECTOR_FIELD = "interactionresults:embeddingsImage";
    private static final String BOOST = "1.0";
    private static final String K = "10";
    private static final String TEXT_WEIGHT = "1.0";
    private static final String IMAGE_WEIGHT = "1.0";
    private static final String SCORE_MODE = "AVG";
    private static final String BOOST_MODE = "AVG";
    private List<DocumentModel> results;
    private boolean useVertesiaApiSearch = false;

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
        if (!namedParameters.containsKey("vectorField") && !namedParameters.containsKey("vectorQuery")) {
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

        NxQueryBuilder nxQuery = this.getQueryBuilder(session);

        // Combine NXQL and KNN using "must" (AND logic)
        BoolQueryBuilder combinedQuery = QueryBuilders.boolQuery();


        if (searchOnAllRepositories()) {
            nxQuery.searchOnAllRepositories();
        }
        nxQuery.useUnrestrictedSession(useUnrestrictedSession());

        List<String> highlightFields = getHighlights();
        if (highlightFields != null && !highlightFields.isEmpty()) {
            nxQuery.highlight(highlightFields);
        }

        combinedQuery = combinedQuery.filter(getCurrentQueryAsEsBuilder());

        if (!useVertesiaApiSearch) {
            Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
            Map<String, String> esSettings = savedSettings.getEsSettings();
            if (MapUtils.isEmpty(esSettings)) {
                esSettings = new HashMap<>();
            }
            if ("text".equals(vectorField)) {
                APIService apiService = Framework.getService(APIService.class);
                double[] textVectors = apiService.textToEmbeddings(vectorQuery);
                vectorQuery = Arrays.toString(textVectors);
            }
            FunctionScoreQueryBuilder knnQuery = knnQuery(vectorQuery, minScore, esSettings);
            combinedQuery = combinedQuery.must(knnQuery);
        }

        nxQuery = nxQuery.esQuery(combinedQuery)
                .fetchFromElasticsearch() // Force ES query
                .offset((int) this.getCurrentPageOffset())
                .limit(this.getLimit())
                .addAggregates(this.buildAggregates());

        log.debug("ES KNN query: " + nxQuery.makeQuery());

        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(nxQuery.makeQuery())
                .from(nxQuery.getOffset()).minScore(minScore);

        //add aggregates
        for (AbstractAggregationBuilder<?> aggregate : nxQuery.getEsAggregates()) {
            searchSourceBuilder.aggregation(aggregate);
        }

        List<SortInfo> sortInfos = getSortInfos();
        for (SortInfo sortInfo : sortInfos) {
            String field = sortInfo.getSortColumn();
            boolean ascending = sortInfo.getSortAscending();
            if (field.contains(RELEVANCE_SCORE)) {
                field = "_score";
            }
            SortOrder order = ascending ? SortOrder.ASC : SortOrder.DESC;
            searchSourceBuilder.sort(field, order);
        }

        searchRequest.source(searchSourceBuilder);

        ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
        ESClient client = esa.getClient();

        SearchResponse response = client.search(searchRequest);

        VcsFetcher fetcher = new VcsFetcher(getCoreSession(), response, null);

        SearchHits hits = response.getHits();

        List<DocumentModel> documents = fetcher.fetchDocuments();

        //reorder using relevance
        List<DocumentModel> result = new ArrayList<>();
        for (SearchHit hit : hits.getHits()) {
            Optional<DocumentModel> documentOpt = documents.stream().filter(doc -> doc.getId().equals(hit.getId())).findFirst();
            documentOpt.ifPresent(doc -> {
                doc.putContextData(RELEVANCE_SCORE, hit.getScore());
                result.add(doc);
            });
        }

        currentPageDocuments = result;

        currentAggregates = new HashMap<>(getResultAggregates(nxQuery, response).size());
        for (Aggregate<Bucket> agg : getResultAggregates(nxQuery, response)) {
            currentAggregates.put(agg.getId(), agg);
        }

        // set total number of hits
        setResultsCount(result.size());

        return result;
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
        return new FunctionScoreQueryBuilder(boolQuery, functionBuilders)
                .scoreMode(scoreMode)
                .boostMode(boostMode)
                .boost(boost)
                .setMinScore(minScore);
    }


    public List<Aggregate<Bucket>> getResultAggregates(NxQueryBuilder queryBuilder, SearchResponse response) {
        for (AggregateEsBase<Aggregation, Bucket> agg : queryBuilder.getAggregates()) {
            Filter filter = response.getAggregations().get(NxQueryBuilder.getAggregateFilterId(agg));
            if (filter == null) {
                continue;
            }
            Aggregation aggregation = filter.getAggregations().get(agg.getId());
            if (aggregation == null) {
                continue;
            }
            agg.parseAggregation(aggregation);
        }
        @SuppressWarnings("unchecked")
        List<Aggregate<Bucket>> ret = (List<Aggregate<Bucket>>) (List<?>) queryBuilder.getAggregates();
        return ret;
    }

    public QueryBuilder getAggregateFilter(NxQueryBuilder builder) {
        BoolQueryBuilder ret = QueryBuilders.boolQuery();
        for (AggregateEsBase<?, ?> agg : builder.getAggregates()) {
            QueryBuilder filter = agg.getEsFilter();
            if (filter != null) {
                ret.must(filter);
            }
        }
        if (!ret.hasClauses()) {
            return null;
        }
        return ret;
    }

}