package io.maretha.cp.core.services;

import io.maretha.cp.core.models.InteractionParameter;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.util.List;

public interface RunInteractionService {
    String FILE_CONTENT = "file:content";

    DocumentModel uploadFileAndSetStoreId(DocumentModel doc, CoreSession session, boolean waitUploadCompletion);

    String triggerInteraction(DocumentModel doc, CoreSession session, String interactionId, List<InteractionParameter> parameters, boolean saveMetadata, boolean saveMetadataModeOverride);
}
