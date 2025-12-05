package io.maretha.cp.core.services.helper;

import com.fasterxml.jackson.databind.JsonNode;
import io.maretha.cp.core.models.DocTypeWithSchema;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.validation.DocumentValidationService;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static io.maretha.cp.core.services.utils.AuditUtils.logAIAudit;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

/**
 * Helper class for metadata validation and saving operations.
 * Extracted from RunInteractionServiceImpl to improve maintainability.
 */
@Log4j2
public class MetadataOperations {

    private MetadataOperations() {
        // Utility class, prevent instantiation
    }

    /**
     * Validates and saves metadata from JSON response to document.
     *
     * @param jsonNode                 JSON response containing metadata
     * @param docId                    Document ID to update
     * @param saveMetadataModeOverride If true, overwrite existing values; if false,
     *                                 append
     * @param schemasFields            Schema fields from settings
     */
    public static void validateAndSaveMetadata(
            JsonNode jsonNode,
            String docId,
            boolean saveMetadataModeOverride,
            Set<DocTypeWithSchema.Schema.Field> schemasFields) {

        CoreSession session = CoreInstance.getCoreSessionSystem(null);
        DocumentModel doc = session.getDocument(new IdRef(docId));

        try {
            DocumentValidationService validationService = Framework.getService(DocumentValidationService.class);
            JsonNode resultNode = extractResultNode(jsonNode);

            resultNode.fields().forEachRemaining(field -> processMetadataField(field, doc, schemasFields,
                    validationService, saveMetadataModeOverride, session));

            session.saveDocument(doc);
        } catch (Exception e) {
            log.error("Could not save document metadata", e);
            throw new RuntimeException("Failed to save document metadata: " + e.getMessage());
        }
    }

    private static JsonNode extractResultNode(JsonNode jsonNode) {
        try {
            JsonNode resultNode = jsonNode.get("result");
            if (resultNode == null) {
                log.debug(jsonNode);
                throw new NuxeoException("ResultNode is null, cannot save metadata.");
            }
            return resultNode;
        } catch (Exception e) {
            log.error("Missing json result from API Response", e);
            throw e;
        }
    }

    private static void processMetadataField(
            Map.Entry<String, JsonNode> field,
            DocumentModel doc,
            Set<DocTypeWithSchema.Schema.Field> schemasFields,
            DocumentValidationService validationService,
            boolean saveMetadataModeOverride,
            CoreSession session) {

        String key = field.getKey();
        JsonNode valueNode = field.getValue();

        if (!isValidFieldValue(key, valueNode)) {
            return;
        }

        Optional<DocTypeWithSchema.Schema.Field> optionalField = schemasFields.stream()
                .filter(schemaField -> schemaField.getXpath().equalsIgnoreCase(key))
                .findFirst();

        if (optionalField.isEmpty()) {
            log.warn("Metadata save skip on not declared xpath: " + key);
            return;
        }

        try {
            if ("nxtag:tags".equalsIgnoreCase(key)) {
                handleTagsField(doc, valueNode, session);
                return;
            }

            if (optionalField.get().isMultiValue() && valueNode.isArray()) {
                handleMultiValueField(doc, key, valueNode, validationService, saveMetadataModeOverride, session);
                return;
            }

            handleSingleValueField(doc, key, valueNode, validationService, saveMetadataModeOverride, session);
        } catch (Exception ex) {
            log.error("Failed to set metadata for " + key + ":" + valueNode.toPrettyString(), ex);
        }
    }

