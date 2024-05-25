package io.maretha.cp.core.operations;

import io.maretha.cp.core.models.CpInteraction;
import io.maretha.cp.core.services.ComposablePromptsServiceInterface;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.runtime.api.Framework;

import java.util.List;
import java.util.Optional;

import static io.maretha.cp.core.operations.SaveInteractionsOperation.CP_SETTINGS_DOCUMENT_TYPE;
import static io.maretha.cp.core.operations.SaveInteractionsOperation.CPS_INTERACTIONS_PATH;

/**
 *
 */
@Operation(id = GetInteractionsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get CP Interactions", description = "Get Composable Prompts Interaction by API")
public class GetInteractionsOperation {

    public static final String ID = "GetInteractionsOperation";
    @Context
    protected CoreSession session;

    @OperationMethod
    public List<CpInteraction> run() {
        //TODO Return local settings + check new CP Interactions and merge the results
        if (getExistingSettingsDocument().isPresent()) {
            DocumentModel documentModel = getExistingSettingsDocument().get();
            return (List<CpInteraction>) documentModel.getPropertyValue(CPS_INTERACTIONS_PATH);
        }
        ComposablePromptsServiceInterface cpService = Framework.getService(ComposablePromptsServiceInterface.class);
        return cpService.getInteractions();
    }

    private Optional<DocumentModel> getExistingSettingsDocument() {
        String query = String.format("SELECT * FROM %s WHERE ecm:isProxy = 0", CP_SETTINGS_DOCUMENT_TYPE);
        DocumentModelList documents = session.query(query);
        return documents.stream().findFirst();
    }

}
