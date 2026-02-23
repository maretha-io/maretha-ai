package io.maretha.cp.core.search;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link CustomKnnSearch} helper methods.
 * Uses reflection to test private methods directly without Nuxeo runtime.
 */
public class CustomKnnSearchTest {

    private CustomKnnSearch knnSearch;
    private Method parseVectorFromJsonMethod;
    private Method buildNxqlWithKnnHintMethod;

    @Before
    public void setUp() throws Exception {
        knnSearch = new CustomKnnSearch();

        parseVectorFromJsonMethod = CustomKnnSearch.class.getDeclaredMethod("parseVectorFromJson", String.class);
        parseVectorFromJsonMethod.setAccessible(true);

        buildNxqlWithKnnHintMethod = CustomKnnSearch.class.getDeclaredMethod(
                "buildNxqlWithKnnHint", String[].class, double[].class, int.class, float.class);
        buildNxqlWithKnnHintMethod.setAccessible(true);
    }

    // --- parseVectorFromJson tests ---

    @Test
    public void testParseVectorFromJson_BasicArray() throws Exception {
        double[] result = (double[]) parseVectorFromJsonMethod.invoke(knnSearch, "[0.1, 0.2, 0.3]");
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(0.1, result[0], 0.001);
        assertEquals(0.2, result[1], 0.001);
        assertEquals(0.3, result[2], 0.001);
    }

    @Test
    public void testParseVectorFromJson_WithoutBrackets() throws Exception {
        double[] result = (double[]) parseVectorFromJsonMethod.invoke(knnSearch, "0.5, -0.3, 0.7");
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(0.5, result[0], 0.001);
        assertEquals(-0.3, result[1], 0.001);
        assertEquals(0.7, result[2], 0.001);
    }

    @Test
    public void testParseVectorFromJson_NegativeValues() throws Exception {
        double[] result = (double[]) parseVectorFromJsonMethod.invoke(knnSearch, "[-0.5, 0.7, -0.3, 0.1]");
        assertNotNull(result);
        assertEquals(4, result.length);
        assertEquals(-0.5, result[0], 0.001);
        assertEquals(0.7, result[1], 0.001);
    }

    @Test
    public void testParseVectorFromJson_SingleValue() throws Exception {
        double[] result = (double[]) parseVectorFromJsonMethod.invoke(knnSearch, "[1.0]");
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(1.0, result[0], 0.001);
    }

    @Test
    public void testParseVectorFromJson_WithWhitespace() throws Exception {
        double[] result = (double[]) parseVectorFromJsonMethod.invoke(knnSearch, "  [ 0.1 , 0.2 , 0.3 ]  ");
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(0.1, result[0], 0.001);
    }

    @Test
    public void testParseVectorFromJson_InvalidReturnsNull() throws Exception {
        double[] result = (double[]) parseVectorFromJsonMethod.invoke(knnSearch, "invalid");
        assertNull(result);
    }

    // --- buildNxqlWithKnnHint tests ---

    @Test
    public void testBuildNxqlWithKnnHint_SingleField_NoBaseQuery() throws Exception {
        double[] vector = { 0.1, 0.2, 0.3 };
        String[] fields = { "interactionresults:embeddingsText" };

        String result = (String) buildNxqlWithKnnHintMethod.invoke(knnSearch, fields, vector, 10, 0.4f);

        assertNotNull(result);
        assertTrue(result.startsWith("SELECT * FROM Document WHERE"));
        assertTrue(result.contains("OPERATOR(knn)"));
        assertTrue(result.contains("INDEX(interactionresults:embeddingsText)"));
        assertTrue(result.contains("10|0.4|"));
        assertTrue(result.contains("interactionresults:embeddingsText ="));
        // Should NOT have OR (single field)
        assertFalse(result.contains(" OR "));
    }

    @Test
    public void testBuildNxqlWithKnnHint_MultiField_NoBaseQuery() throws Exception {
        double[] vector = { 0.1, 0.2, 0.3 };
        String[] fields = { "interactionresults:embeddingsImage", "interactionresults:embeddingsText" };

        String result = (String) buildNxqlWithKnnHintMethod.invoke(knnSearch, fields, vector, 10, 0.4f);

        assertNotNull(result);
        assertTrue(result.startsWith("SELECT * FROM Document WHERE"));
        // Should have OR for multi-field
        assertTrue(result.contains(" OR "));
        // Should have both fields
        assertTrue(result.contains("INDEX(interactionresults:embeddingsImage)"));
        assertTrue(result.contains("INDEX(interactionresults:embeddingsText)"));
        // Should be wrapped in parentheses
        assertTrue(result.contains("(/*+ES:"));
    }

    @Test
    public void testBuildNxqlWithKnnHint_SingleField_WithBaseQuery() throws Exception {
        // Simulate a base query being set on the page provider
        java.lang.reflect.Field queryField = CustomKnnSearch.class.getSuperclass().getDeclaredField("query");
        queryField.setAccessible(true);
        queryField.set(knnSearch, "SELECT * FROM Document WHERE ecm:primaryType = 'InteractionResult'");

        double[] vector = { 0.1, 0.2 };
        String[] fields = { "interactionresults:embeddingsText" };

        String result = (String) buildNxqlWithKnnHintMethod.invoke(knnSearch, fields, vector, 5, 0.3f);

        assertNotNull(result);
        assertTrue(result.contains("OPERATOR(knn)"));
        assertTrue(result.contains("AND ecm:primaryType = 'InteractionResult'"));
        assertTrue(result.contains("5|0.3|"));
    }

    @Test
    public void testBuildNxqlWithKnnHint_MultiField_WithBaseQuery() throws Exception {
        java.lang.reflect.Field queryField = CustomKnnSearch.class.getSuperclass().getDeclaredField("query");
        queryField.setAccessible(true);
        queryField.set(knnSearch, "SELECT * FROM Document WHERE ecm:primaryType = 'InteractionResult'");

        double[] vector = { 0.1, 0.2 };
        String[] fields = { "interactionresults:embeddingsImage", "interactionresults:embeddingsText" };

        String result = (String) buildNxqlWithKnnHintMethod.invoke(knnSearch, fields, vector, 10, 0.4f);

        assertNotNull(result);
        // Multi-field with OR, combined with base filter via AND
        assertTrue(result.contains(" OR "));
        assertTrue(result.contains("AND ecm:primaryType = 'InteractionResult'"));
        assertTrue(result.contains("INDEX(interactionresults:embeddingsImage)"));
        assertTrue(result.contains("INDEX(interactionresults:embeddingsText)"));
    }

    @Test
    public void testBuildNxqlWithKnnHint_VectorValuesArePacked() throws Exception {
        double[] vector = { 0.123456, -0.789012, 0.0 };
        String[] fields = { "field" };

        String result = (String) buildNxqlWithKnnHintMethod.invoke(knnSearch, fields, vector, 10, 0.5f);

        // Vector values should be comma-separated after k|minScore|
        assertTrue(result.contains("10|0.5|"));
        // Check that values are present (as floats)
        assertTrue(result.contains("0.123456"));
        assertTrue(result.contains("-0.789012"));
    }

    @Test
    public void testBuildNxqlWithKnnHint_KAndMinScoreInOutput() throws Exception {
        double[] vector = { 0.1 };
        String[] fields = { "field" };

        String result = (String) buildNxqlWithKnnHintMethod.invoke(knnSearch, fields, vector, 20, 0.75f);

        assertTrue(result.contains("20|0.75|"));
    }
}
