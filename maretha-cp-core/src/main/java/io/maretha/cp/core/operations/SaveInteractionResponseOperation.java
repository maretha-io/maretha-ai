package io.maretha.cp.core.operations;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.services.RepositoryService;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;

import java.util.List;

/**
 *
 */
@Operation(id = SaveInteractionResponseOperation.ID, category = Constants.CAT_DOCUMENT, label = "Save InteractionResponse", description = "Save InteractionResponse")
public class SaveInteractionResponseOperation {

    public static final String ID = "SaveInteractionResponseOperation";
    private final RepositoryService cpLocalSettingsService = Framework.getService(RepositoryService.class);

    @Context
    protected CoreSession session;
    @Param(name = "interactions")
    protected List<ObjectNode> interactions;
    @Param(name = "saveOnMetadata")
    protected boolean saveOnMetadata = true;

    @OperationMethod
    public DocumentModel run(DocumentModel doc) throws OperationException {
        if (saveOnMetadata) {
            return cpLocalSettingsService.saveInteractionResponseAsMetadata(doc, session, interactions);
        }
        return cpLocalSettingsService.saveInteractionResponseAsDocument(doc, session, interactions);
    }

}
