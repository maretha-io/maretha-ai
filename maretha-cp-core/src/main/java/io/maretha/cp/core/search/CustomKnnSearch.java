package io.maretha.cp.core.search;

import io.maretha.cp.core.services.APIService;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.provider.ElasticSearchNxqlPageProvider;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;

import java.util.Arrays;
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

        if (query == null) {
            buildQuery(session);
        }
        if (query == null) {
            throw new NuxeoException(String.format("Cannot perform null query: check provider '%s'", getName()));
        }

        String vectorQuery = namedParameters.get("vectorQuery");
        if (StringUtils.isEmpty(vectorQuery) && "[]".equals(vectorQuery)) {
            throw new IllegalArgumentException("Missing property vectorQuery for kNN query");
        }

        String vectorField = namedParameters.get("vectorField");
        if (StringUtils.isEmpty(vectorQuery)) {
            throw new IllegalArgumentException("Missing property vectorField for kNN query");
        }

        String vectorJson;
        String selectedVectorField;
        if (TEXT_VECTOR_FIELD.equals(vectorField)) {
            APIService apiService = Framework.getService(APIService.class);
            selectedVectorField = TEXT_VECTOR_FIELD;
            double[] vectors = apiService.textToEmbeddings(vectorQuery);
            vectorJson = Arrays.toString(vectors);
        } else {
            selectedVectorField = IMAGE_VECTOR_FIELD;
            vectorJson = vectorQuery;
        }

        //requires Opensearch in Nuxeo 2023
        double minScore = Double.parseDouble(namedParameters.getOrDefault("min_score", "0.4"));

        String knnJsonQuery = "{\n" +
                "  \"knn\": {\n" +
                "    \"" + selectedVectorField + "\": {\n" +
                "      \"vector\": " + vectorJson + ",\n" +
                "      \"k\": " + SEMANTIC_SEARCH_K + "\n" +
                "    }\n" +
                "  }\n" +
                "}";

        log.debug("ES KNN query: " + knnJsonQuery);

        ElasticSearchService esService = Framework.getService(ElasticSearchService.class);
        QueryBuilder queryBuilder = QueryBuilders.wrapperQuery(knnJsonQuery);
        NxQueryBuilder nxQuery = new NxQueryBuilder(session)
                .nxql(query)
                .esQuery(queryBuilder);

        DocumentModelList resultList = esService.query(nxQuery);
        setResultsCount(resultList.totalSize());
        return resultList;
    }

    public DocumentModelList getEmptyResult() {
        setResultsCount(0);
        return new DocumentModelListImpl();
    }
}