package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.Interaction;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.util.List;

public interface RepositoryServiceInterface {

    String RESULTS_DOCUMENT_TYPE = "InteractionResults";
    String RESULTS_SCHEMA_PATH = "interactionresults:interactions";
    String RESULTS_FACET = "InteractionResultsFacet";
    String RESULTS_SCHEMA = "interactionResults";
    String SCHEMA_RESPONSE = "response";
    String SCHEMA_CREATED_BY = "createdBy";
    String SCHEMA_CREATED_AT = "createdAt";
    String SCHEMA_DOCUMENT_ID = "documentId";
    String SCHEMA_INTERACTION_ID = "interactionId";

    String SETTINGS_DOCUMENT_TYPE = "InteractionSettings";
    String SETTINGS_SCHEMA_NAME = "interactionSettings";
    String SETTINGS_SCHEMA_PATH = "interactionsettings:interactions";

    String SCHEMA_ID = "id";
    String SCHEMA_DESCRIPTION = "description";
    String SCHEMA_NAME = "name";
    String SCHEMA_STATUS = "status";
    String SCHEMA_VERSION = "version";
    String SCHEMA_ACTIVE_ON_DOCUMENTS = "activeOnDocumentTypes";
    String SCHEMA_UPDATED_AT = "updatedAt";
    String SCHEMA_ACTIVE = "active";
    String SCHEMA_ACTIVE_ON_FOLDER = "activeOnFolder";
    String SCHEMA_ACTIVE_ON_ACTION_MENU = "activeOnActionMenu";
    String SCHEMA_PARAMETERS = "parameters";

    String PARAMETER_SCHEMA_NAME = "name";
    String PARAMETER_SCHEMA_TYPE = "type";
    String PARAMETER_SCHEMA_FORMAT = "format";
    String PARAMETER_SCHEMA_VALUE = "value";
    String SETTINGS_DOCUMENT_NAME = "InteractionSettings";

    List<Interaction> getLocalInteractionSettings(CoreSession session);

    List<Interaction> getActiveInteractionsForDocumentType(CoreSession session, String documentType, boolean folder);

    DocumentModel saveInteractionSettings(CoreSession session, List<ObjectNode> interactions);

    DocumentModel saveInteractionResponseAsMetadata(DocumentModel doc, CoreSession session, List<ObjectNode> interactions);

    DocumentModel saveInteractionResponseAsDocument(DocumentModel doc, CoreSession session, List<ObjectNode> interactions) throws OperationException;
}
