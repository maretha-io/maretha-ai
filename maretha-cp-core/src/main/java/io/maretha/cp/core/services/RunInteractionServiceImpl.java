package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.services.ai.InteractionService;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.Tika;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.api.validation.DocumentValidationService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.picture.api.adapters.PictureBlobHolder;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.maretha.cp.core.callback.VertesiaCallback.VERTESIA_CALLBACK_API_KEY;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_FACET;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORED_DATE_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORE_DIGEST_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORE_ID_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_EMBEDDINGS_IMAGE;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_EMBEDDINGS_PROPERTIES;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_EMBEDDINGS_TEXT;
import static io.maretha.cp.core.services.utils.AuditUtils.logAIAudit;

public class RunInteractionServiceImpl implements RunInteractionService {

    public static final String MIME_TYPES = "vertesia.mimeTypes";
    public static final String CALLBACK_NOTIFY_URL = "callbackNotifyUrl";
    private static final String DEFAULT_DIRECTORY_BUCKET_SIZE = "100";
    private final Log log = LogFactory.getLog(RunInteractionServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static String getSupportedMimeTypes() {
        return Framework.getService(RepositoryService.class).getSettingsProperty(MIME_TYPES, "text/plain,application/pdf");
    }

    public static Blob getBlob(DocumentModel doc) {
        if (doc.hasFacet("Picture")) {
            PictureBlobHolder pictureBlobHolder = new PictureBlobHolder(doc, "picture:views/3/content"); //FullHD
            Blob blob = pictureBlobHolder.getBlob();
            if (blob != null && blob.getLength() > 0 && !blob.getFilename().contains("empty_picture"))
                return blob;
        }
        return (Blob) doc.getPropertyValue(FILE_CONTENT);
    }

    public static boolean isImage(Blob blob) {
        String mimeType = blob.getMimeType();
        return mimeType != null && mimeType.startsWith("image/");
    }

    @Override
    public String triggerInteraction(DocumentModel doc, CoreSession session, String interactionId, List<InteractionParameter> parameters, boolean saveMetadata, boolean saveMetadataModeOverride, boolean asyncMode) {
        logAIAudit(session, doc, "", "Triggering AI Interaction with id: " + interactionId);
        Map<String, Object> paramsMap = buildParameters(doc, session, parameters);
        if (saveMetadata) {
            triggerSaveMetadataRequest(doc, session, interactionId, saveMetadataModeOverride, paramsMap, asyncMode);
            return "Metadata request in progress.";
        }
        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
        Interaction interaction = getInteractionById(savedSettings, interactionId);
        return Framework.getService(InteractionService.class).invokeModel(doc, interaction, paramsMap);
    }

    private void triggerSaveMetadataRequest(DocumentModel doc, CoreSession session, String interactionId, boolean saveMetadataModeOverride, Map<String, Object> paramsMap, boolean asyncMode) {
        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
        int bucketSize = getBucketSize(savedSettings);

        Set<DocTypeWithSchema.Schema.Field> schemasFieldsInIsolation = getSchemaFields(doc, session, true);
        schemasFieldsInIsolation.forEach(schemaField -> {
            Map<String, String> directoryValues = schemaField.getDirectoryValues();
            if (MapUtils.isNotEmpty(directoryValues) && directoryValues.size() > bucketSize) {
                List<Map<String, String>> partitionedMap = partitionMap(directoryValues, bucketSize);
                partitionedMap.forEach(partition -> {
                    schemaField.setDirectoryValues(partition);
                    sendInteractionRequest(interactionId, paramsMap, Set.of(schemaField), saveMetadataModeOverride, doc, savedSettings, asyncMode);
                });
            } else {
                sendInteractionRequest(interactionId, paramsMap, Set.of(schemaField), saveMetadataModeOverride, doc, savedSettings, asyncMode);
            }
        });

        Set<DocTypeWithSchema.Schema.Field> schemaFieldsNotInIsolation = getSchemaFields(doc, session, false);
        if (CollectionUtils.isNotEmpty(schemaFieldsNotInIsolation)) {
            sendInteractionRequest(interactionId, paramsMap, schemaFieldsNotInIsolation, saveMetadataModeOverride, doc, savedSettings, asyncMode);
        }
    }

    private Interaction getInteractionById(Settings savedSettings, String interactionId) {
        Interaction interactionById = savedSettings.getInteractions().stream()
                .filter(interaction -> interaction.getId().equalsIgnoreCase(interactionId))
                .findFirst()
                .orElseThrow(() -> new NuxeoException(
                        "Interaction with ID '" + interactionId + "' not found."
                ));
        return interactionById;
    }

    private void sendInteractionRequest(String interactionId, Map<String, Object> paramsMap, Set<DocTypeWithSchema.Schema.Field> schemaFields, boolean saveMetadataModeOverride, DocumentModel doc, Settings savedSettings, boolean asyncMode) {
        Interaction interaction = getInteractionById(savedSettings, interactionId);
        String resultSchema = getResultSchema(schemaFields);
        String response;

        Map<String, Object> schemaFieldsMap = getSchemaFieldsMap(schemaFields);
        Map<String, Object> paramsData = addSchemasAndDictionariesToParams(schemaFieldsMap, paramsMap);
        if (schemaFields.size() == 1) {
            DocTypeWithSchema.Schema.Field schemaField = schemaFields.stream().findFirst().get();
            if (schemaField.isInIsolation()) {
                String customInteractionId = schemaField.getInteractionId();
                if (StringUtils.isNotEmpty(customInteractionId)) {
                    interaction = getInteractionById(savedSettings, customInteractionId);
                }
            }
        }

        response = Framework.getService(InteractionService.class).invokeModelWithSchema(doc, interaction, paramsData, resultSchema);
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            validateAndSaveMetadata(jsonNode, doc.getId(), saveMetadataModeOverride);
        } catch (Exception e) {
            throw new NuxeoException("Failed to save metadata for document: " + doc.getId(), e);
        }
        log.debug("API Response sendInteractionRequest: " + response);
    }

