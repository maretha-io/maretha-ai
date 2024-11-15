package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.maretha.cp.core.models.InteractionParameter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import java.util.*;
import java.util.stream.Collectors;

import static io.maretha.cp.core.services.RepositoryService.*;

public class RunInteractionServiceImpl implements RunInteractionService {

    private final Log log = LogFactory.getLog(RunInteractionServiceImpl.class);
    private final APIService apiService = Framework.getService(APIService.class);

    @Override
    public JsonNode triggerInteraction(DocumentModel doc, CoreSession session, String interactionId, List<InteractionParameter> parameters) {
        if (doc.isFolder()) {
            List<DocumentModel> childrenDocs = getChildrenDocuments(session, doc);
            List<DocumentModel> childrenDocsWithStoreId = uploadFilesAndSetStoreId(session, childrenDocs);
            Map<String, Object> paramsMap = buildParametersForFolder(childrenDocsWithStoreId, parameters);
            return apiService.getInteractionResult(interactionId, paramsMap);
        } else {
            doc = uploadFileAndGetStoreId(doc, session);
            Map<String, Object> paramsMap = buildParameters(doc, parameters);
            return apiService.getInteractionResult(interactionId, paramsMap);
        }
    }

    private List<DocumentModel> uploadFilesAndSetStoreId(CoreSession session, List<DocumentModel> docs) {
        return docs.stream()
                .map(childrenDoc -> {
                    boolean isSupportedMimeType = blobIsSupportedMimeType(childrenDoc);
                    if (isSupportedMimeType) {
                        return uploadFileAndGetStoreId(childrenDoc, session); //TODO heavy operation, should be executed in worker
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public DocumentModel uploadFileAndGetStoreId(DocumentModel doc, CoreSession session) {
        boolean isSupportedMimeType = blobIsSupportedMimeType(doc);
        if (!isSupportedMimeType) {
            throw new NuxeoException("This file:content is not supported");
        }
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
                    value = getSelectedDocumentsStoreIds(doc.getCoreSession(), selectedDocumentIds);
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
            List<DocumentModel> selectedDocuments = selectedDocumentIds
                    .stream()
                    .map(docId -> coreSession.getDocument(new IdRef(docId)))
                    .collect(Collectors.toList());
            List<DocumentModel> selectedDocumentsWithStoreId = uploadFilesAndSetStoreId(coreSession, selectedDocuments);
            return selectedDocumentsWithStoreId
                    .stream().map(docWithStoreId -> "store:" + docWithStoreId.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
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
                        .map(doc -> "store:" + doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH))
                        .collect(Collectors.toList());
            }
            paramsMap.put(name, value);
        });
        return paramsMap;
    }

    private boolean blobIsSupportedMimeType(DocumentModel doc) {
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

    private String getSupportedMimeTypes() {
        return Framework.getProperty("cp.supported.mime-types", "text/plain,application/pdf");
    }

    // Not used for now as Composable does not process links, will be used in the future or for media type.
    private String getSignedS3Url(DocumentModel doc) {
        Property blobProperty = doc.getProperty(FILE_CONTENT);
        ManagedBlob blob = (ManagedBlob) blobProperty.getValue();
        CloudStorageFileShare cloudStorageFileShare = Framework.getService(CloudStorageFileShare.class);
        return cloudStorageFileShare.getPublicRemoteUri(blob).toString();
    }
}
