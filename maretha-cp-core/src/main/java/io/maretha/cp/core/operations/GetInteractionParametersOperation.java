package io.maretha.cp.core.operations;

import io.maretha.cp.core.services.CPServiceInterface;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.runtime.api.Framework;

import java.util.List;

/**
 *
 */
@Operation(id = GetInteractionParametersOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get CP Interactions", description = "Get Composable Prompts Interaction by API")
public class GetInteractionParametersOperation {

    public static final String ID = "GetInteractionParametersOperation";
    @Param(name = "interactionId")
    protected String interactionId;

    @OperationMethod
    public List<Object> run() {
        CPServiceInterface cpService = Framework.getService(CPServiceInterface.class);
        return cpService.getInteractionParameters(interactionId);
    }


}