    @Override
    public void validateAndSaveMetadata(JsonNode jsonNode, String docId, boolean saveMetadataModeOverride) {
        CoreSession session = CoreInstance.getCoreSessionSystem(null);
        DocumentModel doc = session.getDocument(new IdRef(docId));

        Set<DocTypeWithSchema.Schema.Field> schemaFieldsNotInIsolation = getSchemaFields(doc, session, false);
        Set<DocTypeWithSchema.Schema.Field> schemaFieldsInIsolation = getSchemaFields(doc, session, true);
        Set<DocTypeWithSchema.Schema.Field> schemasFields = Stream.concat(schemaFieldsNotInIsolation.stream(), schemaFieldsInIsolation.stream())
                .collect(Collectors.toSet());

        try {
            DocumentValidationService validationService = Framework.getService(DocumentValidationService.class);
            JsonNode resultNode;
            try {
                resultNode = jsonNode.get("result");
            } catch (Exception e) {
                log.error("Missing json result from API Response", e);
                throw e;
            }
            resultNode.fields().forEachRemaining(field -> {
                String key = field.getKey();
                JsonNode valueNode = field.getValue();
                Optional<DocTypeWithSchema.Schema.Field> optionalField = schemasFields.stream()
                        .filter(schemaField -> schemaField.getXpath().equalsIgnoreCase(key)).findFirst();
                if (optionalField.isEmpty()) {
                    log.warn("Metadata save skip on not declared xpath: " + key);
                    return;
                }
                if (StringUtils.isEmpty(valueNode.asText()) && valueNode.isEmpty()) {
                    log.warn("Metadata save skip on empty value for xpath: " + key);
                    return;
                }
                try {
                    Serializable propertyValue = doc.getPropertyValue(key);
                    if ("nxtag:tags".equalsIgnoreCase(key)) {
                        log.debug("Adding nxtag:tags for documentId: " + doc.getId());
                        List<String> newTags = new ArrayList<>();
                        valueNode.forEach(element -> newTags.add(element.asText()));
                        appendTags(doc, newTags);
                        logAIAudit(session, doc, "", "Saved AI metadata nxtag:tags : " + newTags);
                        return;
                    }
                    if (optionalField.get().isMultiValue() && valueNode.isArray()) {
                        Set<String> values = new LinkedHashSet<>();
                        valueNode.forEach(element -> values.add(element.asText()));
                        appendMetadataFieldArray(doc, validationService, key, values);
                        logAIAudit(session, doc, "", "Saved AI metadata " + key + " : " + values);
                        return;
                    }
                    if (isNullOrEmpty(propertyValue) || saveMetadataModeOverride) {
                        saveMetadataField(doc, validationService, key, valueNode.asText());
                        logAIAudit(session, doc, "", "Saved AI metadata " + key + " : " + valueNode.asText());
                    }
                } catch (Exception ex) {
                    log.error("Failed to set metadata for " + key + ":" + valueNode.toPrettyString(), ex);
                }
            });
            session.saveDocument(doc);
        } catch (Exception e) {
            log.error("Could not save document metadata", e);
            throw new RuntimeException("Failed to save document metadata: " + e.getMessage());
        }
    }

