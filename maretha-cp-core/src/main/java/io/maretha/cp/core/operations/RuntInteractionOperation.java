package io.maretha.cp.core.operations;

import io.maretha.cp.core.services.CPServiceInterface;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.util.Map;

import static io.maretha.cp.core.listeners.DocumentChangeListener.FILE_CONTENT;

/**
 *
 */
@Operation(id = RuntInteractionOperation.ID, category = Constants.CAT_DOCUMENT, label = "Run Interactions", description = "")
public class RuntInteractionOperation {

    public static final String ID = "RunInteractionOperation";
    @Context
    protected CoreSession session;
    @Param(name = "data")
    protected Map<String, String> data;
    @Param(name = "interactionId")
    protected String interactionId;

    @OperationMethod
    public String run(DocumentModel doc) throws IOException {
        Property blobProperty = doc.getProperty(FILE_CONTENT);
        Blob blob = (Blob) blobProperty.getValue();
        String docText = blob.getString();
        data.put("text", docText);
        return Framework.getService(CPServiceInterface.class).getInteractionResult(interactionId, data);
    }


}
