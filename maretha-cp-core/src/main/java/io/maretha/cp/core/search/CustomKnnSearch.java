package io.maretha.cp.core.search;

import io.maretha.cp.core.services.APIService;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;

public class CustomKnnSearch extends ElasticSearchNxqlPageProvider {
    public static final String TEXT_VECTOR_FIELD = "interactionresults:embeddingsText";
    public static final String IMAGE_VECTOR_FIELD = "interactionresults:embeddingsImage";
    private static final long serialVersionUID = 1L;
    private static final String SEMANTIC_SEARCH_K = "10";
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
        NxQueryBuilder nxQuery = this.getQueryBuilder(session).nxql(query);

        // Combine NXQL and KNN using "must" (AND logic)
        BoolQueryBuilder combinedQuery = QueryBuilders
                .boolQuery();

        String vectorQuery = namedParameters.get("vectorQuery");
        if (StringUtils.isNotEmpty(vectorQuery) && StringUtils.isNotEmpty(vectorField) && !"[]".equals(vectorQuery)) {
            int numCandidates = 100;
            //requires Opensearch in Nuxeo 2023
            double minScore = Double.parseDouble(namedParameters.getOrDefault("min_score", "0.4"));

            if (TEXT_VECTOR_FIELD.equals(vectorField)) {
                APIService apiService = Framework.getService(APIService.class);
                double[] textVectors = apiService.textToEmbeddings(vectorQuery);
                String vectorJson = Arrays.toString(textVectors);
                String knnJsonQuery = "{\n" +
                        "  \"knn\": {\n" +
                        "    \"field\": \"" + TEXT_VECTOR_FIELD + "\",\n" +
                        "    \"query_vector\": " + vectorJson + ",\n" +
                        "    \"k\": " + SEMANTIC_SEARCH_K + ",\n" +
                        "    \"num_candidates\": " + numCandidates + "\n" +
                        "  }\n" +
                        "}";

                QueryBuilder knnTextQuery = QueryBuilders.wrapperQuery(knnJsonQuery);
                combinedQuery = combinedQuery.must(knnTextQuery);
            }

            if (IMAGE_VECTOR_FIELD.equals(vectorField)) {
                String knnJsonQuery = "{\n" +
                        "  \"knn\": {\n" +
                        "    \"field\": \"" + IMAGE_VECTOR_FIELD + "\",\n" +
                        "    \"query_vector\": " + vectorQuery + ",\n" +
                        "    \"k\": " + SEMANTIC_SEARCH_K + ",\n" +
                        "    \"num_candidates\": " + numCandidates + "\n" +
                        "  }\n" +
                        "}";

                QueryBuilder knnTextQuery = QueryBuilders.wrapperQuery(knnJsonQuery);
                combinedQuery = combinedQuery.must(knnTextQuery);
            }


            combinedQuery = combinedQuery.must(getCurrentQueryAsEsBuilder());
            nxQuery = nxQuery.esQuery(combinedQuery)
                    .fetchFromElasticsearch() // Force ES query
                    .offset((int) this.getCurrentPageOffset())
                    .limit(this.getLimit())
                    .addAggregates(this.buildAggregates());

            log.debug("ES KNN query: " + nxQuery.makeQuery());
        }

        if (searchOnAllRepositories()) {
            nxQuery.searchOnAllRepositories();
        }
        nxQuery.useUnrestrictedSession(useUnrestrictedSession());

        List<String> highlightFields = getHighlights();
        if (highlightFields != null && !highlightFields.isEmpty()) {
            nxQuery.highlight(highlightFields);
        }

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
}