package io.maretha.cp.core.bulk.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.services.RunInteractionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

public class BulkInteractionAction implements StreamProcessorTopology {

    public static final String ACTION_NAME = "bulkInteractionAction";
    public static final String ACTION_FULL_NAME = "document/" + ACTION_NAME;
    private static final Log log = LogFactory.getLog(BulkInteractionAction.class);

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                .addComputation(BulkInteractionComputation::new,
                        Arrays.asList(INPUT_1 + ":" + ACTION_FULL_NAME, OUTPUT_1 + ":" + STATUS_STREAM))
                .build();
    }

    public static class BulkInteractionComputation extends AbstractBulkComputation {
        private final ObjectMapper objectMapper = new ObjectMapper();

        public BulkInteractionComputation() {
            super(ACTION_FULL_NAME, 1);
        }

        @Override
        protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> params) {
            log.info("Running BulkInteractionComputation for ids: " + ids);
            RunInteractionService runInteractionService = Framework.getService(RunInteractionService.class);
            String interactionId = (String) params.get("interactionId");
            List<InteractionParameter> parameters = objectMapper
                    .convertValue(params.get("parameters"), objectMapper.getTypeFactory().constructCollectionType(List.class, InteractionParameter.class));
            boolean saveMetadata = (boolean) params.get("saveMetadata");
            boolean saveMetadataModeOverride = (boolean) params.get("saveMetadataModeOverride");
            ids.stream().map(docId -> {
                DocumentModel document = session.getDocument(new IdRef(docId));
                return runInteractionService.uploadFileAndSetStoreId(document, session);
            }).collect(Collectors.toList()).forEach(doc -> runInteractionService.triggerInteraction(doc, session, interactionId, parameters, saveMetadata, saveMetadataModeOverride));
        }
    }
}


