package io.maretha.cp.core.search;

import org.junit.Test;
import org.opensearch.client.opensearch._types.query_dsl.NeuralQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import static org.junit.Assert.*;

public class NeuralOpenSearchHintQueryBuilderTest {

    private final NeuralOpenSearchHintQueryBuilder builder = new NeuralOpenSearchHintQueryBuilder();

    @Test
    public void testMake_BasicNeuralQuery() {
        String value = "my_model_id|10|Here is my test query";
        Query.Builder result = builder.make(null, "text_embedding", value);

        assertNotNull(result);
        Query query = result.build();
        assertTrue(query.isNeural());

        NeuralQuery neural = query.neural();
        assertEquals("text_embedding", neural.field());
        assertEquals("my_model_id", neural.modelId());
        assertEquals(Integer.valueOf(10), neural.k());
        assertEquals("Here is my test query", neural.queryText());
        assertNull("min score shouldn't be set if not provided", neural.minScore());
    }

    @Test
    public void testMake_WithMinScore() {
        String value = "model_abc|5|0.75|A more specific query";
        Query.Builder result = builder.make(null, "image_embedding", value);

        Query query = result.build();
        NeuralQuery neural = query.neural();
        
        assertEquals("image_embedding", neural.field());
        assertEquals("model_abc", neural.modelId());
        assertEquals(Integer.valueOf(5), neural.k());
        assertEquals(Float.valueOf(0.75f), neural.minScore());
        assertEquals("A more specific query", neural.queryText());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMake_InvalidFormat_TooFewParts() {
        builder.make(null, "field", "modelId|10");
    }

    @Test(expected = NumberFormatException.class)
    public void testMake_InvalidK() {
        builder.make(null, "field", "modelId|abc|queryText");
    }
}
