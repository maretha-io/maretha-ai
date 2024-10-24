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
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.maretha.cp.core.listeners.DocumentChangeListener.FILE_CONTENT;

/**
 *
 */
@Operation(id = RuntInteractionOperation.ID, category = Constants.CAT_DOCUMENT, label = "Run Interactions", description = "")
public class RuntInteractionOperation {

    public static final String ID = "RunInteractionOperation";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Context
    protected CoreSession session;
    @Param(name = "data")
    protected List<ObjectNode> data;
    @Param(name = "interactionId")
    protected String interactionId;

    @OperationMethod
    public JsonNode run(DocumentModel doc) {
        List<InteractionParameter> parameters = objectMapper
                .convertValue(data, objectMapper.getTypeFactory().constructCollectionType(List.class, InteractionParameter.class));
        Map<String, Object> paramsMap = new HashMap<>();
        parameters.forEach(param -> {
            String name = param.getName();
            Object value = param.getValue();
            String type = param.getType();
            if ("[[file:content]]".equalsIgnoreCase((String) value)) {
                Property blobProperty = doc.getProperty(FILE_CONTENT);
                ManagedBlob blob = (ManagedBlob) blobProperty.getValue();
                CloudStorageFileShare cloudStorageFileShare = Framework.getService(CloudStorageFileShare.class);
                String signedS3Url = cloudStorageFileShare.getPublicRemoteUri(blob).toString();
                if (!StringUtils.isEmpty(type) && "array".equalsIgnoreCase(type)) {
                    value = List.of(signedS3Url);
                } else {
                    value = signedS3Url;
                }
            }
            paramsMap.put(name, value);
        });

        return Framework.getService(CPServiceInterface.class).getInteractionResult(interactionId, paramsMap);
    }

}
