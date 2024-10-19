package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.Interaction;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.util.List;

public interface SavedSettingsServiceInterface {
    List<Interaction> getLocalSettings(CoreSession session);

    List<Interaction> getActiveInteractionsForDocumentType(CoreSession session, String documentType);

    DocumentModel saveSettings(CoreSession session, List<ObjectNode> interactions);
}
