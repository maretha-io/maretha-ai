package io.maretha.cp.core.operations;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.services.RepositoryService;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;

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
    @Param(name = "interactions")
    protected List<ObjectNode> interactions;
    @Param(name = "awsS3AccessEnabled")
    protected boolean awsS3AccessEnabled;

    @OperationMethod
    public DocumentModel run() {
        Map<String, Object> configuration = Map.of("awsS3AccessEnabled", awsS3AccessEnabled);
        return cpLocalSettingsService.saveInteractionSettings(session, interactions, configuration);
    }

}