    private void appendTags(DocumentModel doc, List<String> newTags) {
        if (CollectionUtils.isNotEmpty(newTags)) {
            if (!doc.hasFacet("NXTag")) {
                doc.addFacet("NXTag");
            }
            List<Map<String, String>> existingTags = (List<Map<String, String>>) doc.getPropertyValue("nxtag:tags");
            List<Map<String, String>> tagsList = new ArrayList<>(existingTags);

            // Build a Set of existing labels for fast lookup
            Set<String> existingLabels = tagsList.stream()
                    .map(tagMap -> tagMap.get("label"))
                    .collect(Collectors.toSet());

            // Add new tags only if their label is not already present
            for (String tagLabel : newTags) {
                if (!existingLabels.contains(tagLabel)) {
                    tagsList.add(Map.of("label", tagLabel, "username", "system"));
                    existingLabels.add(tagLabel);  // keep the set updated
                }
            }

            if (CollectionUtils.isNotEmpty(tagsList)) {
                doc.setPropertyValue("nxtag:tags", (Serializable) tagsList);
                log.debug("Updated nxtag:tags for documentId: " + doc.getId() + " new tags:" + tagsList);
            }
        }
    }

    private void saveMetadataField(DocumentModel doc, DocumentValidationService validationService, String key, Serializable value) {
        if (!validationService.validate(key, value).hasError()) {
            doc.setPropertyValue(key, value);
            log.debug(String.format("Saved metadata key: %s value: %s", key, formatValue(value)));
        } else {
            log.error(String.format("Failed to save metadata key: %s value: %s", key, formatValue(value)));
        }
    }

