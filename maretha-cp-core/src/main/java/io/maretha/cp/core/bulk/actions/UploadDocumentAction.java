package io.maretha.cp.core.bulk.actions;

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

import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

public class UploadDocumentAction implements StreamProcessorTopology {

    public static final String ACTION_NAME = "uploadDocumentAction";
    public static final String ACTION_FULL_NAME = "upload/" + ACTION_NAME;
    private static final Log log = LogFactory.getLog(UploadDocumentAction.class);

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                .addComputation(UploadDocumentComputation::new,
                        Arrays.asList(INPUT_1 + ":" + ACTION_FULL_NAME, OUTPUT_1 + ":" + STATUS_STREAM))
                .build();
    }

    public static class UploadDocumentComputation extends AbstractBulkComputation {

        public UploadDocumentComputation() {
            super(ACTION_FULL_NAME, 1);
        }

        @Override
        protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> properties) {
            log.info("Running UploadDocumentComputation for ids: " + ids);
            RunInteractionService runInteractionService = Framework.getService(RunInteractionService.class);
            ids.forEach(docId -> {
                DocumentModel document = session.getDocument(new IdRef(docId));
                runInteractionService.uploadFileAndSetStoreId(document, session);
            });
        }
    }
}


