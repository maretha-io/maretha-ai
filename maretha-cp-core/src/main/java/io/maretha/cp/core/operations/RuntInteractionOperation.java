package io.maretha.cp.core.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.bulk.actions.BulkInteractionAction;
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
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
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
    @Param(name = "saveMetadata", required = false)
    protected boolean saveMetadata;
    @Param(name = "saveMetadataModeOverride", required = false)
    protected boolean saveMetadataModeOverride;

    @OperationMethod
    public String run(DocumentModel doc) {
        log.debug(String.format("RunInteraction interactionId: [%s], docId: [%s]", interactionId, doc.getId()));
        List<InteractionParameter> parameters = objectMapper
                .convertValue(data, objectMapper.getTypeFactory().constructCollectionType(List.class, InteractionParameter.class));

        if (doc.isFolder()) {
            String nxqlQuery = String.format("SELECT * FROM Document WHERE ecm:ancestorId = '%s' AND ecm:isTrashed = 0 AND ecm:isProxy = 0" +
                    " AND file:content IS NOT NULL", doc.getId());
            BulkCommand.Builder builder = new BulkCommand.Builder(BulkInteractionAction.ACTION_NAME, nxqlQuery, "system")
                    .param("interactionId", interactionId)
                    .param("parameters", (Serializable) parameters)
                    .param("saveMetadata", saveMetadata)
                    .param("saveMetadataModeOverride", saveMetadataModeOverride);
            BulkCommand command = builder.build();
            BulkService bulkService = Framework.getService(BulkService.class);
            return bulkService.submit(command);
        }
        return runInteractionService.triggerInteraction(doc, session, interactionId, parameters, saveMetadata, saveMetadataModeOverride);
    }

}
