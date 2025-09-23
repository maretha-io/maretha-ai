package io.maretha.cp.core.operations;

import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.services.RepositoryService;
import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

/**
 *
 */
@Operation(id = GetSettingsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get CP Interactions", description = "Get Composable Prompts Interaction by API")
public class GetSettingsOperation {

    public static final String ID = "GetSettingsOperation";
    private final RepositoryService repositoryService = Framework.getService(RepositoryService.class);

    @Context
    protected CoreSession session;

    @OperationMethod
    public Settings run() {
        Settings settings = repositoryService.getSavedSettings(session, false);
        if (CollectionUtils.isEmpty(settings.getInteractions())) {
            addDefaultInteractions();
        }
        return settings;
    }

    private void addDefaultInteractions() {
        //TODO load file default_interactions.json
    }


}
