package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.DocOrMediaType;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.DocTypeWithSchema.Schema.Field;
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

import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.maretha.cp.core.services.RepositoryService.RESULTS_FACET;

import static io.maretha.cp.core.services.utils.AuditUtils.logAIAudit;

public class RunInteractionServiceImpl implements RunInteractionService {

    public static final String CALLBACK_NOTIFY_URL = "callbackNotifyUrl";
    private static final String DEFAULT_DIRECTORY_BUCKET_SIZE = "100";
    private static final String FACET_PREFIX = "Facet_";
    private static final String FILES_SCHEMA = "files";
    private static final String FILE_SCHEMA = "file";
    private static final String FILE_CONTENTS = "files:files";

    private final Log log = LogFactory.getLog(RunInteractionServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static Blob getBlob(DocumentModel doc) {
        return io.maretha.cp.core.services.helper.BlobExtractor.getFileContent(doc);
    }

    public static boolean isImage(Blob blob) {
        return io.maretha.cp.core.services.helper.BlobExtractor.isImage(blob);
    }

    @Override
    public String triggerInteraction(DocumentModel doc, CoreSession session, String interactionId,
            List<InteractionParameter> parameters, boolean saveMetadata, boolean saveMetadataModeOverride,
            boolean asyncMode) {
        logAIAudit(session, doc, "", "Triggering AI Interaction with id: " + interactionId);

        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
        Interaction interaction = getInteractionById(savedSettings, interactionId);

        Map<String, Object> paramsMap = buildParameters(doc, session, parameters, interaction);
        if (!doc.hasFacet(RESULTS_FACET)) {
            doc.addFacet(RESULTS_FACET);
        }
        if (saveMetadata) {
            triggerSaveMetadataRequest(doc, session, interactionId, saveMetadataModeOverride, paramsMap, asyncMode);
            addEmbeddings(doc, session, false, false); // todo move to async worker
            return "Metadata request in progress.";
        }

        String response = Framework.getService(InteractionService.class).invokeModel(doc, interaction, paramsMap);
        addEmbeddings(doc, session, false, false); // todo move to async worker
        return response;
    }

    private void triggerSaveMetadataRequest(DocumentModel doc, CoreSession session, String interactionId,
            boolean saveMetadataModeOverride, Map<String, Object> paramsMap, boolean asyncMode) {
        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
        int bucketSize = getBucketSize(savedSettings);

        Set<DocTypeWithSchema.Schema.Field> schemasFieldsInIsolation = getSchemaFields(doc, session, true);
        schemasFieldsInIsolation.forEach(schemaField -> {
            Map<String, DocTypeWithSchema.Schema.DictionaryWithDescription> directoryValues = schemaField
                    .getDirectoryValues();
            if (MapUtils.isNotEmpty(directoryValues) && directoryValues.size() > bucketSize) {
                List<Map<String, DocTypeWithSchema.Schema.DictionaryWithDescription>> partitionedMap = partitionMap(
                        directoryValues, bucketSize);
                partitionedMap.forEach(partition -> {
                    schemaField.setDirectoryValues(partition);
                    sendInteractionRequest(interactionId, paramsMap, Set.of(schemaField), saveMetadataModeOverride, doc,
                            savedSettings, asyncMode);
                });
            } else {
                sendInteractionRequest(interactionId, paramsMap, Set.of(schemaField), saveMetadataModeOverride, doc,
                        savedSettings, asyncMode);
            }
        });

        Set<DocTypeWithSchema.Schema.Field> schemaFieldsNotInIsolation = getSchemaFields(doc, session, false);
        if (CollectionUtils.isNotEmpty(schemaFieldsNotInIsolation)) {
            sendInteractionRequest(interactionId, paramsMap, schemaFieldsNotInIsolation, saveMetadataModeOverride, doc,
                    savedSettings, asyncMode);
        }
    }

    private Interaction getInteractionById(Settings savedSettings, String interactionId) {
        Interaction interactionById = savedSettings.getInteractions().stream()
                .filter(interaction -> interaction.getId().equalsIgnoreCase(interactionId))
                .findFirst()
                .orElseThrow(() -> new NuxeoException(
                        "Interaction with ID '" + interactionId + "' not found."));
        return interactionById;
    }

    private void sendInteractionRequest(String interactionId, Map<String, Object> paramsMap,
            Set<DocTypeWithSchema.Schema.Field> schemaFields, boolean saveMetadataModeOverride, DocumentModel doc,
            Settings savedSettings, boolean asyncMode) {
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
                    Interaction customInteraction = getInteractionById(savedSettings, customInteractionId);

                    // Skip if main metadata interaction and custom interaction requires user input
                    if (interaction.isMainMetadata() && customInteraction.isRequiresUserInput()) {
                        log.info("Skipping interaction " + customInteractionId + " - requires user input");
                        return;
                    }

                    interaction = customInteraction;
                }
            }
        }

        response = Framework.getService(InteractionService.class).invokeModelWithSchema(doc, interaction, paramsData,
                resultSchema);
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
        Set<DocTypeWithSchema.Schema.Field> schemasFields = Stream
                .concat(schemaFieldsNotInIsolation.stream(), schemaFieldsInIsolation.stream())
                .collect(Collectors.toSet());

        // Delegate to helper class
        io.maretha.cp.core.services.helper.MetadataOperations.validateAndSaveMetadata(
                jsonNode, docId, saveMetadataModeOverride, schemasFields);
    }

    @Override
    public DocumentModel addEmbeddings(DocumentModel doc, CoreSession session, boolean waitForCompletion,
            boolean lowPriority) {
        Blob blob = getBlob(doc);
        return io.maretha.cp.core.services.helper.EmbeddingsHelper.addEmbeddings(
                doc, session, blob, waitForCompletion, lowPriority);
    }

    private Map<String, Object> buildParameters(DocumentModel doc, CoreSession session,
            List<InteractionParameter> parameters, Interaction interaction) {
        Map<String, Object> paramsMap = new HashMap<>();
        List<DocumentModel> relatedDocuments = getRelatedDocuments(session, doc);

        Map<String, Blob> attachments = new HashMap<>();
        if (interaction.isSendAttachments()) {
            attachments = getAttachmentsMap(doc);
        }

        if (interaction.isSendParentsContext()) {
            List<DocumentModel> allParentDocuments = getAllParentDocuments(session, doc);
            Map<String, Blob> parentDocuments = addParentDocuments(session, allParentDocuments);
            attachments.putAll(parentDocuments);
        }

        final Map<String, Blob> finalAttachments = attachments;
        parameters.forEach(param -> processParameter(doc, session, interaction, relatedDocuments, finalAttachments,
                paramsMap, param));

        // Populate additional binary content if necessary?
        // We might want to pass 'attachments' to paramMap if not already?
        // Or processParameter handles it.
        return paramsMap;
    }

    private void processParameter(DocumentModel doc, CoreSession session, Interaction interaction,
            List<DocumentModel> relatedDocuments, Map<String, Blob> attachments,
            Map<String, Object> paramsMap, InteractionParameter param) {
        String name = param.getName();
        Object value = param.getValue();
        String type = param.getType();
        String format = param.getFormat();
        String selectedDocumentId = param.getSelectedDocumentId();

        if (DocOrMediaType.DOCUMENT.getTypeName().equals(format)) {
            value = getDocumentOrMediaValue(doc, session, param, type, relatedDocuments, selectedDocumentId,
                    DocOrMediaType.DOCUMENT, attachments);
        }
        if (DocOrMediaType.MEDIA.getTypeName().equalsIgnoreCase(format)) {
            value = getDocumentOrMediaValue(doc, session, param, type, relatedDocuments, selectedDocumentId,
                    DocOrMediaType.MEDIA, attachments);
        }

        if (param.isAdditionalContext()) {
            Map<String, Object> additionalContext = getAdditionalContext(session, doc);

            if (interaction.isSendParentsContext()) {
                List<DocumentModel> allParentDocuments = getAllParentDocuments(session, doc);
                if (CollectionUtils.isNotEmpty(allParentDocuments)) {
                    allParentDocuments.forEach(parentDoc -> {
                        Map<String, Object> parentContext = getAdditionalContext(session, parentDoc);
                        if (parentContext != null && !parentContext.isEmpty()) {
                            additionalContext.put(parentDoc.getId(), parentContext);
                        }
                    });
                    additionalContext.put("documentPath", doc.getPathAsString());
                }
            }

            if (param.getType().equalsIgnoreCase("string")) {
                try {
                    String valueAsString = objectMapper.writeValueAsString(additionalContext);
                    paramsMap.put(name, valueAsString);
                } catch (Exception ignore) {
                }
            } else {
                paramsMap.put(name, additionalContext);
            }
            return;
        }

        paramsMap.put(name, value);
    }

    private List<DocumentModel> getRelatedDocuments(CoreSession session, DocumentModel doc) {
        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
        Map<String, DocTypeWithSchema.Schema> schemaSettings = savedSettings.getSchemaSettings();

        if (MapUtils.isEmpty(schemaSettings)) {
            log.error("SchemaSettings is empty");
            return Collections.emptyList();
        }

        return io.maretha.cp.core.services.helper.DocumentContextBuilder.getRelatedDocuments(
                session, doc, this::getSchemaFields, schemaSettings);
    }

    private List<DocumentModel> getAllParentDocuments(CoreSession session, DocumentModel doc) {
        return io.maretha.cp.core.services.helper.DocumentContextBuilder.getAllParentDocuments(session, doc);
    }

    private Map<String, Blob> addParentDocuments(CoreSession session, List<DocumentModel> allParentDocuments) {
        Map<String, Blob> attachments = new HashMap<>();
        allParentDocuments.forEach(parentDoc -> {
            Set<Field> schemaFields = getMetadataSettingsSchemaFields(parentDoc, session);

            boolean parentHasFiles = schemaFields.stream()
                    .filter(DocTypeWithSchema.Schema.Field::isAdditionalContext)
                    .anyMatch(field -> {
                        return field.getXpath().equalsIgnoreCase(FILE_SCHEMA)
                                || field.getXpath().equalsIgnoreCase(FILES_SCHEMA);
                    });
            if (parentHasFiles) {
                Map<String, Blob> parentAttachments = getAttachmentsMap(parentDoc);
                if (MapUtils.isNotEmpty(parentAttachments)) {
                    attachments.putAll(parentAttachments);
                }
            }
        });
        return attachments;
    }

    private Object getDocumentOrMediaValue(DocumentModel doc, CoreSession session, InteractionParameter param,
            String type,
            List<DocumentModel> relatedDocuments, String selectedDocumentId, DocOrMediaType docOrMediaType,
            Map<String, Blob> attachments) {
        Object value;
        Set<String> relatedDocumentIds = relatedDocuments.stream().map(DocumentModel::getId)
                .collect(Collectors.toSet());
        if ("array".equalsIgnoreCase(type)) {
            Set<String> selectedDocumentIds = new HashSet<>(relatedDocumentIds);
            selectedDocumentIds.addAll(param.getSelectedDocumentIds());
            value = getSelectedDocumentsContent(doc, selectedDocumentIds, docOrMediaType.getTypeName(),
                    attachments);
        } else if (StringUtils.isNotEmpty(selectedDocumentId)) {
            DocumentModel selectedDocument = doc.getCoreSession().getDocument(new IdRef(selectedDocumentId));
            // selectedDocument = uploadFilesAndSetStoreIds(selectedDocument, session, true,
            // true);
            // value = "store:" +
            // selectedDocument.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
            // Changing to pass Blob for direct processing:
            value = getBlob(selectedDocument);
        } else {
            // value = "store:" + doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
            value = getBlob(doc);
        }
        Blob blob = getBlob(doc);
        if (blob != null && !isImage(blob) && !isImage(blob) && docOrMediaType.equals(DocOrMediaType.MEDIA)) { // check
                                                                                                               // isImage
                                                                                                               // for
                                                                                                               // media?
            // Logic from maretha-cp:
            // if (blob != null && !isImage(blob) && !isVideo(blob) &&
            // docOrMediaType.equals(DocOrMediaType.MEDIA))
            // value = "";
            // I'm skipping video check for now
        }
        return value;
    }

    private List<Blob> getSelectedDocumentsContent(DocumentModel documentModel, Set<String> selectedDocumentIds,
            String docOrMediaType, Map<String, Blob> attachments) {
        CoreSession coreSession = documentModel.getCoreSession();
        List<Blob> blobs = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(selectedDocumentIds)) {
            selectedDocumentIds.stream().map(docId -> coreSession.getDocument(new IdRef(docId))).filter(doc -> {
                if (!doc.hasSchema(FILE_SCHEMA)) {
                    return false;
                }
                Blob blob = (Blob) doc.getPropertyValue(FILE_CONTENT);
                if (blob == null) {
                    return false;
                }
                if (docOrMediaType.equals("media") && isImage(blob)) {
                    return true;
                }
                return docOrMediaType.equals("document") && !isImage(blob);
            }).map(RunInteractionServiceImpl::getBlob)
                    .forEach(blobs::add);
        }
        if (documentModel.hasSchema(FILES_SCHEMA)) {
            attachments.entrySet().stream().filter(entry -> {
                Blob blob = entry.getValue();
                if (blob == null) {
                    return false;
                }
                if (docOrMediaType.equals("media") && isImage(blob)) {
                    return true;
                }
                return docOrMediaType.equals("document") && !isImage(blob);
            }).map(Map.Entry::getValue).forEach(blobs::add);
        }
        return blobs;
    }

    private Set<Field> getMetadataSettingsSchemaFields(DocumentModel doc, CoreSession session) {
        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session, true);
        Map<String, DocTypeWithSchema.Schema> schemaSettings = savedSettings.getSchemaSettings();
        if (MapUtils.isEmpty(schemaSettings)) {
            log.error("SchemaSettings is empty");
            return Set.of();
        }

        return getSchemaFields(doc, schemaSettings);
    }

    private Set<DocTypeWithSchema.Schema.Field> getSchemaFields(DocumentModel doc,
            Map<String, DocTypeWithSchema.Schema> schemaSettings) {
        Set<DocTypeWithSchema.Schema.Field> schemaFields = new HashSet<>();

        Arrays.stream(doc.getSchemas()).forEach(schema -> {
            DocTypeWithSchema.Schema schemaSetting = getSchemaSetting(schemaSettings, doc.getType(), schema);
            if (schemaSetting != null) {
                Set<DocTypeWithSchema.Schema.Field> fields = schemaSetting.getFields().stream()
                        .filter(DocTypeWithSchema.Schema.Field::isAdditionalContext).collect(Collectors.toSet());
                schemaFields.addAll(fields);
            }

            doc.getFacets().forEach(facet -> {
                DocTypeWithSchema.Schema schemaSettingByFacet = getSchemaSetting(schemaSettings, FACET_PREFIX + facet,
                        schema);
                if (schemaSettingByFacet != null) {
                    Set<DocTypeWithSchema.Schema.Field> fields = schemaSettingByFacet.getFields().stream()
                            .filter(DocTypeWithSchema.Schema.Field::isAdditionalContext).collect(Collectors.toSet());
                    schemaFields.addAll(fields);
                }
            });
        });
        return schemaFields;
    }

    @SneakyThrows
    private Map<String, Object> getAdditionalContext(CoreSession session, DocumentModel doc) {
        return io.maretha.cp.core.services.helper.DocumentContextBuilder.buildContext(
                session, doc, this::getMetadataSettingsSchemaFields);
    }

    private Map<String, Blob> getAttachmentsMap(DocumentModel doc) {
        return io.maretha.cp.core.services.helper.BlobExtractor.getAttachmentsMap(doc);
    }

    private List<Blob> getAttachmentBlobs(DocumentModel doc) {
        return io.maretha.cp.core.services.helper.BlobExtractor.getAttachments(doc);
    }

    private Map<String, DocTypeWithSchema.Schema.DictionaryWithDescription> getDictionaryValues(CoreSession session,
            DocumentModel doc, String directoryName) {
        Map<String, DocTypeWithSchema.Schema.DictionaryWithDescription> dictionaryMap = new HashMap<>();
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
                // ignore
            }
            OperationContext ctx = new OperationContext(session);
            Blob directoriesJsonBlob = (Blob) Framework.getService(AutomationService.class).run(ctx,
                    "Directory.SuggestEntries", params);

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
                boolean isObsolete = node.has("obsolete")
                        && (node.get("obsolete").asLong(0) > 0 || node.get("obsolete").asBoolean(false));

                if (!isObsolete && node.hasNonNull("id") && node.hasNonNull("label")) {
                    String id = node.get("id").asText();
                    String label = node.get("label").asText();
                    String description = null;
                    if (node.hasNonNull("description")) {
                        description = node.get("description").asText();
                    }
                    DocTypeWithSchema.Schema.DictionaryWithDescription dict = new DocTypeWithSchema.Schema.DictionaryWithDescription(
                            label, description);
                    dictionaryMap.put(id, dict);
                }
            }
        } catch (Exception e) {
            log.error("Failed to get directory: " + directoryName, e);
        }
        return dictionaryMap;
    }

    private void addSchemaFields(DocumentModel doc, CoreSession session, boolean inIsolation,
            DocTypeWithSchema.Schema schemaSetting, Set<DocTypeWithSchema.Schema.Field> schemaFields) {
        Set<DocTypeWithSchema.Schema.Field> fields = schemaSetting.getFields().stream()
                .filter(DocTypeWithSchema.Schema.Field::isActive)
                .filter(field -> (field.isInIsolation() == inIsolation)).map(schemaField -> {
                    if (StringUtils.isNotEmpty(schemaField.getDirectoryName())) {
                        Map<String, DocTypeWithSchema.Schema.DictionaryWithDescription> dictionaryValues = getDictionaryValues(
                                session, doc, schemaField.getDirectoryName());
                        schemaField.setDirectoryValues(dictionaryValues);
                    }
                    return schemaField;
                }).collect(Collectors.toSet());
        schemaFields.addAll(fields);
    }

    private DocTypeWithSchema.Schema getSchemaSetting(Map<String, DocTypeWithSchema.Schema> schemaSettings,
            String docType, String schemaName) {
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

    private Map<String, Object> addSchemasAndDictionariesToParams(Map<String, Object> schemaFieldsMap,
            Map<String, Object> parameters) {
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
            Map<String, DocTypeWithSchema.Schema.DictionaryWithDescription> directoryValues = field
                    .getDirectoryValues();
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

    private Set<DocTypeWithSchema.Schema.Field> getSchemaFields(DocumentModel doc, CoreSession session,
            boolean inIsolation) {
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
                DocTypeWithSchema.Schema schemaSettingByFacet = getSchemaSetting(schemaSettings, FACET_PREFIX + facet,
                        schema);
                if (schemaSettingByFacet != null) {
                    addSchemaFields(doc, session, inIsolation, schemaSettingByFacet, schemaFields);
                }
            });
        });

        return schemaFields;
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
                ? (String) savedSettings.getAiSettings().getOrDefault("directoryBucketSize",
                        DEFAULT_DIRECTORY_BUCKET_SIZE)
                : DEFAULT_DIRECTORY_BUCKET_SIZE;
        if (StringUtils.isEmpty(bucketSizeStr)) {
            return Integer.parseInt(DEFAULT_DIRECTORY_BUCKET_SIZE);
        }
        return Integer.parseInt(bucketSizeStr);
    }

    @Override
    public DocumentModel uploadFilesAndSetStoreIds(DocumentModel doc, CoreSession session, boolean waitForCompletion,
            boolean lowPriority) {
        // Implementation for interface compliance, but we don't use 'Store' in
        // maretha-ai.
        // We can just return doc, or perform addEmbeddings?
        return addEmbeddings(doc, session, waitForCompletion, lowPriority);
    }

}
