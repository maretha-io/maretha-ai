package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.models.Settings;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.validation.DocumentValidationService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.picture.api.adapters.PictureBlobHolder;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.maretha.cp.core.services.RepositoryService.RESULTS_FACET;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORED_DATE_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORE_DIGEST_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORE_ID_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_EMBEDDINGS_IMAGE;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_EMBEDDINGS_TEXT;

public class RunInteractionServiceImpl implements RunInteractionService {

    public static final String MIME_TYPES = "vertesia.mimeTypes";
    private final Log log = LogFactory.getLog(RunInteractionServiceImpl.class);
    private final APIService apiService = Framework.getService(APIService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static String getSupportedMimeTypes() {
        return Framework.getService(RepositoryService.class).getSettingsProperty(MIME_TYPES, "text/plain,application/pdf");
    }

    @Override
    public String triggerInteraction(DocumentModel doc, CoreSession session, String interactionId, List<InteractionParameter> parameters, boolean saveMetadata, boolean saveMetadataModeOverride) {
        List<DocTypeWithSchema.Schema.Field> schemasFields = List.of();
        if (saveMetadata) {
            schemasFields = addSchemasFieldsAndVocabs(doc, session, parameters);
        }
        Map<String, Object> paramsMap = buildParameters(doc, session, parameters);
        if (saveMetadata) {
            String response = apiService.getInteractionResult(interactionId, paramsMap, false);
            return validateAndSaveMetadata(response, doc, session, saveMetadataModeOverride, schemasFields);
        }
        return apiService.getInteractionResult(interactionId, paramsMap, true);
    }

    private String validateAndSaveMetadata(String response, DocumentModel doc, CoreSession session, boolean saveMetadataModeOverride, List<DocTypeWithSchema.Schema.Field> schemasFields) {
        try {
            DocumentValidationService validationService = Framework.getService(DocumentValidationService.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode resultNode;
            try {
                resultNode = jsonNode.get("result").get("result");
            } catch (Exception e) {
                throw new NuxeoException("Missing json result from API Response", e);
            }
            resultNode.fields().forEachRemaining(field -> {
                String key = field.getKey();
                JsonNode valueNode = field.getValue();
                String value = valueNode.asText();
                Optional<DocTypeWithSchema.Schema.Field> optionalField = schemasFields.stream()
                        .filter(schemaField -> schemaField.getXpath().equalsIgnoreCase(key)).findFirst();
                if (optionalField.isEmpty()) {
                    log.warn("Metadata save skip on not declared xpath: " + key);
                    return;
                }
                if (StringUtils.isEmpty(value)) {
                    log.warn("Metadata save skip on empty value for xpath: " + key);
                    return;
                }
                try {
                    Serializable propertyValue = doc.getPropertyValue(key);
                    if (Objects.isNull(propertyValue) || saveMetadataModeOverride) {
                        if (optionalField.get().isMultiValue() && valueNode.isArray()) {
                            List<String> values = new ArrayList<>();
                            valueNode.forEach(element -> values.add(element.asText()));
                            saveMetadataField(doc, validationService, key, values.toArray(new String[0]));
                        } else {
                            saveMetadataField(doc, validationService, key, value);
                        }
                    }
                } catch (Exception propertyException) {
                    log.error("Failed to set metadata for " + key + ":" + value);
                }
            });
            session.saveDocument(doc);
            return objectMapper.writeValueAsString(resultNode);
        } catch (Exception e) {
            log.error("Could not save document metadata", e);
            throw new RuntimeException("Failed to save document metadata: " + e.getMessage());
        }
    }

    private void saveMetadataField(DocumentModel doc, DocumentValidationService validationService, String key, Serializable value) {
        if (!validationService.validate(key, value).hasError()) {
            log.debug(String.format("Saving metadata key: %s value: %s", key, value));
            doc.setPropertyValue(key, value);
        } else {
            log.error(String.format("Failed to save metadata key: %s value: %s", key, value));
        }
    }

    public DocumentModel uploadFileAndSetStoreId(DocumentModel doc, CoreSession session, boolean waitForCompletion) {
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
            Settings settings = Framework.getService(RepositoryService.class).getSavedSettings(session);
            if (settings.isAwsS3AccessEnabled()) {
                String s3Path = getS3Path(doc);
                storeId = apiService.uploadS3FileToStore(blob, s3Path);
            } else {
                storeId = apiService.uploadFileToStore(blob);
            }
            doc.setPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH, storeId);
            doc.setPropertyValue(RESULTS_STORE_DIGEST_SCHEMA_PATH, blob.getDigest());
            doc.setPropertyValue(RESULTS_STORED_DATE_SCHEMA_PATH, new GregorianCalendar());

            doc = session.saveDocument(doc);
        }

        try {
            //Retry for uploaded Vertesia document to have status completed
            if (waitForCompletion) {
                apiService.checkProcessingStatusCompleted(storeId, true);
            }

            if (isImage(blob) && CollectionUtils.isEmpty((Collection) doc.getPropertyValue(SCHEMA_EMBEDDINGS_IMAGE))) {
                addImageEmbeddings(doc);
                doc = session.saveDocument(doc);
            }

            if (CollectionUtils.isEmpty((Collection) doc.getPropertyValue(SCHEMA_EMBEDDINGS_TEXT))) {
                addTextEmbeddings(doc);
                doc = session.saveDocument(doc);
            }
        } catch (Exception e) {
            log.error("Failed to save document: " + doc.getId(), e);
        }

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
        return apiService.checkProcessingStatusCompleted(storeId, false);
    }

