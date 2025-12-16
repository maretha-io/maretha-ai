package io.maretha.cp.core.services.helper;

import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.services.utils.NuxeoDocResolverUtils;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Helper class for building document context recursively.
 * Handles depth limits and cycle detection when traversing document
 * relationships.
 * Extracted from RunInteractionServiceImpl to improve testability.
 */
@Log4j2
public class DocumentContextBuilder {

    private static final int MAX_DEPTH = 5;

    private DocumentContextBuilder() {
        // Utility class, prevent instantiation
    }

    /**
     * Builds context map for a document, starting with depth 0 and empty visited
     * set.
     *
     * @param session              Core session
     * @param doc                  Document to build context for
     * @param schemaFieldsProvider Function to get schema fields for a document
     * @return Context map with document metadata and related document contexts
     */
    @SneakyThrows
    public static Map<String, Object> buildContext(
            CoreSession session,
            DocumentModel doc,
            BiFunction<DocumentModel, CoreSession, Set<DocTypeWithSchema.Schema.Field>> schemaFieldsProvider) {
        return buildContext(session, doc, 0, new HashSet<>(), schemaFieldsProvider);
    }

    /**
     * Builds context map recursively with depth tracking and cycle detection.
     *
     * @param session              Core session
     * @param doc                  Document to build context for
     * @param depth                Current recursion depth
     * @param visitedDocIds        Set of already visited document IDs (for cycle
     *                             detection)
     * @param schemaFieldsProvider Function to get schema fields for a document
     * @return Context map with document metadata and related document contexts
     */
    @SneakyThrows
    public static Map<String, Object> buildContext(
            CoreSession session,
            DocumentModel doc,
            int depth,
            Set<String> visitedDocIds,
            BiFunction<DocumentModel, CoreSession, Set<DocTypeWithSchema.Schema.Field>> schemaFieldsProvider) {

        if (depth >= MAX_DEPTH) {
            log.warn("Max recursion depth {} reached for document: {}", MAX_DEPTH, doc.getId());
            return new HashMap<>();
        }

        if (visitedDocIds.contains(doc.getId())) {
            log.debug("Document already visited, skipping: {}", doc.getId());
            return new HashMap<>();
        }
        visitedDocIds.add(doc.getId());

        Set<DocTypeWithSchema.Schema.Field> schemaFields = schemaFieldsProvider.apply(doc, session);
        Map<String, Object> valuesMap = new HashMap<>();

        schemaFields.forEach(
                field -> processField(session, doc, field, depth, visitedDocIds, schemaFieldsProvider, valuesMap));

        return valuesMap;
    }

    private static void processField(
            CoreSession session,
            DocumentModel doc,
            DocTypeWithSchema.Schema.Field field,
            int depth,
            Set<String> visitedDocIds,
            BiFunction<DocumentModel, CoreSession, Set<DocTypeWithSchema.Schema.Field>> schemaFieldsProvider,
            Map<String, Object> valuesMap) {

        String xpath = field.getXpath();
        try {
            // Check if field references other documents (recursive case)
            if (NuxeoDocResolverUtils.isDocumentReference(doc, xpath)) {
                Map<String, Object> relatedDocContext = buildRelatedDocumentsContext(
                        session, doc, xpath, depth, visitedDocIds, schemaFieldsProvider);
                valuesMap.put(xpath, relatedDocContext);
            } else {
                // Simple field: get property value
                Serializable propertyValue = doc.getPropertyValue(xpath);
                valuesMap.put(xpath, propertyValue);
            }
        } catch (Exception e) {
            log.debug("Failed to process field: {}", xpath, e);
        }
    }

    private static Map<String, Object> buildRelatedDocumentsContext(
            CoreSession session,
            DocumentModel doc,
            String xpath,
            int depth,
            Set<String> visitedDocIds,
            BiFunction<DocumentModel, CoreSession, Set<DocTypeWithSchema.Schema.Field>> schemaFieldsProvider) {

        Map<String, Object> relatedDocContext = new HashMap<>();
        List<DocumentModel> relatedDocs = NuxeoDocResolverUtils.resolveDocumentReferences(session, doc, xpath);

        relatedDocs.forEach(relatedDoc -> {
            // RECURSIVE CALL with incremented depth
            Map<String, Object> context = buildContext(
                    session, relatedDoc, depth + 1, visitedDocIds, schemaFieldsProvider);

            if (context != null && !context.isEmpty()) {
                relatedDocContext.put(relatedDoc.getId(), context);
            }
        });

        return relatedDocContext;
    }

    /**
     * Gets all parent documents of a document up to the root.
     *
     * @param session Core session
     * @param doc     Starting document
     * @return List of parent documents (excluding root)
     */
    public static List<DocumentModel> getAllParentDocuments(CoreSession session, DocumentModel doc) {
        List<DocumentModel> parents = new ArrayList<>();

        var parentRef = doc.getParentRef();
        while (parentRef != null) {
            DocumentModel parentDoc = session.getDocument(parentRef);

            // Stop at root document
            if (isRootDocument(parentDoc)) {
                break;
            }

            parents.add(parentDoc);
            parentRef = parentDoc.getParentRef();
        }

        return parents;
    }

    private static boolean isRootDocument(DocumentModel doc) {
        return "00000000-0000-0000-0000-000000000000".equalsIgnoreCase(doc.getId()) ||
                "/".equals(doc.getPathAsString());
    }

    /**
     * Gets all related documents referenced by a document's schema fields.
     *
     * @param session              Core session
     * @param doc                  Document to get related docs for
     * @param schemaFieldsProvider Function to get schema fields
     * @return List of related documents
     */
    public static List<DocumentModel> getRelatedDocuments(
            CoreSession session,
            DocumentModel doc,
            BiFunction<DocumentModel, Map<String, DocTypeWithSchema.Schema>, Set<DocTypeWithSchema.Schema.Field>> schemaFieldsProvider,
            Map<String, DocTypeWithSchema.Schema> schemaSettings) {

        List<DocumentModel> allRelatedDocuments = new ArrayList<>();
        Set<DocTypeWithSchema.Schema.Field> schemaFields = schemaFieldsProvider.apply(doc, schemaSettings);

        schemaFields.forEach(field -> {
            String xpath = field.getXpath();
            try {
                if (NuxeoDocResolverUtils.isDocumentReference(doc, xpath)) {
                    List<DocumentModel> relatedDocumentModels = NuxeoDocResolverUtils.resolveDocumentReferences(session,
                            doc, xpath);
                    allRelatedDocuments.addAll(relatedDocumentModels);
                }
            } catch (Exception ignored) {
                // Silently ignore resolution errors
            }
        });

        return allRelatedDocuments;
    }
}
