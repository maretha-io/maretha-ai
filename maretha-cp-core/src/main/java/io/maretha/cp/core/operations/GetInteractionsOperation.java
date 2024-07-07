package io.maretha.cp.core.operations;

import io.maretha.cp.core.models.CpInteraction;
import io.maretha.cp.core.services.CPLocalSettingsServiceInterface;
import io.maretha.cp.core.services.CPServiceInterface;
import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

import java.util.List;

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
        CPLocalSettingsServiceInterface cpLocalSettingsService = Framework.getService(CPLocalSettingsServiceInterface.class);
        List<CpInteraction> interactionSettings = cpLocalSettingsService.getInteractionSettings();
        if (!CollectionUtils.isEmpty(interactionSettings)) {
            return interactionSettings;
        }
        CPServiceInterface cpService = Framework.getService(CPServiceInterface.class);
        return cpService.getInteractions();
    }


}
