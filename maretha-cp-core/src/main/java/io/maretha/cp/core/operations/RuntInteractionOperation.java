package io.maretha.cp.core.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.services.RunInteractionService;
import io.maretha.cp.core.services.RunInteractionServiceImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
@Operation(id = RuntInteractionOperation.ID, category = Constants.CAT_DOCUMENT, label = "Run Interactions", description = "Run Interaction")
public class RuntInteractionOperation {

    public static final String ID = "RunInteractionOperation";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RunInteractionService runInteractionService = Framework.getService(RunInteractionService.class);
    private final Log log = LogFactory.getLog(RunInteractionServiceImpl.class);

    @Context
    protected CoreSession session;
    @Param(name = "data")
    protected List<ObjectNode> data;
    @Param(name = "interactionId")
    protected String interactionId;

    @OperationMethod
    public String run(DocumentModel doc) {
        log.debug(String.format("RunInteraction interactionId: [%s], docId: [%s]", interactionId, doc.getId()));
        List<InteractionParameter> parameters = objectMapper
                .convertValue(data, objectMapper.getTypeFactory().constructCollectionType(List.class, InteractionParameter.class));
        return runInteractionService.triggerInteraction(doc, session, interactionId, parameters);
    }

}
