package io.maretha.cp.core.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.services.CPServiceInterface;
import io.maretha.cp.core.services.CloudStorageFileShare;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.maretha.cp.core.listeners.DocumentChangeListener.FILE_CONTENT;
import static io.maretha.cp.core.services.RepositoryServiceInterface.RESULTS_FACET;
import static io.maretha.cp.core.services.RepositoryServiceInterface.RESULTS_STORED_DATE_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryServiceInterface.RESULTS_STORE_ID_SCHEMA_PATH;

/**
 *
 */
@Operation(id = RuntInteractionOperation.ID, category = Constants.CAT_DOCUMENT, label = "Run Interactions", description = "Run Interaction")
public class RuntInteractionOperation {

    public static final String ID = "RunInteractionOperation";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CPServiceInterface cpServiceInterface = Framework.getService(CPServiceInterface.class);
    @Context
    protected CoreSession session;
    @Param(name = "data")
    protected List<ObjectNode> data;
    @Param(name = "interactionId")
    protected String interactionId;

    //TODO Extract logic in Service Q. How to handle 1000 child documents?
    @OperationMethod
    public JsonNode run(DocumentModel doc) {
        List<InteractionParameter> parameters = objectMapper
                .convertValue(data, objectMapper.getTypeFactory().constructCollectionType(List.class, InteractionParameter.class));
        if (doc.isFolder()) {
            List<DocumentModel> childrenDocsWithStoreId = getChildrenDocumentsAndUploadFiles(session.getChildren(doc.getRef()));
            Map<String, Object> paramsMap = buildParametersForFolder(childrenDocsWithStoreId, parameters);
            return cpServiceInterface.getInteractionResult(interactionId, paramsMap);
        } else {
            doc = uploadFileAndGetStoreId(doc);
            Map<String, Object> paramsMap = buildParameters(doc, parameters);
            return cpServiceInterface.getInteractionResult(interactionId, paramsMap);
        }
    }

    private DocumentModel uploadFileAndGetStoreId(DocumentModel doc) {
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
            storeId = cpServiceInterface.uploadFileToStore((Blob) doc.getPropertyValue(FILE_CONTENT));
            doc.setPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH, storeId);
            doc.setPropertyValue(RESULTS_STORED_DATE_SCHEMA_PATH, new GregorianCalendar());
            return session.saveDocument(doc);
        }
        return doc;
    }

    private List<DocumentModel> getChildrenDocumentsAndUploadFiles(DocumentModelList childrenDocs) {
        return childrenDocs.stream()
                .filter(doc -> !doc.isTrashed() && !doc.isProxy())
                .map(childrenDoc -> {
                    boolean isSupportedMimeType = blobIsSupportedMimeType(childrenDoc);
                    if (isSupportedMimeType) {
                        return uploadFileAndGetStoreId(childrenDoc);
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private Map<String, Object> buildParameters(DocumentModel doc, List<InteractionParameter> parameters) {
        Map<String, Object> paramsMap = new HashMap<>();
        parameters.forEach(param -> {
            String name = param.getName();
            Object value = param.getValue();
            String type = param.getType();
            String format = param.getFormat();
            boolean isDocumentOrMediaFormat = "document".equalsIgnoreCase(format) || "media".equalsIgnoreCase(format);
            if (isDocumentOrMediaFormat) {
                String storeId = "store:" + doc.getPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH);
                if ("array".equalsIgnoreCase(type)) {
                    value = List.of(storeId);
                } else {
                    value = storeId;
                }
            }
            paramsMap.put(name, value);
        });
        return paramsMap;
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
