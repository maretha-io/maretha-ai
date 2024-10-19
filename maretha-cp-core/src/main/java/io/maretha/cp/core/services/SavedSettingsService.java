package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.models.Settings;
import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.repository.Repository;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SavedSettingsService implements SavedSettingsServiceInterface {
    public static final String INTERACTION_RESULTS_DOCUMENT_TYPE = "InteractionResults";
    public static final String INTERACTION_RESULTS_PATH = "interactionresults:interactions";
    public static final String INTERACTION_RESULTS_FACET = "InteractionResultsFacet";
    public static final String INTERACTION_RESULTS_SCHEMA = "interactionResults";
    public static final String CP_SETTINGS_DOCUMENT_TYPE = "InteractionSettings";
    public static final String CP_SETTINGS_SCHEMA_NAME = "interactionSettings";
    public static final String CPS_INTERACTIONS_PATH = "interactionsettings:interactions";
    public static final String INTERACTION_SCHEMA_ID = "id";
    public static final String INTERACTION_SCHEMA_DESCRIPTION = "description";
    public static final String INTERACTION_SCHEMA_NAME = "name";
    public static final String INTERACTION_SCHEMA_STATUS = "status";
    public static final String INTERACTION_SCHEMA_ACTIVE_ON_DOCUMENTS = "activeOnDocumentTypes";
    public static final String INTERACTION_SCHEMA_UPDATED_AT = "updatedAt";
    public static final String INTERACTION_SCHEMA_ACTIVE = "active";
    public static final String INTERACTION_SCHEMA_PARAMETERS = "parameters";
    public static final String INTERACTION_PARAMETER_SCHEMA_NAME = "name";
    public static final String INTERACTION_PARAMETER_SCHEMA_TYPE = "type";
    public static final String INTERACTION_PARAMETER_SCHEMA_FORMAT = "format";
    public static final String INTERACTION_PARAMETER_SCHEMA_VALUE = "value";
    public static String INTERACTION_SETTINGS_DOCUMENT_NAME = "InteractionSettings";
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<Interaction> getLocalSettings(CoreSession session) {
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
        return getLocalSettings(session)
                .stream()
                .filter(cpInteraction -> cpInteraction.getActiveOnDocumentTypes().contains(documentType))
                .filter(Interaction::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public DocumentModel saveSettings(CoreSession session, List<ObjectNode> interactions) {
        DocumentModel cpSettingsDoc;
        Optional<DocumentModel> existingSettingsDocument = getExistingSettingsDocument(session);
        if (existingSettingsDocument.isPresent()) {
            cpSettingsDoc = existingSettingsDocument.get();
        } else {
            //TODO Create a folder and add permissions?
            cpSettingsDoc = session.createDocumentModel("/", INTERACTION_SETTINGS_DOCUMENT_NAME, CP_SETTINGS_DOCUMENT_TYPE);
            cpSettingsDoc = session.createDocument(cpSettingsDoc);
        }
        List<HashMap<String, Object>> interactionsMap = interactions.stream()
                .map(this::convertToCpInteraction)
                .map(this::convertComplexType).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(interactionsMap)) {
            throw new NuxeoException("Empty list of settings");
        }

        cpSettingsDoc.setPropertyValue(CPS_INTERACTIONS_PATH, (Serializable) interactionsMap);
        return session.saveDocument(cpSettingsDoc);
    }

    private Optional<DocumentModel> getExistingSettingsDocument(CoreSession session) {
        String query = String.format("SELECT * FROM %s WHERE ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:isTrashed = 0", CP_SETTINGS_DOCUMENT_TYPE);
        DocumentModelList documents = session.query(query);
        return documents.stream().findFirst();
    }

    private CoreSession openSession() {
        Repository repository = Framework.getService(RepositoryManager.class).getDefaultRepository();
        if (repository != null) {
            return CoreInstance.getCoreSession(repository.getName());
        }
        throw new NuxeoException("Unable to open session");
    }

    private Interaction convertToCpInteraction(ObjectNode obj) {
        try {
            return mapper.treeToValue(obj, Interaction.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private HashMap<String, Object> convertComplexType(Interaction interaction) {
        HashMap<String, Object> interactionMap = new HashMap<>();
        interactionMap.put(INTERACTION_SCHEMA_ID, interaction.getId());
        interactionMap.put(INTERACTION_SCHEMA_DESCRIPTION, interaction.getDescription());
        interactionMap.put(INTERACTION_SCHEMA_NAME, interaction.getName());
        interactionMap.put(INTERACTION_SCHEMA_STATUS, interaction.getStatus());
        interactionMap.put(INTERACTION_SCHEMA_ACTIVE_ON_DOCUMENTS, interaction.getActiveOnDocumentTypes());
        interactionMap.put(INTERACTION_SCHEMA_UPDATED_AT, interaction.getUpdated_at()); //TODO update all at the same time or send isolated interactions?
        interactionMap.put(INTERACTION_SCHEMA_ACTIVE, interaction.isActive());
        interactionMap.put(INTERACTION_SCHEMA_PARAMETERS, convertParameters(interaction.getParameters()));
        return interactionMap;
    }

    private List<Map<String, Object>> convertParameters(List<InteractionParameter> parameters) {
        return parameters.stream().map(param -> {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put(INTERACTION_PARAMETER_SCHEMA_NAME, param.getName());
            paramMap.put(INTERACTION_PARAMETER_SCHEMA_TYPE, param.getType());
            paramMap.put(INTERACTION_PARAMETER_SCHEMA_FORMAT, param.getFormat());
            paramMap.put(INTERACTION_PARAMETER_SCHEMA_VALUE, param.getValue());
            return paramMap;
        }).collect(Collectors.toList());
    }
}
