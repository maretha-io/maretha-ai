package io.maretha.cp.core.bulk.actions;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.maretha.cp.core.services.helper.BlobExtractor;
import io.maretha.cp.core.services.helper.EmbeddingsHelper;

import static io.maretha.cp.core.events.BulkEventListener.DISABLE_BULK_LISTENER;
import static io.maretha.cp.core.events.InteractionsEventListener.DISABLE_INTERACTIONS_LISTENER;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_FACET;
import static io.maretha.cp.core.services.utils.AuditUtils.logAIAudit;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

/**
 * Bulk action that (re)computes embeddings for a set of documents, bypassing the chat/LLM
 * interaction call entirely. Unlike {@link BulkInteractionAction}, this always overwrites
 * existing embedding values rather than only filling in the ones that are empty.
 */
public class BulkRegenerateEmbeddingsAction implements StreamProcessorTopology {

    public static final String ACTION_NAME = "bulkRegenerateEmbeddingsAction";
    public static final String ACTION_FULL_NAME = "document/" + ACTION_NAME;
    private static final Log log = LogFactory.getLog(BulkRegenerateEmbeddingsAction.class);

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                .addComputation(BulkRegenerateEmbeddingsComputation::new,
                        Arrays.asList(INPUT_1 + ":" + ACTION_FULL_NAME, OUTPUT_1 + ":" + STATUS_STREAM))
                .build();
    }

    public static class BulkRegenerateEmbeddingsComputation extends AbstractBulkComputation {

        public BulkRegenerateEmbeddingsComputation() {
            super(ACTION_FULL_NAME, 1);
        }

        @Override
        protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> params) {
            String commandId = this.command.getId();
            log.info("Running BulkRegenerateEmbeddingsComputation: " + commandId + " for ids: " + ids);

            ids.forEach(docId -> {
                if (!session.exists(new IdRef(docId))) {
                    log.error("Skipping non existing document by uuid: " + docId);
                    return;
                }
                DocumentModel doc = session.getDocument(new IdRef(docId));
                doc.putContextData(DISABLE_BULK_LISTENER, true);
                doc.putContextData(DISABLE_INTERACTIONS_LISTENER, true);

                Blob blob = BlobExtractor.getFileContent(doc);
                if (blob == null) {
                    log.info("Skipping document without content blob: " + docId);
                    return;
                }

                if (!doc.isFolder() && !doc.hasFacet(RESULTS_FACET)) {
                    doc.addFacet(RESULTS_FACET);
                }

                logAIAudit(session, doc, commandId, "Regenerating embeddings");
                try {
                    EmbeddingsHelper.addEmbeddings(doc, session, blob, false, false, true);
                } catch (Exception e) {
                    log.error("Failed to regenerate embeddings for document: " + docId, e);
                }
            });

            session.save();
            if (TransactionHelper.isTransactionActive()) {
                TransactionHelper.commitOrRollbackTransaction();
            }
            TransactionHelper.startTransaction();
        }
    }
}
