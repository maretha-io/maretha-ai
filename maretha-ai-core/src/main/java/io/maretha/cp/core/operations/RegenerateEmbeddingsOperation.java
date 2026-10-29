package io.maretha.cp.core.operations;

import io.maretha.cp.core.bulk.actions.BulkRegenerateEmbeddingsAction;
import io.maretha.cp.core.services.helper.BlobExtractor;
import io.maretha.cp.core.services.helper.EmbeddingsHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.runtime.api.Framework;

/**
 * Regenerates (overwrites) embeddings for a document, or for every document with content
 * under a folder/Collection, bypassing the chat/LLM interaction call entirely.
 */
@Operation(id = RegenerateEmbeddingsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Regenerate Embeddings", description = "Regenerate AI embeddings for a document or for all content under a folder/Collection")
public class RegenerateEmbeddingsOperation {

    public static final String ID = "RegenerateEmbeddingsOperation";
    private final Log log = LogFactory.getLog(RegenerateEmbeddingsOperation.class);

    @Context
    protected CoreSession session;

    @OperationMethod
    public String run(DocumentModel doc) {
        if (doc.isFolder()) {
            String nxqlQuery = String.format(
                    "SELECT * FROM Document WHERE ecm:ancestorId = '%s' AND ecm:isTrashed = 0 AND ecm:isProxy = 0 AND ecm:isVersion = 0 AND file:content IS NOT NULL",
                    doc.getId());
            return submitBulkCommand(nxqlQuery);
        }

        if (doc.hasFacet("Collection")) {
            String nxqlQuery = String.format(
                    "SELECT * FROM Document WHERE collectionMember:collectionIds = '%s' AND ecm:isTrashed = 0 AND ecm:isProxy = 0 AND ecm:isVersion = 0 AND file:content IS NOT NULL",
                    doc.getId());
            return submitBulkCommand(nxqlQuery);
        }

        Blob blob = BlobExtractor.getFileContent(doc);
        if (blob == null) {
            return "Document has no content to generate embeddings from.";
        }
        try {
            EmbeddingsHelper.addEmbeddings(doc, session, blob, false, false, true);
        } catch (Exception e) {
            log.error("Failed to regenerate embeddings for document: " + doc.getId(), e);
            return "Failed to regenerate embeddings: " + e.getMessage();
        }
        return "Embeddings regenerated.";
    }

    private String submitBulkCommand(String nxqlQuery) {
        BulkCommand command = new BulkCommand.Builder(BulkRegenerateEmbeddingsAction.ACTION_NAME, nxqlQuery, "system")
                .build();
        BulkService bulkService = Framework.getService(BulkService.class);
        return bulkService.submit(command);
    }
}
