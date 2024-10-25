package io.maretha.cp.core.operations;

import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.services.RepositoryServiceInterface;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;

import java.util.Collection;
import java.util.Collections;

/**
 *
 */
@Operation(id = GetInteractionsForDocumentOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get CP Interactions for Document", description = "Get Interactions for Document")
public class GetInteractionsForDocumentOperation {

    public static final String ID = "GetInteractionsForDocumentOperation";
    private final RepositoryServiceInterface cpLocalSettingsService = Framework.getService(RepositoryServiceInterface.class);

    @Context
    protected CoreSession session;

    @OperationMethod
    public Collection<Interaction> run(DocumentModel doc) {
        if (doc == null) return Collections.emptyList();
        return cpLocalSettingsService.getActiveInteractionsForDocumentType(session, doc.getType(), doc.hasFacet("Folder"));
    }

}
