package io.maretha.cp.core.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.services.CPServiceInterface;
import io.maretha.cp.core.services.CloudStorageFileShare;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.maretha.cp.core.listeners.DocumentChangeListener.FILE_CONTENT;

/**
 *
 */
@Operation(id = RuntInteractionOperation.ID, category = Constants.CAT_DOCUMENT, label = "Run Interactions", description = "")
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

    @OperationMethod
    //TODO Extract logic in Service Q. How to handle 1000 child documents?
    public JsonNode run(DocumentModel doc) {
        List<InteractionParameter> parameters = objectMapper
                .convertValue(data, objectMapper.getTypeFactory().constructCollectionType(List.class, InteractionParameter.class));
        if (doc.isFolder()) {
            DocumentModelList childrenDocs = session.getChildren(doc.getRef());
            List<String> childrenSignedDocuments = getChildrenSignedDocuments(childrenDocs);
            Map<String, Object> paramsMap = buildParametersForFolder(childrenSignedDocuments, parameters);
            return cpServiceInterface.getInteractionResult(interactionId, paramsMap);
        }
        Map<String, Object> paramsMap = buildParameters(doc, parameters);
        return cpServiceInterface.getInteractionResult(interactionId, paramsMap);
    }

    private List<String> getChildrenSignedDocuments(DocumentModelList childrenDocs) {
        return childrenDocs.stream().map(childrenDoc -> {
            boolean isSupportedMimeType = blobIsSupportedMimeType(childrenDoc);
            if (isSupportedMimeType) {
                return getSignedS3Url(childrenDoc);
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
                String signedS3Url = getSignedS3Url(doc);
                if ("array".equalsIgnoreCase(type)) {
                    value = List.of(signedS3Url);
                } else {
                    value = signedS3Url;
                }
            }
            paramsMap.put(name, value);
        });
        return paramsMap;
    }

    private Map<String, Object> buildParametersForFolder(List<String> childrenSignedDocuments, List<InteractionParameter> parameters) {
        Map<String, Object> paramsMap = new HashMap<>();
        parameters.forEach(param -> {
            String name = param.getName();
            Object value = param.getValue();
            String type = param.getType();
            String format = param.getFormat();
            boolean isDocumentOrMediaFormat = "document".equalsIgnoreCase(format) || "media".equalsIgnoreCase(format);
            if (isDocumentOrMediaFormat && "array".equalsIgnoreCase(type)) {
                value = childrenSignedDocuments;
            }
            paramsMap.put(name, value);
        });
        return paramsMap;
    }


    private String getSignedS3Url(DocumentModel doc) {
        Property blobProperty = doc.getProperty(FILE_CONTENT);
        ManagedBlob blob = (ManagedBlob) blobProperty.getValue();
        CloudStorageFileShare cloudStorageFileShare = Framework.getService(CloudStorageFileShare.class);
        return cloudStorageFileShare.getPublicRemoteUri(blob).toString();
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

}
