package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.maretha.cp.core.models.Interaction;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;

import java.util.List;
import java.util.Map;

public interface APIServiceInterface {
    List<Interaction> getInteractions();

    JsonNode getInteractionResult(String request, Map<String, Object> data);

    String getBearerToken();

    String uploadFileToStore(Blob file);

    JSONObject getStoredDocumentText(String storeId);
}
