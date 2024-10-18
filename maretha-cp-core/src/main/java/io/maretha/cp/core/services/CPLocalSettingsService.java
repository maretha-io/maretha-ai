package io.maretha.cp.core.services;

import io.maretha.cp.core.models.CpInteraction;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.repository.Repository;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.runtime.api.Framework;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.maretha.cp.core.operations.SaveInteractionsOperation.CPS_INTERACTIONS_PATH;
import static io.maretha.cp.core.operations.SaveInteractionsOperation.CP_SETTINGS_DOCUMENT_TYPE;

public class CPLocalSettingsService implements CPLocalSettingsServiceInterface {
    @Override
    public List<CpInteraction> getInteractionSettings() {
        Optional<DocumentModel> existingSettingsDocument = getExistingSettingsDocument();
        if (existingSettingsDocument.isPresent()) {
            DocumentModel documentModel = existingSettingsDocument.get();
            return (List<CpInteraction>) documentModel.getPropertyValue(CPS_INTERACTIONS_PATH);
        }
        return Collections.emptyList();
    }

    @Override
    public List<CpInteraction> getActiveInteractionsForDocumentType(String documentType) {
        return getInteractionSettings()
                .stream()
                .filter(cpInteraction -> cpInteraction.getActiveOnDocumentTypes().contains(documentType))
                .filter(CpInteraction::getIsActive)
                .collect(Collectors.toList());
    }

    private Optional<DocumentModel> getExistingSettingsDocument() {
        //TODO and deleted = false
        String query = String.format("SELECT * FROM %s WHERE ecm:isProxy = 0", CP_SETTINGS_DOCUMENT_TYPE);
        DocumentModelList documents = openSession().query(query);
        return documents.stream().findFirst();
    }

    private CoreSession openSession() {
        Repository repository = Framework.getService(RepositoryManager.class).getDefaultRepository();
        if (repository != null) {
            return CoreInstance.getCoreSession(repository.getName());
        }
        throw new NuxeoException("Unable to open session");
    }
}
