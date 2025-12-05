package io.maretha.cp.core.services.utils;

import org.apache.commons.codec.binary.Base64;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.runtime.api.Framework;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlobUtils {

    public static void addAttachment(CoreSession session, DocumentModel doc, String text) throws Exception {
        String mimeType = "text/html";
        Blob blob = Blobs.createBlob(text, mimeType);

        MimetypeRegistry mimetypeService = Framework.getService(MimetypeRegistry.class);
        List<String> extensionsFromMimetypeName = mimetypeService.getExtensionsFromMimetypeName(mimeType);
        String extension = extensionsFromMimetypeName.isEmpty() ? "html" : extensionsFromMimetypeName.get(0);
        String filename = "ai_attachment" + "." + extension;
        blob.setFilename(filename);

        // Get existing attachments
        List<Map<String, Object>> files = (List<Map<String, Object>>) doc.getPropertyValue("files:files");
        if (files == null) {
            files = new ArrayList<>();
        }

        // Create new attachment entry
        Map<String, Object> fileEntry = new HashMap<>();
        fileEntry.put("file", blob);
        fileEntry.put("filename", filename);

        // Add to list
        files.add(fileEntry);

        // Update the document property
        doc.setPropertyValue("files:files", (Serializable) files);

        // Save changes
        session.saveDocument(doc);
        session.save();
    }
}