    private static boolean isValidFieldValue(String key, JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            log.warn("Metadata save skip for null value: " + key);
            return false;
        }
        if (StringUtils.isEmpty(valueNode.asText()) && valueNode.isEmpty()) {
            log.warn("Metadata save skip on empty value for xpath: " + key);
            return false;
        }
        return true;
    }

    private static void handleTagsField(DocumentModel doc, JsonNode valueNode, CoreSession session) {
        log.debug("Adding nxtag:tags for documentId: " + doc.getId());
        List<String> newTags = new ArrayList<>();
        valueNode.forEach(element -> newTags.add(element.asText()));
        appendTags(doc, newTags);
        logAIAudit(session, doc, "", "Saved AI metadata nxtag:tags : " + newTags);
    }

    private static void handleMultiValueField(
            DocumentModel doc,
            String key,
            JsonNode valueNode,
            DocumentValidationService validationService,
            boolean saveMetadataModeOverride,
            CoreSession session) {

        Set<String> values = new LinkedHashSet<>();
        valueNode.forEach(element -> values.add(element.asText()));

        if (saveMetadataModeOverride) {
            updateMetadataFieldArray(doc, validationService, key, values);
        } else {
            appendMetadataFieldArray(doc, validationService, key, values);
        }

        logAIAudit(session, doc, "", "Saved AI metadata " + key + " : " + values);
    }

    private static void handleSingleValueField(
            DocumentModel doc,
            String key,
            JsonNode valueNode,
            DocumentValidationService validationService,
            boolean saveMetadataModeOverride,
            CoreSession session) {

        Serializable propertyValue = doc.getPropertyValue(key);
        if (isNullOrEmpty(propertyValue) || saveMetadataModeOverride) {
            saveMetadataField(doc, validationService, key, valueNode.asText());
            logAIAudit(session, doc, "", "Saved AI metadata " + key + " : " + valueNode.asText());
        }
    }

    /**
     * Appends tags to document without duplicates.
     */
    public static void appendTags(DocumentModel doc, List<String> newTags) {
        if (CollectionUtils.isEmpty(newTags)) {
            return;
        }

        if (!doc.hasFacet("NXTag")) {
            doc.addFacet("NXTag");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> existingTags = (List<Map<String, String>>) doc.getPropertyValue("nxtag:tags");
        List<Map<String, String>> tagsList = new ArrayList<>(existingTags);

        // Build set of existing labels for fast lookup
        Set<String> existingLabels = tagsList.stream()
                .map(tagMap -> tagMap.get("label"))
                .collect(Collectors.toSet());

        // Add new tags only if not already present
        for (String tagLabel : newTags) {
            if (!existingLabels.contains(tagLabel)) {
                tagsList.add(Map.of("label", tagLabel, "username", "system"));
                existingLabels.add(tagLabel);
            }
        }

        if (CollectionUtils.isNotEmpty(tagsList)) {
            doc.setPropertyValue("nxtag:tags", (Serializable) tagsList);
            log.debug("Updated nxtag:tags for documentId: " + doc.getId() + " new tags:" + tagsList);
        }
    }

    /**
     * Saves a single metadata field value.
     */
    public static void saveMetadataField(
            DocumentModel doc,
            DocumentValidationService validationService,
            String key,
            Serializable value) {

        if (!validationService.validate(key, value).hasError()) {
            doc.setPropertyValue(key, value);
            log.debug(String.format("Saved metadata key: %s value: %s", key, formatValue(value)));
        } else {
            log.error(String.format("Failed to save metadata key: %s value: %s", key, formatValue(value)));
        }
    }

    /**
     * Overwrites metadata field array with new values.
     */
    public static void updateMetadataFieldArray(
            DocumentModel doc,
            DocumentValidationService validationService,
            String key,
            Set<String> newValues) {

        if (newValues == null || newValues.isEmpty()) {
            log.error(String.format("Skip save metadata for key: %s on empty value: %s",
                    key, formatValue((Serializable) newValues)));
            return;
        }

        ArrayList<String> result = new ArrayList<>(newValues);

        if (!validationService.validate(key, result).hasError()) {
            doc.setPropertyValue(key, result);
            log.debug(String.format("Saved metadata key: %s value: %s", key, formatValue(result)));
        } else {
            log.error(String.format("Failed to save metadata key: %s value: %s", key, formatValue(result)));
        }
    }

    /**
     * Appends values to metadata field array (no duplicates).
     */
    public static void appendMetadataFieldArray(
            DocumentModel doc,
            DocumentValidationService validationService,
            String key,
            Set<String> newValues) {

        if (newValues == null || newValues.isEmpty()) {
            log.error(String.format("Skip save metadata for key: %s on empty value: %s",
                    key, formatValue((Serializable) newValues)));
            return;
        }

        Object value = doc.getPropertyValue(key);
        ArrayList<String> existingValues = extractExistingValues(value);

        // Use LinkedHashSet to preserve order: new first, then existing
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        combined.addAll(newValues);
        combined.addAll(existingValues);

        ArrayList<String> result = new ArrayList<>(combined);

        if (!validationService.validate(key, result).hasError()) {
            doc.setPropertyValue(key, result);
            log.debug(String.format("Saved metadata key: %s value: %s", key, formatValue(result)));
        } else {
            log.error(String.format("Failed to save metadata key: %s value: %s", key, formatValue(result)));
        }
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<String> extractExistingValues(Object value) {
        if (value instanceof String[]) {
            return new ArrayList<>(Arrays.asList((String[]) value));
        } else if (value instanceof ArrayList) {
            return (ArrayList<String>) value;
        }
        return new ArrayList<>();
    }

    private static String formatValue(Serializable value) {
        if (value instanceof String[]) {
            return Arrays.toString((String[]) value);
        }
        return String.valueOf(value);
    }

    private static boolean isNullOrEmpty(Serializable value) {
        return value == null ||
                (value instanceof String && StringUtils.isEmpty((String) value)) ||
                !isNotEmpty(value);
    }
}