    private Blob getBlob(DocumentModel doc) {
        if (doc.hasFacet("Picture")) {
            PictureBlobHolder pictureBlobHolder = new PictureBlobHolder(doc, "picture:views/3/content"); //FullHD
            Blob blob = pictureBlobHolder.getBlob();
            if (blob != null && blob.getLength() > 0 && !blob.getFilename().contains("empty_picture"))
                return blob;
        }
        return (Blob) doc.getPropertyValue(FILE_CONTENT);
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

            boolean isDocumentOrMediaFormat = "document".equalsIgnoreCase(format) || "media".equalsIgnoreCase(format);
            if (isDocumentOrMediaFormat) {
                if ("array".equalsIgnoreCase(type)) {
                    List<String> selectedDocumentsStoreIds = getSelectedDocumentsStoreIds(doc.getCoreSession(), selectedDocumentIds);
                    value = selectedDocumentsStoreIds;
                    if (CollectionUtils.isEmpty(selectedDocumentsStoreIds))
                        value = List.of("store:" + doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH));
                } else if (StringUtils.isNotEmpty(selectedDocumentId)) {
                    DocumentModel selectedDocument = doc.getCoreSession().getDocument(new IdRef(selectedDocumentId));
                    selectedDocument = uploadFileAndSetStoreId(selectedDocument, session, true);
                    value = "store:" + selectedDocument.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
                } else {
                    value = "store:" + doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
                }

                //If not image type leave media empty
                Blob blob = getBlob(doc);
                if ("media".equalsIgnoreCase(format) && !isImage(blob)) {
                    value = null;
                }
            }
            paramsMap.put(name, value);
        });
        return paramsMap;
    }

    private List<String> getSelectedDocumentsStoreIds(CoreSession coreSession, List<String> selectedDocumentIds) {
        if (CollectionUtils.isNotEmpty(selectedDocumentIds)) {
            return selectedDocumentIds
                    .stream()
                    .map(docId -> coreSession.getDocument(new IdRef(docId)))
                    .map(doc -> uploadFileAndSetStoreId(doc, coreSession, false))
                    .map(docWithStoreId -> "store:" + docWithStoreId.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH))
                    .collect(Collectors.toList());
        }
        return null;
    }

    //source: "s3://composable-bs-test/my-photo.jpg#eu-north-1",
    private String getS3Path(DocumentModel doc) {
        Property blobProperty = doc.getProperty(FILE_CONTENT);
        ManagedBlob blob = (ManagedBlob) blobProperty.getValue();
        CloudStorageFileShare cloudStorageFileShare = Framework.getService(CloudStorageFileShare.class);
        return cloudStorageFileShare.getS3FilePath(blob);
    }

    private boolean isImage(Blob blob) {
        String mimeType = blob.getMimeType();
        return mimeType != null && mimeType.startsWith("image/");
    }

    private Map<String, String> getDictionaryValues(String directoryName) {
        Map<String, String> dictionaryMap = new HashMap<>();
        try {
            DirectoryService directoryService = Framework.getService(DirectoryService.class);
            Directory directory = directoryService.getDirectory(directoryName);
            if (directory == null) {
                log.warn(String.format("Directory '{%s}' not found, returning empty dictionary.", directoryName));
                return dictionaryMap; // Return empty map safely
            }
            try (Session session = Framework.getService(DirectoryService.class).open(directoryName)) {
                DocumentModelList entries = session.query(Map.of());
                entries.forEach(entry -> {
                    String id = entry.getId();
                    try {
                        String label = (String) (entry.getProperty("label")).getValue();
                        dictionaryMap.put(id, label);
                    } catch (Exception e) {
                        log.error("Label not found for directory: " + id);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Failed to get directory: " + directoryName, e);
        }
        return dictionaryMap;
    }

    private List<DocTypeWithSchema.Schema.Field> addSchemasFieldsAndVocabs(DocumentModel doc, CoreSession session, List<InteractionParameter> parameters) {
        Settings savedSettings = Framework.getService(RepositoryService.class).getSavedSettings(session);
        Map<String, DocTypeWithSchema.Schema> schemaSettings = savedSettings.getSchemaSettings();
        Map<String, Object> dictionariesMap = new HashMap<>();
        List<DocTypeWithSchema.Schema.Field> schemasFields = new ArrayList<>();

        Arrays.stream(doc.getSchemas()).forEach(schema -> {
            if (MapUtils.isNotEmpty(schemaSettings) && schemaSettings.containsKey(schema)) {
                DocTypeWithSchema.Schema schemaSetting = schemaSettings.get(schema);
                schemaSetting.getFields().stream().filter(DocTypeWithSchema.Schema.Field::isActive).forEach(activeField -> {
                    schemasFields.add(activeField);
                    if (StringUtils.isNotEmpty(activeField.getDirectoryName())) {
                        Map<String, String> dictionaryValues = getDictionaryValues(activeField.getDirectoryName());
                        dictionariesMap.put(activeField.getDirectoryName(), dictionaryValues);
                    }
                });
            }
        });

        parameters.forEach(param -> {
            if (param.getName().equals("schema")) {
                try {
                    param.setValue(objectMapper.writeValueAsString(schemasFields));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }

            if (param.getName().equals("dictionaries")) {
                try {
                    param.setValue(objectMapper.writeValueAsString(dictionariesMap));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return schemasFields;
    }

    private void addTextEmbeddings(DocumentModel doc) {
        String storeId = (String) doc.getPropertyValue("interactionresults:storeId");
        double[] documentEmbeddings = apiService.getDocumentEmbeddings(storeId, "text");
        doc.setPropertyValue(SCHEMA_EMBEDDINGS_TEXT, documentEmbeddings);
    }

    private void addImageEmbeddings(DocumentModel doc) {
        String storeId = (String) doc.getPropertyValue("interactionresults:storeId");
        double[] documentEmbeddings = apiService.getDocumentEmbeddings(storeId, "image");
        doc.setPropertyValue(SCHEMA_EMBEDDINGS_IMAGE, documentEmbeddings);
    }
}
