package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.CpInteraction;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CPService implements CPServiceInterface {
    private static final Log log = LogFactory.getLog(CPService.class);
    String API_URL = Framework.getProperty("cp.api.url", "https://api.composableprompts.com/api/v1");
    String AUTH_TOKEN = Framework.getProperty("cp.auth.token");
    OkHttpClient client = new OkHttpClient();
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<CpInteraction> getInteractions() {
        String url = API_URL + "/interactions";
        String interactions = getRequest(AUTH_TOKEN, url);
        try {
            return objectMapper.readValue(interactions, new TypeReference<List<CpInteraction>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Object> getInteractionParameters(String interactionId) {
        String url = API_URL + "/interactions/" + interactionId;
        String interactionParametersResponse = getRequest(AUTH_TOKEN, url);
        List<Object> paramsList = new ArrayList<>();
        try {
            JsonNode jsonNode = objectMapper.readTree(interactionParametersResponse);
            JsonNode prompts = jsonNode.get("prompts");
            if (prompts.isArray()) {
                for (JsonNode parameterNode : prompts) {
                        Object params = parameterNode.get("template").get("inputSchema").get("properties");
                        if (params != null) paramsList.add(params);
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return paramsList;
    }

    @Override
    public String getInteractionResult(String interaction, Map<String, String> data) {
        // Variables to be used in the JSON string
        boolean stream = false;
        String dataParameters = "";
        // Convert map to JSON string
        try {
            dataParameters = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // Construct the JSON string with variable values
        String jsonBody = "{\n" +
                "    \"interaction\": \"" + interaction + "\",\n" +
                "    \"data\": " + dataParameters + ",\n" +
                "    \"stream\": " + stream + "\n" +
                "}";
        String url = API_URL + "/runs";
        String response = postRequest(AUTH_TOKEN, url, jsonBody);
        try {
            return objectMapper.readTree(response).get("result").toPrettyString();
        } catch (JsonProcessingException e) {
            return e.getMessage();
        }
    }

    private String getRequest(String apiToken, String url) {
        Request request = new Request.Builder().url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiToken)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseString = response.body().string();
            log.debug(responseString);
            return responseString;
        } catch (IOException | JSONException e) {
            // Handle IO or JSON exception
            throw new NuxeoException(e);
        }
    }

    private String postRequest(String apiToken, String url, String jsonBody) {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiToken)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseString = response.body().string();
            log.debug(responseString);
            return responseString;
        } catch (IOException | JSONException e) {
            // Handle IO or JSON exception
            throw new NuxeoException(e);
        }
    }
}
