package io.maretha.cp.core.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.HybridQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NeuralQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.util.Arrays;
import java.util.List;

/**
 * OpenSearch hint query builder for Hybrid AI search (Lexical + Neural).
 * <p>
 * Builds a native OpenSearch {@link HybridQuery} from NXQL ES hints.
 * Usage in NXQL:
 * 
 * <pre>
 * SELECT * FROM Document WHERE /*+ES: INDEX(dummy_field) OPERATOR(hybrid) *&#47;
 *   dummy_field = 'keywordField|neuralField|modelId|k|minScore|queryText'
 * </pre>
 */
public class HybridOpenSearchHintQueryBuilder implements OpenSearchHintQueryBuilder {

    private static final Log log = LogFactory.getLog(HybridOpenSearchHintQueryBuilder.class);

    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        String valueStr = value.toString();

        String[] parts = valueStr.split("\\|", 6);
        if (parts.length < 5) {
            throw new IllegalArgumentException(
                    "Hybrid hint value must be 'keywordField|neuralField|modelId|k|minScore|queryText' but got: "
                            + valueStr.substring(0, Math.min(50, valueStr.length())));
        }

        String keywordField = parts[0].trim();
        String neuralField = parts[1].trim();
        String modelId = parts[2].trim();
        int k = Integer.parseInt(parts[3].trim());
        
        float minScore = -1f;
        String queryText;

        if (parts.length == 6) {
            String scoreStr = parts[4].trim();
            if (!scoreStr.isEmpty()) {
                minScore = Float.parseFloat(scoreStr);
            }
            queryText = parts[5].trim();
        } else {
            // length is 5
            queryText = parts[4].trim();
        }

        log.debug(String.format("Building Hybrid query: keywordField=%s, neuralField=%s, modelId=%s, k=%d",
                keywordField, neuralField, modelId, k));

        // 1. Build Lexical Query
        String finalQueryText = queryText;
        Query matchQuery = Query.of(q -> q
                .match(m -> m
                        .field(keywordField)
                        .query(FieldValue.of(finalQueryText))
                )
        );

        // 2. Build Neural Query
        final float finalMinScore = minScore;
        Query neuralQuery = Query.of(q -> q
                .neural(n -> {
                    n.field(neuralField)
                     .modelId(modelId)
                     .queryText(finalQueryText)
                     .k(k);
                    if (finalMinScore >= 0f) {
                        n.minScore(finalMinScore);
                    }
                    return n;
                })
        );

        // 3. Wrap in Hybrid Query
        HybridQuery hybridQuery = HybridQuery.of(q -> q
                .queries(Arrays.asList(matchQuery, neuralQuery))
        );

        Query.Builder builder = new Query.Builder();
        builder.hybrid(hybridQuery);
        return builder;
    }
}
