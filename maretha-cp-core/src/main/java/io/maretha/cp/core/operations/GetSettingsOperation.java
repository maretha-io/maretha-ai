package io.maretha.cp.core.operations;

import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.services.APIService;
import io.maretha.cp.core.services.RepositoryService;
import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Operation(id = GetSettingsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get CP Interactions", description = "Get Composable Prompts Interaction by API")
public class GetSettingsOperation {

    public static final String ID = "GetSettingsOperation";
    private final APIService apiService = Framework.getService(APIService.class);
    private final RepositoryService repositoryService = Framework.getService(RepositoryService.class);

    @Param(name = "refresh", required = false)
    protected boolean refresh;
    @Context
    protected CoreSession session;

    @OperationMethod
    public Settings run() {
        Map<String, Interaction> interactionsById = new HashMap<>();
        //Get local interactions
        Settings settings = repositoryService.getSavedSettings(session);
        List<Interaction> interactionSettings = settings.getInteractions();
        if (!CollectionUtils.isEmpty(interactionSettings)) {
            interactionSettings.forEach(interaction -> interactionsById.put(interaction.getId(), interaction));
        }
        //Get remote interactions
        boolean settingsDocIsPresent = repositoryService.getExistingSettingsDocument(session).isPresent();
        if (settingsDocIsPresent && (refresh || CollectionUtils.isEmpty(interactionSettings))) {
            List<Interaction> remoteInteractions = apiService.getInteractions();
            remoteInteractions.forEach(interaction -> {
                if (!interactionsById.containsKey(interaction.getId())) {
                    interactionsById.put(interaction.getId(), interaction);
                }
            });
        }
        List<Interaction> interactions = new ArrayList<>(interactionsById.values());
        settings.setInteractions(interactions);
        return settings;
    }


}
