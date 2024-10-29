package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.maretha.cp.core.models.InteractionParameter;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.util.List;

public interface RunInteractionService {
    JsonNode triggerInteraction(DocumentModel doc, CoreSession session, String interactionId, List<InteractionParameter> parameters);
}
