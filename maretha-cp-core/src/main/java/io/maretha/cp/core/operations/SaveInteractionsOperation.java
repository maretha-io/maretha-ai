package io.maretha.cp.core.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.CpInteraction;
import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.versioning.VersioningService;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.nuxeo.ecm.core.api.CoreSession.ALLOW_VERSION_WRITE;

/**
 *
 */
@Operation(id = SaveInteractionsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Save CP Interactions", description = "Save Composable Prompts Interactions")
public class SaveInteractionsOperation {

    public static final String ID = "SaveInteractionsOperation";
    public static final String CP_SETTINGS_DOCUMENT_TYPE = "CPSettings";
    public static final String CPS_INTERACTIONS_PATH = "cps:interactions";
    @Context
    protected CoreSession session;
    @Param(name = "interactions", required = true)
    protected List<ObjectNode> interactions;

    ObjectMapper mapper = new ObjectMapper();

    @OperationMethod
    public DocumentModel run() {
        DocumentModel cpSettingsDoc;
        if (getExistingSettingsDocument().isPresent()) {
            cpSettingsDoc = getExistingSettingsDocument().get();
        } else {
            cpSettingsDoc = session.createDocumentModel("/", "CPSettings", CP_SETTINGS_DOCUMENT_TYPE);
            cpSettingsDoc = session.createDocument(cpSettingsDoc);
        }
        List<HashMap<String, Object>> interactionsMap = interactions.stream()
                .map(this::convertToCpInteraction)
                .map(this::convertComplexType).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(interactionsMap)) {
            throw new NuxeoException("Empty list of settings");
        }

        cpSettingsDoc.setPropertyValue(CPS_INTERACTIONS_PATH, (Serializable) interactionsMap);
        if (cpSettingsDoc.isVersion()) {
            cpSettingsDoc.putContextData(ALLOW_VERSION_WRITE, Boolean.TRUE);
        }
        cpSettingsDoc.putContextData(VersioningService.VERSIONING_OPTION, VersioningOption.MINOR);
        cpSettingsDoc.putContextData(VersioningService.CHECKIN_COMMENT, "Settings updated");
        return session.saveDocument(cpSettingsDoc);
    }

    private CpInteraction convertToCpInteraction(ObjectNode obj) {
        try {
            return mapper.treeToValue(obj, CpInteraction.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<DocumentModel> getExistingSettingsDocument() {
        String query = String.format("SELECT * FROM %s WHERE ecm:isProxy = 0", CP_SETTINGS_DOCUMENT_TYPE);
        DocumentModelList documents = session.query(query);
        return documents.stream().findFirst();
    }

    private HashMap<String, Object> convertComplexType(CpInteraction interaction) {
        HashMap<String, Object> interactionMap = new HashMap<>();
        interactionMap.put("id", interaction.getId());
        interactionMap.put("description", interaction.getDescription());
        interactionMap.put("name", interaction.getName());
        interactionMap.put("status", interaction.getName());
        interactionMap.put("activeOnDocumentTypes", interaction.getActiveOnDocumentTypes());
        interactionMap.put("updatedAt", interaction.getUpdated_at());
        interactionMap.put("isActive", interaction.getIsActive());
        return interactionMap;
    }

}
