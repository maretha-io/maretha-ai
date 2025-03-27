package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.util.List;
import java.util.Map;

public interface RepositoryService {

    String RESULTS_DOCUMENT_TYPE = "InteractionResults";
    String RESULTS_DOCUMENT_NAME = "InteractionResults";
    String RESULTS_SCHEMA_PATH = "interactionresults:interactions";
    String RESULTS_FACET = "InteractionResultsFacet";
    String RESULTS_SCHEMA = "interactionResults";
    String RESULTS_STORE_ID_SCHEMA_PATH = "interactionresults:storeId";
    String RESULTS_STORE_DIGEST_SCHEMA_PATH = "interactionresults:storeDigest";
    String RESULTS_STORED_DATE_SCHEMA_PATH = "interactionresults:storedDate";
    String SCHEMA_RESPONSE = "response";
    String SCHEMA_CREATED_BY = "createdBy";
    String SCHEMA_CREATED_AT = "createdAt";
    String SCHEMA_DOCUMENT_ID = "documentId";
    String SCHEMA_INTERACTION_ID = "interactionId";

    String SCHEMA_EMBEDDINGS_TEXT = "interactionresults:embeddingsText";
    String SCHEMA_EMBEDDINGS_IMAGE = "interactionresults:embeddingsImage";

    String SETTINGS_DOCUMENT_TYPE = "InteractionSettings";
    String SETTINGS_SCHEMA_NAME = "interactionSettings";
    String SETTINGS_SCHEMA_PATH = "interactionsettings:interactions";
    String SETTINGS_SCHEMA_AWS_S3_ACCESS = "interactionsettings:awsS3AccessEnabled";
    String SETTINGS_SCHEMA_METADATA = "interactionsettings:schemaSettings";
    String SETTINGS_ELASTICSEARCH = "interactionsettings:esSettings";
    String SETTINGS_VERTESIA = "interactionsettings:vertesiaSettings";
    String SETTINGS_EVENTS = "interactionsettings:eventsSettings";

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
    String SCHEMA_IS_CHAT = "chat";
    String SCHEMA_IS_SAVE_METADATA = "saveMetadata";
    String SCHEMA_PARAMETERS = "parameters";

    String PARAMETER_SCHEMA_NAME = "name";
    String PARAMETER_SCHEMA_TYPE = "type";
    String PARAMETER_SCHEMA_FORMAT = "format";
    String PARAMETER_SCHEMA_DESCRIPTION = "description";
    String PARAMETER_SCHEMA_VALUE = "value";
    String PARAMETER_SCHEMA_READ_ONLY = "readOnly";
    String PARAMETER_SCHEMA_SELECTION_READ_ONLY = "selectionReadOnly";
    String PARAMETER_SCHEMA_IS_MAIN_DOCUMENT = "isMainDocument";
    String PARAMETER_SCHEMA_ENABLE_DOC_SELECTION = "enableDocSelection";
    String PARAMETER_SCHEMA_SELECTED_DOC_IDS = "selectedDocumentIds";
    String PARAMETER_SCHEMA_SELECTED_DOC_ID = "selectedDocumentId";
    String PARAMETER_SCHEMA_IS_CONTEXT_MEMORY = "isContextMemoryParameter";

    String SETTINGS_DOCUMENT_NAME = "InteractionSettings";

    Settings getSavedSettings(CoreSession session);

    List<Interaction> getActiveInteractionsForDocumentType(CoreSession session, String documentType, boolean folder);

    List<Interaction> getActiveChatInteractionsForDocumentType(CoreSession session, String documentType, boolean folder);

    Settings saveInteractionSettings(CoreSession session, List<ObjectNode> interactions, Map<String, Object> configuration);

    DocumentModel saveInteractionResponseAsMetadata(DocumentModel doc, CoreSession session, List<ObjectNode> interactions);

    DocumentModel saveInteractionResponseAsDocument(DocumentModel doc, CoreSession session, List<ObjectNode> interactions) throws OperationException;

    String getSettingsProperty(String name, String defaultValue);
}
