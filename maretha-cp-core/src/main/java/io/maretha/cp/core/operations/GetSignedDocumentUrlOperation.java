package io.maretha.cp.core.operations;

import io.maretha.cp.core.services.CloudStorageFileShare;
import io.maretha.cp.core.services.SignS3Media;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.ManagedBlob;

/**
 *
 */
@Operation(id = GetSignedDocumentUrlOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get Signed Document URL", description = "Get Signed Document URL")
public class GetSignedDocumentUrlOperation {

    public static final String ID = "GetSignedDocumentUrlOperation";

    @OperationMethod
    public String run(DocumentModel documentModel) {
        CloudStorageFileShare cloudStorageFileShare = new SignS3Media();
        ManagedBlob blob = (ManagedBlob) documentModel.getPropertyValue("file:content");
        return cloudStorageFileShare.getPublicRemoteUri(blob).toString();
    }
}
