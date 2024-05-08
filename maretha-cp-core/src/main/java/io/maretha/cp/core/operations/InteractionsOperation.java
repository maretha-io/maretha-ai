package io.maretha.cp.core.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.services.ComposablePromptsServiceInterface;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.runtime.api.Framework;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 */
@Operation(id = InteractionsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get CP Interactions", description = "Get Composable Prompts Interaction by API")
public class InteractionsOperation {

    public static final String ID = "InteractionsOperation";
    @Context
    protected CoreSession session;
    @Param(name = "path", required = false)
    protected String path;
    ObjectMapper objectMapper = new ObjectMapper();

    @OperationMethod
    public Blob run() {
        ComposablePromptsServiceInterface cpService = Framework.getService(ComposablePromptsServiceInterface.class);
        String interactions = cpService.getInteractions();
        return new StringBlob(interactions, "application/json");
    }

}
