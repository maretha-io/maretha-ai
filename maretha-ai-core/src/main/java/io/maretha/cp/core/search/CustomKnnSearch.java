package io.maretha.cp.core.search;

import io.maretha.cp.core.services.ai.EmbeddingsService;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
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

import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;

public class CustomKnnSearch extends ElasticSearchNxqlPageProvider {

    public static final String RELEVANCE_SCORE = "relevance_score";
    public static final String TEXT_VECTOR_FIELD = "interactionresults:embeddingsText";
    public static final String IMAGE_VECTOR_FIELD = "interactionresults:embeddingsImage";

    private static final String BOOST = "0.5";
    private static final int NUM_CANDIDATES = 1000;
    private static final String K = "10";

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

        float minScore = Float.parseFloat(namedParameters.getOrDefault("min_score", "0.4"));
        String vectorField = namedParameters.get("vectorField");
        String vectorQuery = namedParameters.get("vectorQuery");

        if (query == null) {
            buildQuery(session);
        }
        if (query == null) {
            throw new NuxeoException(String.format("Cannot perform null query: check provider '%s'", getName()));
        }

        NxQueryBuilder nxQuery = this.getQueryBuilder(session);
        BoolQueryBuilder combinedQuery = QueryBuilders.boolQuery();
        combinedQuery.filter(getCurrentQueryAsEsBuilder()); // keep NXQL filters

//        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
//        Map<String, String> esSettings = savedSettings.getEsSettings();
//        if (MapUtils.isEmpty(esSettings)) {
//            esSettings = new HashMap<>();
//        }

        int numCandidates = NUM_CANDIDATES;
        int k = Integer.parseInt(K);
        float boostValue = Float.parseFloat(BOOST);

        // --- Build KNN logic ---
        if (StringUtils.isNotEmpty(vectorQuery)) {

            // Case 1: text search
            if ("text".equalsIgnoreCase(vectorField)) {
                double[] vector = Framework.getService(EmbeddingsService.class).textToEmbedding(vectorQuery);
                String vectorJson = Arrays.toString(vector);
                String knnTextJson = buildKnnFunctionScore(TEXT_VECTOR_FIELD, vectorJson, k, numCandidates);
                QueryBuilder textKnnQuery = QueryBuilders.wrapperQuery(knnTextJson);
                BoolQueryBuilder shouldQuery = QueryBuilders.boolQuery()
                        .should(textKnnQuery);
                combinedQuery.must(shouldQuery).boost(boostValue);

                // Case 2: image or text search
            } else if ("image".equalsIgnoreCase(vectorField) || "similarity".equalsIgnoreCase(vectorField)) {
                String knnImageJson = buildKnnFunctionScore(IMAGE_VECTOR_FIELD, vectorQuery, k, numCandidates);
                String knnTextJson = buildKnnFunctionScore(TEXT_VECTOR_FIELD, vectorQuery, k, numCandidates);
                QueryBuilder textKnnQuery = QueryBuilders.wrapperQuery(knnTextJson);

                QueryBuilder knnImageQuery = QueryBuilders.wrapperQuery(knnImageJson);
                BoolQueryBuilder shouldQuery = QueryBuilders.boolQuery()
                        .should(knnImageQuery)
                        .should(textKnnQuery);
                combinedQuery.must(shouldQuery).boost(boostValue);
            }
        }

        // --- Execute Elasticsearch query ---
        nxQuery = nxQuery.esQuery(combinedQuery)
                .fetchFromElasticsearch()
                .offset((int) getCurrentPageOffset())
                .limit(getLimit())
                .addAggregates(buildAggregates());

        log.debug("ES KNN query: " + nxQuery.makeQuery());

        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(nxQuery.makeQuery())
                .from(nxQuery.getOffset())
                .minScore(minScore);

        for (AbstractAggregationBuilder<?> agg : nxQuery.getEsAggregates()) {
            sourceBuilder.aggregation(agg);
        }

        // Sorting
        List<SortInfo> sortInfos = getSortInfos();
        for (SortInfo sortInfo : sortInfos) {
            String field = sortInfo.getSortColumn();
            if (field.contains(RELEVANCE_SCORE)) {
                field = "_score";
            }
            SortOrder order = sortInfo.getSortAscending() ? SortOrder.ASC : SortOrder.DESC;
            sourceBuilder.sort(field, order);
        }

        searchRequest.source(sourceBuilder);

        ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
        ESClient client = esa.getClient();
        SearchResponse response = client.search(searchRequest);

        // Fetch results
        VcsFetcher fetcher = new VcsFetcher(getCoreSession(), response, null);
        SearchHits hits = response.getHits();
        List<DocumentModel> docs = fetcher.fetchDocuments();
        List<DocumentModel> result = new ArrayList<>();

        for (SearchHit hit : hits.getHits()) {
            docs.stream()
                    .filter(doc -> doc.getId().equals(hit.getId()))
                    .findFirst()
                    .ifPresent(doc -> {
                        doc.putContextData(RELEVANCE_SCORE, hit.getScore());
                        result.add(doc);
                    });
        }

        currentPageDocuments = result;
        currentAggregates = new HashMap<>(getResultAggregates(nxQuery, response).size());
        for (Aggregate<Bucket> agg : getResultAggregates(nxQuery, response)) {
            currentAggregates.put(agg.getId(), agg);
        }

        setResultsCount(result.size());
        return result;
    }

    /**
     * Build OpenSearch-style function_score + knn block
     */
    private String buildKnnFunctionScore(String field, String vectorJson, int k, int numCandidates) {
        return "{\n" +
                "  \"function_score\": {\n" +
                "    \"query\": {\n" +
                "      \"knn\": {\n" +
                "        \"field\": \"" + field + "\",\n" +
                "        \"query_vector\": " + vectorJson + ",\n" +
                "        \"k\": " + k + ",\n" +
                "        \"num_candidates\": " + numCandidates + "\n" +
                "      }\n" +
                "    },\n" +
                "    \"boost_mode\": \"multiply\"\n" +
                "  }\n" +
                "}";
    }

    public DocumentModelList getEmptyResult() {
        setResultsCount(0);
        return new DocumentModelListImpl();
    }

    public List<Aggregate<Bucket>> getResultAggregates(NxQueryBuilder queryBuilder, SearchResponse response) {
        for (AggregateEsBase<Aggregation, Bucket> agg : queryBuilder.getAggregates()) {
            Filter filter = response.getAggregations().get(NxQueryBuilder.getAggregateFilterId(agg));
            if (filter == null) continue;
            Aggregation aggregation = filter.getAggregations().get(agg.getId());
            if (aggregation == null) continue;
            agg.parseAggregation(aggregation);
        }
        @SuppressWarnings("unchecked")
        List<Aggregate<Bucket>> ret = (List<Aggregate<Bucket>>) (List<?>) queryBuilder.getAggregates();
        return ret;
    }
}
