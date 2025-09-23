package io.maretha.cp.core.services.ai;

import io.maretha.cp.core.models.Interaction;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.util.Map;

public interface InteractionService {
    String invokeModel(DocumentModel doc, Interaction interaction, Map<String, Object> paramsData, String resultSchema);
}
