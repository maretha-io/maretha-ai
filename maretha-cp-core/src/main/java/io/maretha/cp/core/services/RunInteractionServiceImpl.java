package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.maretha.cp.core.bulk.actions.UploadDocumentAction;
import io.maretha.cp.core.models.InteractionParameter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.runtime.api.Framework;

import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.maretha.cp.core.services.RepositoryService.RESULTS_FACET;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORED_DATE_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORE_ID_SCHEMA_PATH;

public class RunInteractionServiceImpl implements RunInteractionService {

    private final Log log = LogFactory.getLog(RunInteractionServiceImpl.class);
    private final APIService apiService = Framework.getService(APIService.class);

    public static boolean blobIsSupportedMimeType(DocumentModel doc) {
        if (!doc.hasSchema("file")) return false;
        try {
            Property blobProperty = doc.getProperty(FILE_CONTENT);
            Blob blob = (Blob) blobProperty.getValue();
            String mimeType = blob.getMimeType();
            return getSupportedMimeTypes().contains(mimeType);
        } catch (Exception ex) {
            return false;
        }
    }

    private static String getSupportedMimeTypes() {
        return Framework.getProperty("cp.supported.mime-types", "text/plain,application/pdf");
    }

    @Override
    public JsonNode triggerInteraction(DocumentModel doc, CoreSession session, String interactionId, List<InteractionParameter> parameters) {
        if (doc.isFolder()) {
            List<DocumentModel> childrenDocs = getChildrenDocuments(session, doc);
            List<DocumentModel> childrenDocsWithStoreId = uploadFilesAndSetStoreId(session, childrenDocs);
            Map<String, Object> paramsMap = buildParametersForFolder(childrenDocsWithStoreId, parameters);
            return apiService.getInteractionResult(interactionId, paramsMap);
        } else {
            doc = uploadFileAndSetStoreId(doc, session);
            Map<String, Object> paramsMap = buildParameters(doc, parameters);
            return apiService.getInteractionResult(interactionId, paramsMap);
        }
    }

    private List<DocumentModel> uploadFilesAndSetStoreId(CoreSession session, List<DocumentModel> docs) {
        String ids = docs.stream().map(doc -> "'" + doc.getId() + "'").collect(Collectors.joining(", "));
        String query = "SELECT * FROM Document WHERE ecm:uuid IN (" + ids + ") AND ecm:mixinType != 'HiddenInNavigation' AND ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:isTrashed = 0 ";
        BulkCommand.Builder builder = new BulkCommand.Builder(UploadDocumentAction.ACTION_NAME, query, "system");
        BulkCommand command = builder.build();
        BulkService bulkService = Framework.getService(BulkService.class);
        String commandId = bulkService.submit(command);
        //TODO wait for bulkService to finish?
        return docs;
    }

    public DocumentModel uploadFileAndSetStoreId(DocumentModel doc, CoreSession session) {
        String storeId = null;
        if (doc.hasFacet(RESULTS_FACET)) {
            storeId = (String) doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
        }
        if (StringUtils.isEmpty(storeId)) {
            doc.addFacet(RESULTS_FACET);
            storeId = apiService.uploadFileToStore((Blob) doc.getPropertyValue(FILE_CONTENT));
            doc.setPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH, storeId);
            doc.setPropertyValue(RESULTS_STORED_DATE_SCHEMA_PATH, new GregorianCalendar());
            return session.saveDocument(doc);
        }
        return doc;
    }

    private List<DocumentModel> getChildrenDocuments(CoreSession session, DocumentModel parentDoc) {
        String nxqlQuery = String.format("SELECT * FROM Document WHERE ecm:ancestorId = '%s' AND ecm:isTrashed = 0 AND ecm:isProxy = 0" +
                " AND file:content IS NOT NULL", parentDoc.getId());
        return session.query(nxqlQuery);
    }

    private Map<String, Object> buildParameters(DocumentModel doc, List<InteractionParameter> parameters) {
        Map<String, Object> paramsMap = new HashMap<>();
        parameters.forEach(param -> {
            String name = param.getName();
            Object value = param.getValue();
            String type = param.getType();
            String format = param.getFormat();
            List<String> selectedDocumentIds = param.getSelectedDocumentIds();
            boolean isDocumentOrMediaFormat = "document".equalsIgnoreCase(format) || "media".equalsIgnoreCase(format);
            if (isDocumentOrMediaFormat) {
//                paramValue = getSignedS3Url(doc); //Signed s3 URl
                if ("array".equalsIgnoreCase(type)) {
                    List<String> selectedDocumentsStoreIds = getSelectedDocumentsStoreIds(doc.getCoreSession(), selectedDocumentIds);
                    value = selectedDocumentsStoreIds;
                    if (CollectionUtils.isEmpty(selectedDocumentsStoreIds))
                        value = List.of("store:" + doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH));
                } else {
                    value = "store:" + doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
                }
            }
            paramsMap.put(name, value);
        });
        return paramsMap;
    }

    private List<String> getSelectedDocumentsStoreIds(CoreSession coreSession, List<String> selectedDocumentIds) {
        if (CollectionUtils.isNotEmpty(selectedDocumentIds)) {
            return selectedDocumentIds
                    .stream()
                    .map(docId -> coreSession.getDocument(new IdRef(docId)))
                    .filter(RunInteractionServiceImpl::blobIsSupportedMimeType)
                    .map(doc -> uploadFileAndSetStoreId(doc, coreSession))
                    .map(docWithStoreId -> "store:" + docWithStoreId.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH))
                    .collect(Collectors.toList());
        }
        return null;
    }

    private Map<String, Object> buildParametersForFolder(List<DocumentModel> childrenDocsWithStoreId, List<InteractionParameter> parameters) {
        Map<String, Object> paramsMap = new HashMap<>();
        parameters.forEach(param -> {
            String name = param.getName();
            Object value = param.getValue();
            String type = param.getType();
            String format = param.getFormat();
            boolean isDocumentOrMediaFormat = "document".equalsIgnoreCase(format) || "media".equalsIgnoreCase(format);
            if (isDocumentOrMediaFormat && "array".equalsIgnoreCase(type)) {
                value = childrenDocsWithStoreId.stream()
                        .filter(doc -> doc.hasFacet(RESULTS_FACET))
                        .map(doc -> "store:" + doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH))
                        .collect(Collectors.toList());
            }
            paramsMap.put(name, value);
        });
        return paramsMap;
    }

    // Not used for now as Composable does not process links, will be used in the future or for media type.
    private String getSignedS3Url(DocumentModel doc) {
        Property blobProperty = doc.getProperty(FILE_CONTENT);
        ManagedBlob blob = (ManagedBlob) blobProperty.getValue();
        CloudStorageFileShare cloudStorageFileShare = Framework.getService(CloudStorageFileShare.class);
        return cloudStorageFileShare.getPublicRemoteUri(blob).toString();
    }
}
