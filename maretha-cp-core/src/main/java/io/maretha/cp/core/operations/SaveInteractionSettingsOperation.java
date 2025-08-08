package io.maretha.cp.core.operations;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.services.RepositoryService;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Operation(id = SaveInteractionSettingsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Save CP Interactions", description = "Save Composable Prompts Interactions")
public class SaveInteractionSettingsOperation {

    public static final String ID = "SaveInteractionsOperation";
    public static final String VERTESIA_SETTINGS = "vertesiaSettings";
    public static final String EVENTS_SETTINGS = "eventsSettings";
    public static final String ES_SETTINGS = "esSettings";
    public static final String SCHEMA_SETTINGS = "schemaSettings";
    public static final String AWS_S3_ACCESS_ENABLED = "awsS3AccessEnabled";
    private final RepositoryService cpLocalSettingsService = Framework.getService(RepositoryService.class);

    @Context
    protected CoreSession session;
    @Param(name = "interactions", required = false)
    protected List<ObjectNode> interactions;
    @Param(name = AWS_S3_ACCESS_ENABLED, required = false)
    protected Boolean awsS3AccessEnabled;
    @Param(name = SCHEMA_SETTINGS, required = false)
    protected DocTypeWithSchema.Schema schemaSettings;
    @Param(name = ES_SETTINGS, required = false)
    protected Map<String, String> esSettings;
    @Param(name = VERTESIA_SETTINGS, required = false)
    protected Map<String, String> vertesiaSettings;
    @Param(name = EVENTS_SETTINGS, required = false)
    protected List<ObjectNode> eventsSettings;

    @OperationMethod
    public Settings run() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(AWS_S3_ACCESS_ENABLED, awsS3AccessEnabled);
        configuration.put(SCHEMA_SETTINGS, schemaSettings);
        configuration.put(ES_SETTINGS, esSettings);
        configuration.put(VERTESIA_SETTINGS, vertesiaSettings);
        configuration.put(EVENTS_SETTINGS, eventsSettings);
        return cpLocalSettingsService.saveInteractionSettings(session, interactions, configuration);
    }

}
