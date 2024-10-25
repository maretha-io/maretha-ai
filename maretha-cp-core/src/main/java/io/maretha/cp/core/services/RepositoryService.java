package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.models.InteractionResponse;
import io.maretha.cp.core.models.Settings;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
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

public class RepositoryService implements RepositoryServiceInterface {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Interaction> getLocalInteractionSettings(CoreSession session) {
        Optional<DocumentModel> existingSettingsDocument = getExistingSettingsDocument(session);
        if (existingSettingsDocument.isPresent()) {
            DocumentModel documentModel = existingSettingsDocument.get();
            Settings settings = documentModel.getAdapter(Settings.class);
            return settings.getInteractions();
        }
        return Collections.emptyList();
    }

    @Override
    public List<Interaction> getActiveInteractionsForDocumentType(CoreSession session, String documentType) {
        return getLocalInteractionSettings(session)
                .stream()
                .filter(cpInteraction -> cpInteraction.getActiveOnDocumentTypes().contains(documentType))
                .filter(Interaction::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public DocumentModel saveInteractionSettings(CoreSession session, List<ObjectNode> interactions) {
        DocumentModel cpSettingsDoc;
        Optional<DocumentModel> existingSettingsDocument = getExistingSettingsDocument(session);
        if (existingSettingsDocument.isPresent()) {
            cpSettingsDoc = existingSettingsDocument.get();
        } else {
            //TODO Create a folder and add permissions?
            cpSettingsDoc = session.createDocumentModel("/", SETTINGS_DOCUMENT_NAME, SETTINGS_DOCUMENT_TYPE);
            cpSettingsDoc = session.createDocument(cpSettingsDoc);
        }
        List<HashMap<String, Object>> interactionsMap = interactions.stream()
                .map(objNode -> convertToType(objNode, Interaction.class))
                .map(this::convertToInteractionComplexType)
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(interactionsMap)) {
            throw new NuxeoException("Empty list of settings");
        }

        cpSettingsDoc.setPropertyValue(SETTINGS_SCHEMA_PATH, (Serializable) interactionsMap);
        return session.saveDocument(cpSettingsDoc);
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
        DocumentModel interactionResponseDoc;
        //TODO Create a folder and add permissions?
        interactionResponseDoc = session.createDocumentModel("/", RESULTS_DOCUMENT_TYPE, RESULTS_DOCUMENT_TYPE);
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

    private void addDocumentRelation(DocumentModel sourceDoc, CoreSession session, DocumentModel targetDoc) throws OperationException {
        AutomationService automationService = Framework.getService(AutomationService.class);
        try (OperationContext context = new OperationContext(session)) {
            context.setInput(sourceDoc);
            String predicate = "http://www.nuxeo.org/ecm/schemas/relation#isRelatedTo"; // Relation predicate
            Map<String, Object> params = new HashMap<>();
            params.put("object", targetDoc.getId()); // The target document's ID or reference
            params.put("predicate", predicate);      // The relation predicate (relation type)
            params.put("outgoing", true);            // Optional: Define whether the relation is outgoing (true) or incoming (default is false)
            automationService.run(context, "Document.AddRelation", params);
            context.getCoreSession().save();
        }
    }

    @SneakyThrows
    private HashMap<String, Object> convertToInteractionResponseComplexType(InteractionResponse interactionResponse) {
        HashMap<String, Object> interactionMap = new HashMap<>();
        addNonNullValue(interactionMap, SCHEMA_ID, interactionResponse.getId());
        addNonNullValue(interactionMap, SCHEMA_NAME, interactionResponse.getName());
        addNonNullValue(interactionMap, SCHEMA_DOCUMENT_ID, interactionResponse.getDocumentId());
        addNonNullValue(interactionMap, SCHEMA_INTERACTION_ID, interactionResponse.getInteractionId());
        addNonNullValue(interactionMap, SCHEMA_RESPONSE, objectMapper.writeValueAsString(interactionResponse.getResponse()));
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
        interactionMap.put(SCHEMA_PARAMETERS, convertParameters(interaction.getParameters()));
        return interactionMap;
    }

    private List<Map<String, Object>> convertParameters(List<InteractionParameter> parameters) {
        return parameters.stream().map(param -> {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put(PARAMETER_SCHEMA_NAME, param.getName());
            paramMap.put(PARAMETER_SCHEMA_TYPE, param.getType());
            paramMap.put(PARAMETER_SCHEMA_FORMAT, param.getFormat());
            paramMap.put(PARAMETER_SCHEMA_VALUE, param.getValue());
            return paramMap;
        }).collect(Collectors.toList());
    }
}
