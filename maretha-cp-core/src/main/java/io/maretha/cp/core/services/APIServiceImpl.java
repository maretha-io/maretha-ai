package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.services.utils.FileUploadAsStream;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class APIServiceImpl implements APIService {
    private final Log log = LogFactory.getLog(APIServiceImpl.class);
    private final String API_URL = Framework.getProperty("cp.api.url", "https://api.becomposable.com");
    private final String UPLOAD_API_URL = Framework.getProperty("cp.upload.url", "https://zeno-server-production.api.becomposable.com");
    private final String AUTH_TOKEN = Framework.getProperty("cp.auth.token");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private final RetryConfig config = RetryConfig.custom()
            .maxAttempts(3) // Maximum number of attempts
            .waitDuration(Duration.ofSeconds(1)) // Wait 1 second between attempts
            .retryExceptions(Exception.class) // Retry on all exceptions
            .build();

    private final RetryRegistry registry = RetryRegistry.of(config);
    private final Retry retry = registry.retry("httpRetry");

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
            return objectMapper.readTree(response);
        } catch (Exception e) {
            return null;
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
        log.debug(String.format("uploadFileToStore: fileName:[%s], digest:[%s]", file.getFilename(), file.getDigest()));

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

        if (StringUtils.isEmpty(etag)) {
            throw new NuxeoException("Failed to upload the file");
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
        return uploadedResponseObject.getString("id");
    }

    private String putFileRequest(String url, Blob file) throws IOException {
        RequestBody requestBody = new FileUploadAsStream().createStreamRequestBody(MediaType.parse("application/octet-stream"), file.getStream());

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
        return getResponseWithRetry(request);
    }

    private String postRequest(String apiToken, String url, String jsonBody) {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiToken)
                .post(body)
                .build();
        return getResponseWithRetry(request);
    }

    private String getResponseWithRetry(Request request) {
        String responseBody = Retry.decorateSupplier(retry, () -> executeHttpRequest(request)).get();
        log.debug(responseBody);
        return responseBody;
    }

    private String executeHttpRequest(Request request) {
        log.debug("Executing HttpRequest for URL: " + request.url());
        try (Response response = client.newCall(request).execute()) {
            String responseString = response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to get a successful response, code: " + response.code());
            }
            return responseString;
        } catch (Exception e) {
            // Handle exception (optional)
            log.error("Request failed: " + e.getMessage());
            throw new RuntimeException(e); // Rethrow to trigger retry
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
                            InteractionParameter parameter = InteractionParameter.builder()
                                    .name(name)
                                    .type(type)
                                    .format(format)
                                    .build();
                            paramsList.add(parameter);
                        });
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get parameter for interactionId: " + interactionId);
        }
        return paramsList;
    }

    public JSONObject getStoredDocumentText(String storeId) {
        String bearerToken = getBearerToken();
        String documentResponse = getRequest(bearerToken, UPLOAD_API_URL + "/api/v1/objects/" + storeId + "/text");
        return new JSONObject(documentResponse);
    }

}