    private void appendMetadataFieldArray(DocumentModel doc, DocumentValidationService validationService, String key, Set<String> newValues) {
        if (newValues == null || newValues.isEmpty()) {
            log.error(String.format("Skip save metadata for key: %s on empty value: %s", key, formatValue((Serializable) newValues)));
            return;
        }

        Object value = doc.getPropertyValue(key);
        ArrayList<String> existingValues;

        if (value instanceof String[]) {
            String[] valuesArray = (String[]) value;
            existingValues = new ArrayList<>(Arrays.asList(valuesArray));
        } else if (value instanceof ArrayList) {
            existingValues = (ArrayList<String>) value;
        } else {
            existingValues = new ArrayList<>(); // fallback if null or unexpected type
        }

        // Use LinkedHashSet to preserve order: new first, then existing (if not duplicated)
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

    private String formatValue(Serializable value) {
        if (value instanceof String[]) {
            return Arrays.toString((String[]) value);
        } else {
            return String.valueOf(value);
        }
    }

    public DocumentModel uploadFileAndSetStoreId(DocumentModel doc, CoreSession session, boolean waitForCompletion, boolean lowPriority) {
        log.debug("Uploading file for documentId: " + doc.getId());
        String storeId = null;
        Blob blob = getBlob(doc);
        if (doc.hasFacet(RESULTS_FACET)) {
            storeId = (String) doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
            String storeDigest = (String) doc.getPropertyValue(RESULTS_STORE_DIGEST_SCHEMA_PATH);
            if (!blob.getDigest().equalsIgnoreCase(storeDigest)) {
                storeId = ""; //re-upload file
            }
        }
        if (StringUtils.isEmpty(storeId)) {
            doc.addFacet(RESULTS_FACET);
            Settings settings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
            String fileTypeId = (String) settings.getAiSettings().getOrDefault("fileTypeId", "");
            logAIAudit(session, doc, "", "Sending blob/rendition for AI processing.");
            if (settings.isAwsS3AccessEnabled()) {
                String s3Path = getS3Path(blob);
                storeId = Framework.getService(APIService.class).uploadS3FileToStore(blob, s3Path, fileTypeId, lowPriority);
            } else {
                storeId = Framework.getService(APIService.class).uploadFileToStore(blob, fileTypeId, lowPriority);
            }
            doc.setPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH, storeId);
            doc.setPropertyValue(RESULTS_STORE_DIGEST_SCHEMA_PATH, blob.getDigest());
            doc.setPropertyValue(RESULTS_STORED_DATE_SCHEMA_PATH, new GregorianCalendar());

            doc = session.saveDocument(doc);
        }

//        try {
//            //Retry for uploaded Vertesia document to have status completed
//            if (waitForCompletion) {
//                Framework.getService(APIService.class).checkProcessingStatusCompleted(storeId, true);
//            }
//
//            if (isImage(blob) && CollectionUtils.isEmpty((Collection) doc.getPropertyValue(SCHEMA_EMBEDDINGS_IMAGE))) {
//                addImageEmbeddings(doc);
//                doc = session.saveDocument(doc);
//            }
//
//            if (CollectionUtils.isEmpty((Collection) doc.getPropertyValue(SCHEMA_EMBEDDINGS_TEXT))) {
//                addTextEmbeddings(doc);
//                doc = session.saveDocument(doc);
//            }
//
//            if (CollectionUtils.isEmpty((Collection) doc.getPropertyValue(SCHEMA_EMBEDDINGS_PROPERTIES))) {
//                addPropertiesEmbeddings(doc);
//                doc = session.saveDocument(doc);
//            }
//        } catch (Exception e) {
//            log.error("Failed to save document: " + doc.getId(), e);
//        }

        return doc;
    }

    @Override
    public String validateProcessStatusCompleted(DocumentModel doc) {
        String storeId = null;
        if (doc.hasFacet(RESULTS_FACET)) {
            storeId = (String) doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
        }
        if (StringUtils.isEmpty(storeId)) {
            throw new NuxeoException("The document does not have: " + RESULTS_STORE_ID_SCHEMA_PATH + ", docID:" + doc.getId());
        }
        return Framework.getService(APIService.class).checkProcessingStatusCompleted(storeId, false);
    }

