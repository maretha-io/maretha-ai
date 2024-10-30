package io.maretha.cp.core.operations;

import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.services.APIServiceInterface;
import io.maretha.cp.core.services.RepositoryServiceInterface;
import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Operation(id = GetInteractionSettingsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get CP Interactions", description = "Get Composable Prompts Interaction by API")
public class GetInteractionSettingsOperation {

    public static final String ID = "GetInteractionsOperation";
    private final APIServiceInterface cpService = Framework.getService(APIServiceInterface.class);
    private final RepositoryServiceInterface cpLocalSettingsService = Framework.getService(RepositoryServiceInterface.class);

    @Param(name = "refresh", required = false)
    protected boolean refresh;
    @Context
    protected CoreSession session;

    @OperationMethod
    public Collection<Interaction> run() {
        Map<String, Interaction> interactionsById = new HashMap<>();
        //Get local interactions
        List<Interaction> interactionSettings = cpLocalSettingsService.getLocalInteractionSettings(session);
        if (!CollectionUtils.isEmpty(interactionSettings)) {
            interactionSettings.forEach(interaction -> interactionsById.put(interaction.getId(), interaction));
        }
        //Get remote interactions
        if (refresh || CollectionUtils.isEmpty(interactionSettings)) {
            List<Interaction> remoteInteractions = cpService.getInteractions();
            remoteInteractions.forEach(interaction -> {
                if (!interactionsById.containsKey(interaction.getId())) {
                    interactionsById.put(interaction.getId(), interaction);
                }
            });
        }
        return interactionsById.values();
    }


}
