package io.maretha.cp.core.search;

import org.junit.Test;
import org.opensearch.client.opensearch._types.query_dsl.KnnQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link KnnOpenSearchHintQueryBuilder}.
 * These are pure unit tests — no Nuxeo runtime required.
 */
public class KnnOpenSearchHintQueryBuilderTest {

    private final KnnOpenSearchHintQueryBuilder builder = new KnnOpenSearchHintQueryBuilder();

    @Test
    public void testMake_BasicKnnQuery() {
        String value = "10|0.5|0.1,0.2,0.3,0.4,0.5";
        Query.Builder result = builder.make(null, "interactionresults:embeddingsText", value);

        assertNotNull(result);
        Query query = result.build();
        assertTrue(query.isKnn());

        KnnQuery knn = query.knn();
        assertEquals("interactionresults:embeddingsText", knn.field());
        assertEquals(Integer.valueOf(10), knn.k());
        assertEquals(Float.valueOf(0.5f), knn.minScore());

        float[] vector = knn.vector();
        assertEquals(5, vector.length);
        assertEquals(0.1f, vector[0], 0.001f);
        assertEquals(0.2f, vector[1], 0.001f);
        assertEquals(0.3f, vector[2], 0.001f);
        assertEquals(0.4f, vector[3], 0.001f);
        assertEquals(0.5f, vector[4], 0.001f);
    }

    @Test
    public void testMake_DifferentKAndMinScore() {
        String value = "20|0.8|1.0,-1.0,0.0";
        Query.Builder result = builder.make(null, "interactionresults:embeddingsImage", value);

        Query query = result.build();
        KnnQuery knn = query.knn();

        assertEquals("interactionresults:embeddingsImage", knn.field());
        assertEquals(Integer.valueOf(20), knn.k());
        assertEquals(Float.valueOf(0.8f), knn.minScore());
        assertEquals(3, knn.vector().length);
        assertEquals(1.0f, knn.vector()[0], 0.001f);
        assertEquals(-1.0f, knn.vector()[1], 0.001f);
        assertEquals(0.0f, knn.vector()[2], 0.001f);
    }

    @Test
    public void testMake_LargeVector() {
        // Build a 1024-dim vector
        StringBuilder sb = new StringBuilder("5|0.4|");
        for (int i = 0; i < 1024; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(String.format("%.4f", (float) i / 1024));
        }
        Query.Builder result = builder.make(null, "interactionresults:embeddingsText", sb.toString());

        Query query = result.build();
        KnnQuery knn = query.knn();
        assertEquals(1024, knn.vector().length);
        assertEquals(Integer.valueOf(5), knn.k());
        assertEquals(Float.valueOf(0.4f), knn.minScore());
    }

    @Test
    public void testMake_NegativeValues() {
        String value = "10|0.3|-0.5,0.7,-0.3,0.1";
        Query.Builder result = builder.make(null, "field", value);

        KnnQuery knn = result.build().knn();
        assertEquals(-0.5f, knn.vector()[0], 0.001f);
        assertEquals(0.7f, knn.vector()[1], 0.001f);
        assertEquals(-0.3f, knn.vector()[2], 0.001f);
        assertEquals(0.1f, knn.vector()[3], 0.001f);
    }

    @Test
    public void testMake_ZeroMinScore() {
        String value = "10|0.0|0.1,0.2";
        Query.Builder result = builder.make(null, "field", value);

        KnnQuery knn = result.build().knn();
        assertEquals(Float.valueOf(0.0f), knn.minScore());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMake_InvalidFormat_MissingParts() {
        builder.make(null, "field", "10|0.5");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMake_InvalidFormat_NoPipes() {
        builder.make(null, "field", "just-a-string");
    }

    @Test(expected = NumberFormatException.class)
    public void testMake_InvalidK() {
        builder.make(null, "field", "abc|0.5|0.1,0.2");
    }

    @Test(expected = NumberFormatException.class)
    public void testMake_InvalidMinScore() {
        builder.make(null, "field", "10|xyz|0.1,0.2");
    }

    @Test(expected = NumberFormatException.class)
    public void testMake_InvalidVectorValue() {
        builder.make(null, "field", "10|0.5|0.1,abc,0.3");
    }
}
