package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.models.InteractionResponse;
import io.maretha.cp.core.models.Settings;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.platform.relations.api.DocumentRelationManager;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.AWS_S3_ACCESS_ENABLED;
import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.ES_SETTINGS;
import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.SCHEMA_SETTINGS;
import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.VERTESIA_SETTINGS;

public class RepositoryServiceImpl implements RepositoryService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Settings getSavedSettings(CoreSession session) {
        Optional<DocumentModel> existingSettingsDocument = getExistingSettingsDocument(session);
        if (existingSettingsDocument.isPresent()) {
            DocumentModel documentModel = existingSettingsDocument.get();
            return documentModel.getAdapter(Settings.class);
        }
        return new Settings();
    }

    @Override
    public List<Interaction> getActiveInteractionsForDocumentType(CoreSession session, String documentType, boolean isFolder) {
        return getSavedSettings(session).getInteractions()
                .stream()
                .filter(cpInteraction -> cpInteraction.getActiveOnDocumentTypes().contains(documentType))
                .filter(Interaction::isActive)
                .filter(Interaction::isActiveOnActionMenu)
                .filter(interaction -> !interaction.isChat())
                .filter(interaction -> {
                    if (isFolder) return interaction.isActiveOnFolder();
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Interaction> getActiveChatInteractionsForDocumentType(CoreSession session, String documentType, boolean isFolder) {
        return getSavedSettings(session).getInteractions()
                .stream()
                .filter(cpInteraction -> cpInteraction.getActiveOnDocumentTypes().contains(documentType))
                .filter(Interaction::isActive)
                .filter(Interaction::isActiveOnActionMenu)
                .filter(Interaction::isChat)
                .filter(interaction -> {
                    if (isFolder) return interaction.isActiveOnFolder();
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Settings saveInteractionSettings(CoreSession session, List<ObjectNode> interactions, Map<String, Object> configuration) {
        DocumentModel cpSettingsDoc;
        Optional<DocumentModel> existingSettingsDocument = getExistingSettingsDocument(session);

        if (existingSettingsDocument.isPresent()) {
            cpSettingsDoc = existingSettingsDocument.get();
        } else {
            cpSettingsDoc = session.createDocumentModel("/", SETTINGS_DOCUMENT_NAME, SETTINGS_DOCUMENT_TYPE);
            cpSettingsDoc = session.createDocument(cpSettingsDoc);
        }

        if (CollectionUtils.isNotEmpty(interactions)) {
            List<HashMap<String, Object>> interactionsMap = interactions.stream()
                    .map(objNode -> convertToType(objNode, Interaction.class))
                    .map(this::convertToInteractionComplexType)
                    .collect(Collectors.toList());

            cpSettingsDoc.setPropertyValue(SETTINGS_SCHEMA_PATH, (Serializable) interactionsMap);
        }

        Boolean awsS3AccessEnabled = (Boolean) configuration.get(AWS_S3_ACCESS_ENABLED);
        cpSettingsDoc.setPropertyValue(SETTINGS_SCHEMA_AWS_S3_ACCESS, awsS3AccessEnabled);

        DocTypeWithSchema.Schema schemaSettings = (DocTypeWithSchema.Schema) configuration.get(SCHEMA_SETTINGS);
        if (!Objects.isNull(schemaSettings)) {
            updateSchemaSettings(schemaSettings, cpSettingsDoc);
        }

        @SuppressWarnings("unchecked")
        Map<String, String> esSettings = (Map<String, String>) configuration.get(ES_SETTINGS);
        if (!Objects.isNull(esSettings)) {
            updateEsSettings(esSettings, cpSettingsDoc);
        }

        @SuppressWarnings("unchecked")
        Map<String, String> vertesiaSettings = (Map<String, String>) configuration.get(VERTESIA_SETTINGS);
        if (!Objects.isNull(vertesiaSettings)) {
            updateVertesiaSettings(vertesiaSettings, cpSettingsDoc);
        }

        return session.saveDocument(cpSettingsDoc).getAdapter(Settings.class);
    }

    private void updateVertesiaSettings(Map<String, String> settings, DocumentModel cpSettingsDoc) {
        try {
            String jsonString = objectMapper.writeValueAsString(settings);
            cpSettingsDoc.setPropertyValue(SETTINGS_VERTESIA, jsonString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void updateSchemaSettings(DocTypeWithSchema.Schema schemaSettingsInput, DocumentModel cpSettingsDoc) {
        Map<String, DocTypeWithSchema.Schema> schemaSettingsMap = new HashMap<>();

        try {
            String propertyValue = (String) cpSettingsDoc.getPropertyValue(SETTINGS_SCHEMA_METADATA);
            if (!Objects.isNull(propertyValue)) {
                schemaSettingsMap = objectMapper.readValue(propertyValue, new TypeReference<>() {
                });
            }
            String schemaName = schemaSettingsInput.getName();
            schemaSettingsMap.put(schemaName, schemaSettingsInput);
            String jsonString = objectMapper.writeValueAsString(schemaSettingsMap);
            cpSettingsDoc.setPropertyValue(SETTINGS_SCHEMA_METADATA, jsonString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void updateEsSettings(Map<String, String> esSettings, DocumentModel cpSettingsDoc) {
        try {
            String jsonString = objectMapper.writeValueAsString(esSettings);
            cpSettingsDoc.setPropertyValue(SETTINGS_ELASTICSEARCH, jsonString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DocumentModel saveInteractionResponseAsMetadata(DocumentModel doc, CoreSession session, List<ObjectNode> interactions) {
        if (!doc.hasFacet(RESULTS_FACET)) {
            doc.addFacet(RESULTS_FACET);
        }
        List<HashMap<String, Object>> existingInteractions = (List<HashMap<String, Object>>) doc.getPropertyValue(RESULTS_SCHEMA_PATH);
        if (CollectionUtils.isEmpty(existingInteractions)) {
            existingInteractions = new ArrayList<>();
        }
        List<HashMap<String, Object>> interactionsMap = interactions.stream()
                .map(objNode -> convertToType(objNode, InteractionResponse.class))
                .map(this::convertToInteractionResponseComplexType)
                .collect(Collectors.toList());
        existingInteractions.addAll(interactionsMap);
        doc.setPropertyValue(RESULTS_SCHEMA_PATH, (Serializable) existingInteractions);
        return session.saveDocument(doc);
    }

    @Override
    public DocumentModel saveInteractionResponseAsDocument(DocumentModel doc, CoreSession session, List<ObjectNode> interactions) throws OperationException {
        String folderPath = "/" + RESULTS_DOCUMENT_NAME;
        boolean folderExists = session.exists(new PathRef(folderPath));
        if (!folderExists) {
            DocumentModel folderDocument = session.createDocumentModel("/", RESULTS_DOCUMENT_NAME, "Folder");
            folderDocument = session.createDocument(folderDocument);
            session.saveDocument(folderDocument);
        }
        DocumentModel interactionResponseDoc;
        String docTitle = RESULTS_DOCUMENT_NAME + '-' + doc.getTitle();
        interactionResponseDoc = session.createDocumentModel(folderPath, docTitle, RESULTS_DOCUMENT_TYPE);
        interactionResponseDoc.setPropertyValue("dc:title", docTitle);
        interactionResponseDoc = session.createDocument(interactionResponseDoc);
        List<HashMap<String, Object>> interactionsMap = interactions.stream()
                .map(objNode -> convertToType(objNode, InteractionResponse.class))
                .map(this::convertToInteractionResponseComplexType)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(interactionsMap)) {
            throw new NuxeoException("Empty list of interactions");
        }
        interactionResponseDoc.setPropertyValue(RESULTS_SCHEMA_PATH, (Serializable) interactionsMap);
        interactionResponseDoc = session.saveDocument(interactionResponseDoc);
        addDocumentRelation(doc, session, interactionResponseDoc);
        return interactionResponseDoc;
    }

    @Override
    public String getSettingsProperty(String name, String defaultValue) {
        String property = Framework.getProperty(name);
        if (StringUtils.isNotEmpty(property)) {
            return property;
        }
        //TODO Cache
        Settings settings = getSavedSettings(CoreInstance.getCoreSessionSystem(null));
        String nameWithoutPrefix = name.replace("vertesia.", "");
        String savedProperty = settings.getVertesiaSettings().get(nameWithoutPrefix);
        if (StringUtils.isNotEmpty(savedProperty)) {
            return savedProperty;
        }
        if (StringUtils.isNotEmpty(defaultValue)) {
            return defaultValue;
        }
        return "";
    }

    private void addDocumentRelation(DocumentModel sourceDoc, CoreSession session, DocumentModel targetDoc) {
        DocumentRelationManager documentRelationManager = Framework.getService(DocumentRelationManager.class);
        String predicate = "http://www.nuxeo.org/ecm/schemas/relation#isRelatedTo";
        boolean outgoing = true;
        documentRelationManager.addRelation(session, sourceDoc, targetDoc, predicate, !outgoing);
    }

    private HashMap<String, Object> convertToInteractionResponseComplexType(InteractionResponse interactionResponse) {
        HashMap<String, Object> interactionMap = new HashMap<>();
        addNonNullValue(interactionMap, SCHEMA_ID, interactionResponse.getId());
        addNonNullValue(interactionMap, SCHEMA_NAME, interactionResponse.getName());
        addNonNullValue(interactionMap, SCHEMA_DOCUMENT_ID, interactionResponse.getDocumentId());
        addNonNullValue(interactionMap, SCHEMA_INTERACTION_ID, interactionResponse.getInteractionId());
        addNonNullValue(interactionMap, SCHEMA_RESPONSE, interactionResponse.getResponse());
        addNonNullValue(interactionMap, SCHEMA_STATUS, interactionResponse.getStatus());
        addNonNullValue(interactionMap, SCHEMA_CREATED_BY, interactionResponse.getCreatedBy());
        addNonNullValue(interactionMap, SCHEMA_CREATED_AT, interactionResponse.getCreatedAt());
        addNonNullValue(interactionMap, SCHEMA_PARAMETERS, convertParameters(interactionResponse.getParameters()));
        return interactionMap;
    }

    private void addNonNullValue(HashMap<String, Object> map, String field, Object value) {
        if (!Objects.isNull(value)) {
            map.put(field, value);
        }
    }

    private <T> T convertToType(ObjectNode obj, Class<T> type) {
        try {
            return objectMapper.treeToValue(obj, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting ObjectNode to " + type.getSimpleName(), e);
        }
    }

    private Optional<DocumentModel> getExistingSettingsDocument(CoreSession session) {
        String query = String.format("SELECT * FROM %s WHERE ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:isTrashed = 0", SETTINGS_DOCUMENT_TYPE);
        DocumentModelList documents = session.query(query);
        return documents.stream().findFirst();
    }

    private HashMap<String, Object> convertToInteractionComplexType(Interaction interaction) {
        HashMap<String, Object> interactionMap = new HashMap<>();
        interactionMap.put(SCHEMA_ID, interaction.getId());
        interactionMap.put(SCHEMA_DESCRIPTION, interaction.getDescription());
        interactionMap.put(SCHEMA_NAME, interaction.getName());
        interactionMap.put(SCHEMA_STATUS, interaction.getStatus());
        interactionMap.put(SCHEMA_VERSION, interaction.getVersion());
        interactionMap.put(SCHEMA_ACTIVE_ON_DOCUMENTS, interaction.getActiveOnDocumentTypes());
        interactionMap.put(SCHEMA_UPDATED_AT, interaction.getUpdatedAt()); //TODO update all at the same time or send isolated interactions?
        interactionMap.put(SCHEMA_ACTIVE, interaction.isActive());
        interactionMap.put(SCHEMA_ACTIVE_ON_FOLDER, interaction.isActiveOnFolder());
        interactionMap.put(SCHEMA_ACTIVE_ON_ACTION_MENU, interaction.isActiveOnActionMenu());
        interactionMap.put(SCHEMA_IS_CHAT, interaction.isChat());
        interactionMap.put(SCHEMA_IS_SAVE_METADATA, interaction.isSaveMetadata());
        interactionMap.put(SCHEMA_PARAMETERS, convertParameters(interaction.getParameters()));
        return interactionMap;
    }

    private List<Map<String, Object>> convertParameters(List<InteractionParameter> parameters) {
        if (CollectionUtils.isEmpty(parameters)) return Collections.emptyList();
        return parameters.stream().map(param -> {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put(PARAMETER_SCHEMA_NAME, param.getName());
            paramMap.put(PARAMETER_SCHEMA_TYPE, param.getType());
            paramMap.put(PARAMETER_SCHEMA_FORMAT, param.getFormat());
            paramMap.put(PARAMETER_SCHEMA_DESCRIPTION, param.getDescription());
            paramMap.put(PARAMETER_SCHEMA_VALUE, param.getValue());
            paramMap.put(PARAMETER_SCHEMA_READ_ONLY, param.isReadOnly());
            paramMap.put(PARAMETER_SCHEMA_SELECTION_READ_ONLY, param.isSelectionReadOnly());
            paramMap.put(PARAMETER_SCHEMA_IS_MAIN_DOCUMENT, param.isMainDocument());
            paramMap.put(PARAMETER_SCHEMA_SELECTED_DOC_IDS, param.getSelectedDocumentIds());
            paramMap.put(PARAMETER_SCHEMA_SELECTED_DOC_ID, param.getSelectedDocumentId());
            paramMap.put(PARAMETER_SCHEMA_ENABLE_DOC_SELECTION, param.isEnableDocSelection());
            paramMap.put(PARAMETER_SCHEMA_IS_CONTEXT_MEMORY, param.isContextMemoryParameter());
            return paramMap;
        }).collect(Collectors.toList());
    }
}
