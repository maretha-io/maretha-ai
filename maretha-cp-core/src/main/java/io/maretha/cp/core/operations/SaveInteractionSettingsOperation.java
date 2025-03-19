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
    private final RepositoryService cpLocalSettingsService = Framework.getService(RepositoryService.class);

    @Context
    protected CoreSession session;
    @Param(name = "interactions", required = false)
    protected List<ObjectNode> interactions;
    @Param(name = "awsS3AccessEnabled", required = false)
    protected boolean awsS3AccessEnabled;
    @Param(name = "schemaSettings", required = false)
    protected DocTypeWithSchema.Schema schemaSettings;

    @OperationMethod
    public Settings run() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("awsS3AccessEnabled", awsS3AccessEnabled);
        configuration.put("schemaSettings", schemaSettings);
        return cpLocalSettingsService.saveInteractionSettings(session, interactions, configuration);
    }

}
