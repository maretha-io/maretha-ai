package io.maretha.cp.core.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.opensearch.client.opensearch._types.query_dsl.KnnQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenSearch hint query builder for kNN vector similarity search.
 * <p>
 * Builds a native OpenSearch {@link KnnQuery} from NXQL ES hints.
 * Usage in NXQL:
 * 
 * <pre>
 * SELECT * FROM Document WHERE /*+ES: INDEX(field) OPERATOR(knn) *&#47;
 *   field = 'k|minScore|v1,v2,v3,...'
 * </pre>
 * <p>
 * The value string format is pipe-separated: {@code k|minScore|v1,v2,v3,...}
 * <ul>
 * <li>{@code k} - number of nearest neighbors to return</li>
 * <li>{@code minScore} - minimum similarity score threshold</li>
 * <li>{@code v1,v2,...} - comma-separated vector components</li>
 * </ul>
 */
public class KnnOpenSearchHintQueryBuilder implements OpenSearchHintQueryBuilder {

    private static final Log log = LogFactory.getLog(KnnOpenSearchHintQueryBuilder.class);

    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        String valueStr = value.toString();

        // Parse k|minScore|v1,v2,v3,...
        String[] parts = valueStr.split("\\|", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "kNN hint value must be 'k|minScore|v1,v2,...' but got: "
                            + valueStr.substring(0, Math.min(50, valueStr.length())));
        }

        int k = Integer.parseInt(parts[0].trim());
        float minScore = Float.parseFloat(parts[1].trim());
        String[] vectorParts = parts[2].split(",");
        List<Float> vector = new ArrayList<>(vectorParts.length);
        for (int i = 0; i < vectorParts.length; i++) {
            vector.add(Float.parseFloat(vectorParts[i].trim()));
        }

        log.debug(String.format("Building kNN query: field=%s, k=%d, minScore=%.2f, vectorDim=%d",
                fieldName, k, minScore, vector.size()));

        KnnQuery knnQuery = KnnQuery.of(q -> q
                .field(fieldName)
                .vector(vector)
                .k(k)
                .minScore(minScore));

        Query.Builder builder = new Query.Builder();
        builder.knn(knnQuery);
        return builder;
    }
}
