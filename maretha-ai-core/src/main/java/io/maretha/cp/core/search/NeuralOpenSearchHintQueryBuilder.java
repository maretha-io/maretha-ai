package io.maretha.cp.core.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.opensearch.client.opensearch._types.query_dsl.NeuralQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

/**
 * OpenSearch hint query builder for Neural AI search.
 * <p>
 * Builds a native OpenSearch {@link NeuralQuery} from NXQL ES hints.
 * Usage in NXQL:
 * 
 * <pre>
 * SELECT * FROM Document WHERE /*+ES: INDEX(field) OPERATOR(neural) *&#47;
 *   field = 'modelId|k|queryText'
 *   // OR
 *   field = 'modelId|k|minScore|queryText'
 * </pre>
 */
public class NeuralOpenSearchHintQueryBuilder implements OpenSearchHintQueryBuilder {

    private static final Log log = LogFactory.getLog(NeuralOpenSearchHintQueryBuilder.class);

    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        String valueStr = value.toString();

        // Parse format based on pipes
        String[] parts = valueStr.split("\\|", 4);
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Neural hint value must be 'modelId|k|queryText' or 'modelId|k|minScore|queryText' but got: "
                            + valueStr.substring(0, Math.min(50, valueStr.length())));
        }

        String modelId = parts[0].trim();
        int k = Integer.parseInt(parts[1].trim());

        float minScore = -1f;
        String queryText;

        if (parts.length == 4) {
            minScore = Float.parseFloat(parts[2].trim());
            queryText = parts[3].trim();
        } else {
            queryText = parts[2].trim();
        }

        log.debug(String.format("Building Neural query: field=%s, modelId=%s, k=%d, minScore=%.2f",
                fieldName, modelId, k, minScore));

        final float finalMinScore = minScore;
        final String finalQueryText = queryText;

        NeuralQuery neuralQuery = NeuralQuery.of(q -> {
            q.field(fieldName)
             .modelId(modelId)
             .queryText(finalQueryText)
             .k(k);
            if (parts.length == 4) {
                q.minScore(finalMinScore);
            }
            return q;
        });

        Query.Builder builder = new Query.Builder();
        builder.neural(neuralQuery);
        return builder;
    }
}
