package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.InteractionParameter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CPService implements CPServiceInterface {
    private final Log log = LogFactory.getLog(CPService.class);
    private final String API_URL = Framework.getProperty("cp.api.url", "https://api.becomposable.com");
    private final String UPLOAD_API_URL = Framework.getProperty("cp.upload.url", "https://zeno-server-production.api.becomposable.com");
    private final String AUTH_TOKEN = Framework.getProperty("cp.auth.token");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public List<Interaction> getInteractions() {
        String url = API_URL + "/api/v1/interactions";
        String interactions = getRequest(getBearerToken(), url);
        try {
            List<Interaction> cpInteractions = objectMapper.readValue(interactions, new TypeReference<>() {
            });
            return cpInteractions.stream().peek(interaction -> {
                List<InteractionParameter> interactionParameters = getInteractionParameters(interaction.getId());
                interaction.setParameters(interactionParameters);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public JsonNode getInteractionResult(String interaction, Map<String, Object> data) {
        String url = API_URL + "/api/v1/runs";
        boolean stream = false;
        String dataParameters = "";
        try {
            dataParameters = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new NuxeoException(e);
        }

        String jsonBody = "{\n" +
                "    \"interaction\": \"" + interaction + "\",\n" +
                "    \"data\": " + dataParameters + ",\n" +
                "    \"stream\": " + stream + "\n" +
                "}";
        String response = postRequest(getBearerToken(), url, jsonBody);
        try {
            return objectMapper.readTree(response).get("result");
        } catch (Exception e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public String getBearerToken() {
        String response = getRequest(AUTH_TOKEN, API_URL + "/auth/token");
        JSONObject jsonObject = new JSONObject(response);
        return jsonObject.getString("token");
    }

    @Override
    public String uploadFileToStore(Blob file) {
        String bearerToken = getBearerToken();
        String uploadUrlApi = UPLOAD_API_URL + "/api/v1/objects/upload-url";
        String filename = file.getFilename();
        String mimeType = file.getMimeType();
        String bodyJson = "{" +
                "    \"name\": \"" + filename + "\"," +
                "    \"mime_type\": \"" + mimeType + "\"" +
                "}";
        String uploadUrlResponse = postRequest(bearerToken, uploadUrlApi, bodyJson);
        JSONObject jsonObject = new JSONObject(uploadUrlResponse);
        String uploadUrl = jsonObject.getString("url");
        String uploadId = jsonObject.getString("id");

        String etag = null;
        try {
            etag = putFileRequest(uploadUrl, file);
        } catch (Exception e) {
            throw new NuxeoException(e);
        }

        String bodyJsonObject = "{\n" +
                "    \"content\": {\n" +
                "        \"source\": \"" + uploadId + "\",\n" +
                "        \"name\": \"" + filename + "\",\n" +
                "        \"type\": \"" + mimeType + "\",\n" +
                "        \"etag\": " + etag + "\n" +
                "    }\n" +
                "}";

        String uploadedResponse = postRequest(bearerToken, UPLOAD_API_URL + "/api/v1/objects", bodyJsonObject);
        JSONObject uploadedResponseObject = new JSONObject(uploadedResponse);
//        String checkDocumentResponse = getRequest(bearerToken, UPLOAD_API_URL + "/api/v1/objects/" + storeId);
//        JSONObject savedDocumentJson = new JSONObject(checkDocumentResponse);
        return uploadedResponseObject.getString("id");
    }

    private String putFileRequest(String url, Blob file) throws IOException {
        byte[] fileBytes = file.getStream().readAllBytes();
        RequestBody requestBody = RequestBody.create(fileBytes, MediaType.parse("application/octet-stream"));
        Request request = new Request.Builder()
                .url(url)
                .put(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            log.debug(response.body().string());
            if (response.isSuccessful()) {
                return response.header("Etag");
            }
            return null;
        } catch (Exception e) {
            throw new NuxeoException(e);
        }
    }

    private String getRequest(String apiToken, String url) {
        Request request = new Request.Builder().url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiToken)
                .get()
                .build();
        return getResponseAsString(request);
    }

    private String postRequest(String apiToken, String url, String jsonBody) {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiToken)
                .post(body)
                .build();
        return getResponseAsString(request);
    }

    private String getResponseAsString(Request request) {
        try (Response response = client.newCall(request).execute()) {
            String responseString = response.body().string();
            log.debug(responseString);
            return responseString;
        } catch (Exception e) {
            throw new NuxeoException(e);
        }
    }

    private List<InteractionParameter> getInteractionParameters(String interactionId) {
        String url = API_URL + "/api/v1/interactions/" + interactionId;
        String interactionParametersResponse = getRequest(getBearerToken(), url);
        List<InteractionParameter> paramsList = new ArrayList<>();
        try {
            JsonNode jsonNode = objectMapper.readTree(interactionParametersResponse);
            JsonNode prompts = jsonNode.get("prompts");
            if (prompts.isArray()) {
                for (JsonNode parameterNode : prompts) {
                    JsonNode params = parameterNode.get("template").get("inputSchema").get("properties");
                    if (params != null) {
                        params.fields().forEachRemaining(field -> {
                            String name = field.getKey();
                            JsonNode value = field.getValue();
                            String type = value.has("type") ? value.get("type").asText() : "";
                            String format = value.has("format") ? value.get("format").asText() : "";
                            paramsList.add(new InteractionParameter(name, type, format, ""));
                        });
                    }
                }
            }
            return paramsList;
        } catch (Exception e) {
            throw new NuxeoException(e);
        }
    }
}
