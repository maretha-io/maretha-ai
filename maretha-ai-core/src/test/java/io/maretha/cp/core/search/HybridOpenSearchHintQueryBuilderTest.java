package io.maretha.cp.core.search;

import org.junit.Test;
import org.opensearch.client.opensearch._types.query_dsl.HybridQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NeuralQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.util.List;

import static org.junit.Assert.*;

public class HybridOpenSearchHintQueryBuilderTest {

    private final HybridOpenSearchHintQueryBuilder builder = new HybridOpenSearchHintQueryBuilder();

    @Test
    public void testMake_BasicHybridQuery() {
        // keywordField|neuralField|modelId|k|minScore|queryText
        String value = "dc:title|title_embedding|my_model_id|10||My search query";
        Query.Builder result = builder.make(null, "hybrid", value);

        assertNotNull(result);
        Query query = result.build();
        assertTrue(query.isHybrid());

        HybridQuery hybrid = query.hybrid();
        List<Query> subQueries = hybrid.queries();
        assertEquals(2, subQueries.size());

        // Lexical Match
        Query q1 = subQueries.get(0);
        assertTrue(q1.isMatch());
        MatchQuery match = q1.match();
        assertEquals("dc:title", match.field());
        assertEquals("My search query", match.query().stringValue());

        // Neural Query
        Query q2 = subQueries.get(1);
        assertTrue(q2.isNeural());
        NeuralQuery neural = q2.neural();
        assertEquals("title_embedding", neural.field());
        assertEquals("my_model_id", neural.modelId());
        assertEquals(Integer.valueOf(10), neural.k());
        assertEquals("My search query", neural.queryText());
        assertNull(neural.minScore());
    }

    @Test
    public void testMake_WithMinScore() {
        String value = "dc:description|desc_embedding|model_1|5|0.8|Another test";
        Query.Builder result = builder.make(null, "hybrid", value);

        Query query = result.build();
        List<Query> subQueries = query.hybrid().queries();

        NeuralQuery neural = subQueries.get(1).neural();
        assertEquals(Float.valueOf(0.8f), neural.minScore());
    }

    @Test
    public void testMake_Length5() {
        String value = "dc:description|desc_embedding|model_1|5|Query text only";
        Query.Builder result = builder.make(null, "hybrid", value);

        Query query = result.build();
        List<Query> subQueries = query.hybrid().queries();

        NeuralQuery neural = subQueries.get(1).neural();
        assertNull(neural.minScore());
        assertEquals("Query text only", neural.queryText());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMake_InvalidFormat_TooFewParts() {
        builder.make(null, "hybrid", "title|title_embedding|modelId|10");
    }
}