    private Map<String, Object> buildParameters(DocumentModel doc, CoreSession session, List<InteractionParameter> parameters) {
        Map<String, Object> paramsMap = new HashMap<>();
        parameters.forEach(param -> {
            String name = param.getName();
            Object value = param.getValue();
            String type = param.getType();
            String format = param.getFormat();
            List<String> selectedDocumentIds = param.getSelectedDocumentIds();
            String selectedDocumentId = param.getSelectedDocumentId();

            if (param.isAdditionalContext()) {
                String additionalContext = getAdditionalContext(session, doc);
                paramsMap.put(name, additionalContext);
                return;
            }
            boolean isDocumentOrMediaFormat = "document".equalsIgnoreCase(format) || "media".equalsIgnoreCase(format);
            if (isDocumentOrMediaFormat) {
                Blob blob = getBlob(doc);
                if (isImage(blob)) {
                    try {
                        String base64 = Base64.getEncoder().encodeToString(blob.getByteArray());
                        value = "data:" + blob.getMimeType() + ";base64," + base64;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try (InputStream in = blob.getStream()) {
                        Tika tika = new Tika();
                        value = tika.parseToString(in);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            paramsMap.put(name, value);
        });
        return paramsMap;
    }

    @SneakyThrows
    private String getAdditionalContext(CoreSession session, DocumentModel doc) {
        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
        Map<String, DocTypeWithSchema.Schema> schemaSettings = savedSettings.getSchemaSettings();
        if (MapUtils.isEmpty(schemaSettings)) {
            log.error("SchemaSettings is empty");
            return "";
        }

        Set<DocTypeWithSchema.Schema.Field> schemaFields = new HashSet<>();

        Arrays.stream(doc.getSchemas()).forEach(schema -> {
            DocTypeWithSchema.Schema schemaSetting = getSchemaSetting(schemaSettings, doc.getType(), schema);
            if (schemaSetting != null) {
                Set<DocTypeWithSchema.Schema.Field> fields = schemaSetting.getFields().stream()
                        .filter(DocTypeWithSchema.Schema.Field::isAdditionalContext)
                        .collect(Collectors.toSet());
                schemaFields.addAll(fields);
            }
        });

        Map<String, String> valuesMap = new HashMap<>();
        schemaFields.forEach(field -> {
            String xpath = field.getXpath();
            try {
                Serializable propertyValue = doc.getPropertyValue(xpath);
                String value = objectMapper.writeValueAsString(propertyValue);
                valuesMap.put(xpath, value);
            } catch (Exception ignored) {
            }
        });
        return objectMapper.writeValueAsString(valuesMap);
    }

    //source: "s3://composable-bs-test/my-photo.jpg#eu-north-1",
    private String getS3Path(Blob blob) {
        CloudStorageFileShare cloudStorageFileShare = Framework.getService(CloudStorageFileShare.class);
        return cloudStorageFileShare.getS3FilePath((ManagedBlob) blob);
    }

    private Map<String, String> getDictionaryValues(CoreSession session, DocumentModel doc, String directoryName) {
        Map<String, String> dictionaryMap = new HashMap<>();
        try {
            Map<String, Serializable> params = new HashMap<>();
            params.put("directoryName", directoryName);
            try {
                Property prodlineProp = doc.getProperty("licensing:product_line");
                if (prodlineProp != null && prodlineProp.getValue() != null) {
                    String prodlineId = prodlineProp.getValue().toString();
                    params.put("prodline", prodlineId);
                }
            } catch (PropertyNotFoundException propertyNotFoundException) {
                //ignore
            }
            OperationContext ctx = new OperationContext(session);
            Blob directoriesJsonBlob = (Blob) Framework.getService(AutomationService.class).run(ctx, "Directory.SuggestEntries", params);

            if (directoriesJsonBlob == null) {
                log.warn("No suggestions returned for directory: " + directoryName);
                return dictionaryMap;
            }

            // Parse JSON as tree
            String json = directoriesJsonBlob.getString();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // Iterate array
            for (JsonNode node : root) {
                boolean isObsolete = node.has("obsolete") &&
                        (node.get("obsolete").asLong(0) > 0 || node.get("obsolete").asBoolean(false));

                if (!isObsolete && node.hasNonNull("id") && node.hasNonNull("label")) {
                    dictionaryMap.put(node.get("id").asText(), node.get("label").asText());
                }
            }
        } catch (Exception e) {
            log.error("Failed to get directory: " + directoryName, e);
        }
        return dictionaryMap;
    }

    private Set<DocTypeWithSchema.Schema.Field> getSchemaFields(DocumentModel doc, CoreSession session, boolean inIsolation) {
        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
        Map<String, DocTypeWithSchema.Schema> schemaSettings = savedSettings.getSchemaSettings();
        if (MapUtils.isEmpty(schemaSettings)) {
            log.error("SchemaSettings is empty");
            return Collections.emptySet();
        }

        Set<DocTypeWithSchema.Schema.Field> schemaFields = new HashSet<>();

        Arrays.stream(doc.getSchemas()).forEach(schema -> {
            DocTypeWithSchema.Schema schemaSetting = getSchemaSetting(schemaSettings, doc.getType(), schema);
            if (schemaSetting != null) {
                addSchemaFields(doc, session, inIsolation, schemaSetting, schemaFields);
            }
            doc.getFacets().forEach(facet -> {
                DocTypeWithSchema.Schema schemaSettingByFacet = getSchemaSetting(schemaSettings, "Facet_" + facet, schema);
                if (schemaSettingByFacet != null) {
                    addSchemaFields(doc, session, inIsolation, schemaSettingByFacet, schemaFields);
                }
            });
        });

        return schemaFields;
    }

    private void addSchemaFields(DocumentModel doc, CoreSession session, boolean inIsolation, DocTypeWithSchema.Schema schemaSetting, Set<DocTypeWithSchema.Schema.Field> schemaFields) {
        Set<DocTypeWithSchema.Schema.Field> fields = schemaSetting.getFields().stream()
                .filter(DocTypeWithSchema.Schema.Field::isActive)
                .filter(field -> (field.isInIsolation() == inIsolation))
                .map(schemaField -> {
                    if (StringUtils.isNotEmpty(schemaField.getDirectoryName())) {
                        Map<String, String> dictionaryValues = getDictionaryValues(session, doc, schemaField.getDirectoryName());
                        schemaField.setDirectoryValues(dictionaryValues);
                    }
                    return schemaField;
                })
                .collect(Collectors.toSet());
        schemaFields.addAll(fields);
    }

    private DocTypeWithSchema.Schema getSchemaSetting(Map<String, DocTypeWithSchema.Schema> schemaSettings, String docType, String schemaName) {
        String docTypeSchemaKey = docType + "_" + schemaName;
        if (schemaSettings.containsKey(docTypeSchemaKey)) {
            log.debug("Schema settings by docTye + schemaName : " + docTypeSchemaKey);
            return schemaSettings.get(docTypeSchemaKey);
        }
        if (schemaSettings.containsKey(schemaName)) {
            log.debug("Schema settings by schemaName : " + schemaName);
            return schemaSettings.get(schemaName);
        }
        return null;
    }

    private Map<String, Object> addSchemasAndDictionariesToParams(Map<String, Object> schemaFieldsMap, Map<String, Object> parameters) {
        try {
            parameters.put("schema", objectMapper.writeValueAsString(schemaFieldsMap));
        } catch (Exception e) {
            log.error("Failed to add schema as parameters", e);
        }
        return parameters;
    }

    private Map<String, Object> getSchemaFieldsMap(Set<DocTypeWithSchema.Schema.Field> schemasFields) {
        Map<String, Object> schemaFieldsMap = new HashMap<>();
        schemasFields.forEach(activeField -> {
            Map<String, Object> fieldValuesMap = new HashMap<>();
            fieldValuesMap.put("description", activeField.getDescription());
            fieldValuesMap.put("type", activeField.isMultiValue() ? "array" : "string");
            if (MapUtils.isNotEmpty(activeField.getDirectoryValues())) {
                fieldValuesMap.put("dictionaryValues", activeField.getDirectoryValues());
            }
            schemaFieldsMap.put(activeField.getXpath(), fieldValuesMap);
        });
        return schemaFieldsMap;
    }

    private String getResultSchema(Set<DocTypeWithSchema.Schema.Field> schemasFields) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> resultProperties = new HashMap<>();
        List<String> requiredFields = new ArrayList<>();

        for (DocTypeWithSchema.Schema.Field field : schemasFields) {
            Map<String, Object> fieldSchema = new HashMap<>();
            Map<String, String> directoryValues = field.getDirectoryValues();
            if (field.isMultiValue()) {
                fieldSchema.put("type", "array");
                Map<String, Object> itemsMap = new HashMap<>();
                itemsMap.put("type", "string");
                if (MapUtils.isNotEmpty(directoryValues)) {
                    Set<String> directoryKeys = new HashSet<>(directoryValues.keySet());
                    directoryKeys.add(null);
                    itemsMap.put("enum", directoryKeys);
                    fieldSchema.put("nullable", true);
                }
                fieldSchema.put("items", itemsMap);
            } else {
                fieldSchema.put("type", "string");
                if (MapUtils.isNotEmpty(directoryValues)) {
                    Set<String> directoryKeys = new HashSet<>(directoryValues.keySet());
                    directoryKeys.add(null);
                    fieldSchema.put("enum", directoryKeys);
                    fieldSchema.put("nullable", true);
                }
            }
            fieldSchema.put("description", field.getDescription());
            resultProperties.put(field.getXpath(), fieldSchema);
            requiredFields.add(field.getXpath());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", "object");
        result.put("properties", resultProperties);
        result.put("required", requiredFields);

        Map<String, Object> properties = new HashMap<>();
        properties.put("result", result);

        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("result"));

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (Exception e) {
            throw new NuxeoException(e);
        }
    }

    private void addTextEmbeddings(DocumentModel doc) {
        try {
            String storeId = (String) doc.getPropertyValue("interactionresults:storeId");
            double[] documentEmbeddings = Framework.getService(APIService.class).getDocumentEmbeddings(storeId, "text");
            doc.setPropertyValue(SCHEMA_EMBEDDINGS_TEXT, documentEmbeddings);
        } catch (Exception e) {
            log.error("Failed to save textEmbeddings", e);
        }
    }

    private void addImageEmbeddings(DocumentModel doc) {
        try {
            String storeId = (String) doc.getPropertyValue("interactionresults:storeId");
            double[] documentEmbeddings = Framework.getService(APIService.class).getDocumentEmbeddings(storeId, "image");
            doc.setPropertyValue(SCHEMA_EMBEDDINGS_IMAGE, documentEmbeddings);
        } catch (Exception e) {
            log.error("Failed to save imageEmbeddings", e);
        }
    }

    private void addPropertiesEmbeddings(DocumentModel doc) {
        try {
            String storeId = (String) doc.getPropertyValue("interactionresults:storeId");
            double[] documentEmbeddings = Framework.getService(APIService.class).getDocumentEmbeddings(storeId, "properties");
            doc.setPropertyValue(SCHEMA_EMBEDDINGS_PROPERTIES, documentEmbeddings);
        } catch (Exception e) {
            log.error("Failed to save propertiesEmbeddings", e);
        }
    }

    private boolean isNullOrEmpty(Serializable propertyValue) {
        if (propertyValue instanceof String) {
            return StringUtils.isEmpty((CharSequence) propertyValue);
        }
        if (propertyValue instanceof List) {
            return CollectionUtils.isEmpty((Collection) propertyValue);
        }
        return Objects.isNull(propertyValue);
    }

    private <K, V> List<Map<K, V>> partitionMap(Map<K, V> originalMap, int bucketSize) {
        List<Map<K, V>> buckets = new ArrayList<>();
        Map<K, V> currentBucket = new LinkedHashMap<>();
        int count = 0;

        for (Map.Entry<K, V> entry : originalMap.entrySet()) {
            currentBucket.put(entry.getKey(), entry.getValue());
            count++;

            if (count % bucketSize == 0) {
                buckets.add(currentBucket);
                currentBucket = new LinkedHashMap<>();
            }
        }

        if (!currentBucket.isEmpty()) {
            buckets.add(currentBucket);
        }

        return buckets;
    }

    private int getBucketSize(Settings savedSettings) {
        String bucketSizeStr = (savedSettings.getAiSettings() != null)
                ? (String) savedSettings.getAiSettings().getOrDefault("directoryBucketSize", DEFAULT_DIRECTORY_BUCKET_SIZE)
                : DEFAULT_DIRECTORY_BUCKET_SIZE;
        if (StringUtils.isEmpty(bucketSizeStr)) {
            return Integer.parseInt(DEFAULT_DIRECTORY_BUCKET_SIZE);
        }
        return Integer.parseInt(bucketSizeStr);
    }

    private String getCallbackNotifyUrl(Settings savedSettings, DocumentModel doc, String interactionId, boolean saveMetadataModeOverride) {
        String settingsCallbackNotifyUrl = (savedSettings.getAiSettings() != null)
                ? (String) savedSettings.getAiSettings().get(CALLBACK_NOTIFY_URL)
                : null;
        if (StringUtils.isEmpty(settingsCallbackNotifyUrl)) {
            throw new NuxeoException("AI Settings - callbackNotifyUrl is empty");
        }
        return settingsCallbackNotifyUrl + "/nuxeo/site/vertesia-callback?documentId=" + doc.getId() +
                "&interactionId=" + interactionId +
                "&metadataOverride=" + saveMetadataModeOverride +
                "&apiKey=" + VERTESIA_CALLBACK_API_KEY;
    }

}
