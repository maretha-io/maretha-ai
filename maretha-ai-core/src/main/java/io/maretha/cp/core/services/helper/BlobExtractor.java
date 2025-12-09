package io.maretha.cp.core.services.helper;

import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.picture.api.adapters.PictureBlobHolder;

import java.util.*;

/**
 * Helper class for extracting blobs and attachments from documents.
 * Handles file:content and files:files properties.
 * Extracted from RunInteractionServiceImpl to improve maintainability.
 */
public class BlobExtractor {

    // Schema constants
    public static final String FILE_CONTENT = "file:content";
    public static final String FILES_SCHEMA = "files";
    public static final String FILE_SCHEMA = "file";
    public static final String FILE_CONTENTS = "files:files";

    private BlobExtractor() {
        // Utility class, prevent instantiation
    }

    /**
     * Gets the main file content blob from a document.
     * For Picture documents, retrieves FullHD view; otherwise returns file:content.
     *
     * @param doc Document to extract blob from
     * @return Main content blob, or null if not present
     */
    public static Blob getFileContent(DocumentModel doc) {
        // Check if document has file schema before accessing file:content
        if (!doc.hasSchema(FILE_SCHEMA)) {
            return null;
        }
        if (doc.hasFacet("Picture")) {
            PictureBlobHolder pictureBlobHolder = new PictureBlobHolder(doc, "picture:views/3/content"); // FullHD
            Blob blob = pictureBlobHolder.getBlob();
            if (blob != null && blob.getLength() > 0 && !blob.getFilename().contains("empty_picture")) {
                return blob;
            }
        }
        return (Blob) doc.getPropertyValue(FILE_CONTENT);
    }

    /**
     * Gets all attachment blobs from files:files property.
     *
     * @param doc Document to extract attachments from
     * @return List of attachment blobs, or empty list if none
     */
    public static List<Blob> getAttachments(DocumentModel doc) {
        try {
            if (!doc.hasSchema(FILES_SCHEMA)) {
                return Collections.emptyList();
            }

            List<Blob> blobs = new ArrayList<>();
            @SuppressWarnings("unchecked")
            ArrayList<Map> attachments = (ArrayList<Map>) doc.getPropertyValue(FILE_CONTENTS);

            if (CollectionUtils.isNotEmpty(attachments)) {
                for (Map attachment : attachments) {
                    Blob blob = (Blob) attachment.get(FILE_SCHEMA);
                    blobs.add(blob);
                }
            }
            return blobs;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Gets all blobs (file:content + files:files) as a map.
     * Map keys are blob digest (or filename if digest is null).
     *
     * @param doc Document to extract blobs from
     * @return Map of digest/filename -> Blob
     */
    public static Map<String, Blob> getAttachmentsMap(DocumentModel doc) {
        List<Blob> attachments = new ArrayList<>(getAttachments(doc));
        Blob docBlob = getFileContent(doc);

        if (docBlob != null) {
            attachments.add(docBlob);
        }

        Map<String, Blob> attachmentsMap = new HashMap<>();
        attachments.forEach(blob -> {
            if (blob != null) {
                String key = blob.getDigest() != null ? blob.getDigest() : blob.getFilename();
                attachmentsMap.put(key, blob);
            }
        });

        return attachmentsMap;
    }

    /**
     * Checks if a blob is an image based on MIME type.
     *
     * @param blob Blob to check
     * @return true if image MIME type, false otherwise
     */
    public static boolean isImage(Blob blob) {
        if( blob == null) {
            return false;
        }
        return blob.getMimeType() != null && blob.getMimeType().startsWith("image/");
    }
}
