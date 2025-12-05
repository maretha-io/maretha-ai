package io.maretha.cp.core.services.helper;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;
import io.maretha.cp.core.services.ai.EmbeddingsService;

import java.util.Base64;
import java.util.Collection;

import static io.maretha.cp.core.services.RepositoryService.SCHEMA_EMBEDDINGS_IMAGE;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_EMBEDDINGS_TEXT;

/**
 * Helper class for managing AI embeddings on documents.
 * Handles text and image embeddings for documents and blobs.
 * Extracted from RunInteractionServiceImpl to improve maintainability.
 */
@Log4j2
public class EmbeddingsHelper {

    private EmbeddingsHelper() {
        // Utility class, prevent instantiation
    }

    /**
     * Adds appropriate embeddings to a document based on its blob type.
     * Handles both image and PDF embeddings.
     *
     * @param doc               Document to add embeddings to
     * @param session           Core session
     * @param blob              Main content blob
     * @param waitForCompletion Whether to wait for completion (currently unused)
     * @param lowPriority       Whether this is low priority (currently unused)
     * @return Updated document model
     */
    public static DocumentModel addEmbeddings(DocumentModel doc, CoreSession session, Blob blob,
            boolean waitForCompletion, boolean lowPriority) {
        try {
            boolean isImage = BlobExtractor.isImage(blob);

            // Add image embeddings if image and not already present
            if (isImage && isEmpty(doc, SCHEMA_EMBEDDINGS_IMAGE)) {
                addImageEmbeddings(doc, blob);
                doc = session.saveDocument(doc);
            }

            // Add text embeddings from image description if image and not already present
            if (isImage && isEmpty(doc, SCHEMA_EMBEDDINGS_TEXT)) {
                addImageTextEmbeddings(doc, blob);
                doc = session.saveDocument(doc);
            }

            // Add text embeddings from PDF if PDF and not already present
            if ("application/pdf".equalsIgnoreCase(blob.getMimeType()) && isEmpty(doc, SCHEMA_EMBEDDINGS_TEXT)) {
                addTextEmbeddings(doc, blob);
                doc = session.saveDocument(doc);
            }
        } catch (Exception e) {
            log.error("Failed to save document: " + doc.getId(), e);
        }

        return doc;
    }

    /**
     * Adds text embeddings from image description (dc:description).
     */
    private static void addImageTextEmbeddings(DocumentModel doc, Blob blob) {
        try {
            String description = (String) doc.getPropertyValue("dc:description");
            double[] vector = Framework.getService(EmbeddingsService.class).textToEmbedding(description);
            doc.setPropertyValue(SCHEMA_EMBEDDINGS_TEXT, vector);
        } catch (Exception e) {
            log.error("Failed to add text embeddings", e);
        }
    }

    /**
     * Adds text embeddings from PDF content summary.
     */
    private static void addTextEmbeddings(DocumentModel doc, Blob blob) {
        try {
            double[] documentEmbeddings = Framework.getService(EmbeddingsService.class).embedPdfSummary(blob);
            doc.setPropertyValue(SCHEMA_EMBEDDINGS_TEXT, documentEmbeddings);
        } catch (Exception e) {
            log.error("Failed to save textEmbeddings", e);
        }
    }

    /**
     * Adds image embeddings from blob.
     */
    private static void addImageEmbeddings(DocumentModel doc, Blob blob) {
        try {
            String base64 = Base64.getEncoder().encodeToString(blob.getByteArray());
            double[] documentEmbeddings = Framework.getService(EmbeddingsService.class)
                    .embedImage(base64, doc.getTitle(), 1024);
            doc.setPropertyValue(SCHEMA_EMBEDDINGS_IMAGE, documentEmbeddings);
        } catch (Exception e) {
            log.error("Failed to save imageEmbeddings", e);
        }
    }

    /**
     * Checks if a schema property is empty/not set.
     */
    private static boolean isEmpty(DocumentModel doc, String schema) {
        return CollectionUtils.isEmpty((Collection) doc.getPropertyValue(schema));
    }
}
