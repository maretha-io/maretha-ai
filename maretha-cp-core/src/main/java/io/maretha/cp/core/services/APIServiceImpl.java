package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class APIServiceImpl implements APIService {
    public final static String API_URL = "vertesia.apiUrl";
    public final static String UPLOAD_API_URL = "vertesia.uploadUrl";
    public final static String AUTH_TOKEN = "vertesia.authToken";
    public final static String EMBEDDING_MODEL = "vertesia.embeddingModel";
    public final static String ENVIRONMENT_ID = "vertesia.environmentId";
    public final static String PROJECT_ID = "vertesia.projectId";
    public static final int TOKEN_TTL = 60 * 30;
    public static final int EMBEDDINGS_TTL = 60 * 60 * 24;
    public static final int VERTESIA_SETTINGS_TTL = 60 * 60;
    public static final String VERTESIA_SETTINGS_KV = "kvs_vertesiaSettings";
    public static final String EMBEDDINGS_KV = "kvs_embeddings";
    private final Log log = LogFactory.getLog(APIServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private final RetryConfig config = RetryConfig.custom()
            .maxAttempts(10) // Maximum number of attempts
            .waitDuration(Duration.ofSeconds(30)) // Wait 10 seconds between attempts
            .retryExceptions(Exception.class) // Retry on all exceptions
            .build();
    private final RetryRegistry registry = RetryRegistry.of(config);
    private final Retry retry = registry.retry("httpRetry");

    @Override
    public List<Interaction> getInteractions() {
        try {
            String url = getSetting(API_URL) + "/api/v1/interactions";
            String interactions = getRequest(getBearerToken(), url);
            List<Interaction> cpInteractions = objectMapper.readValue(interactions, new TypeReference<>() {
            });
            return cpInteractions.stream().peek(interaction -> {
                List<InteractionParameter> interactionParameters = getInteractionParameters(interaction.getId());
                interaction.setParameters(interactionParameters);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed request", e);
        }
        return Collections.emptyList();
    }


    @Override
    public String getInteractionResult(String interaction, Map<String, Object> data, boolean stream) {
        String url = getSetting(API_URL) + "/api/v1/runs";
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
        String bearerToken = getBearerToken();
        String interactionResponse = postRequest(bearerToken, url, jsonBody, false);

        if (stream) {
            try {
                String id = objectMapper.readTree(interactionResponse).get("id").asText();
                return url + "/" + id + "/stream?access_token=" + bearerToken;
            } catch (JsonProcessingException e) {
                throw new NuxeoException(e);
            }
        }
        return interactionResponse;
    }

    @Override
    public String getInteractionResultWithResultSchema(String interaction, Map<String, Object> data, String resultSchema, boolean stream) {
        String url = getSetting(API_URL) + "/api/v1/runs";
        String dataParameters = "";
        try {
            dataParameters = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new NuxeoException(e);
        }

        String jsonBody = "{\n" +
                "    \"interaction\": \"" + interaction + "\",\n" +
                "    \"data\": " + dataParameters + ",\n" +
                "    \"result_schema\": " + resultSchema + ",\n" +
                "    \"stream\": " + stream + "\n" +
                "}";
        String bearerToken = getBearerToken();
        String interactionResponse = postRequest(bearerToken, url, jsonBody, false);

        if (stream) {
            try {
                String id = objectMapper.readTree(interactionResponse).get("id").asText();
                return url + "/" + id + "/stream?access_token=" + bearerToken;
            } catch (JsonProcessingException e) {
                throw new NuxeoException(e);
            }
        }
        return interactionResponse;
    }

    @Override
    public String getBearerToken() {
        KeyValueStore kvStore = Framework.getService(KeyValueService.class).getKeyValueStore(VERTESIA_SETTINGS_KV);
        String token = kvStore.getString(AUTH_TOKEN);
        if (StringUtils.isNotEmpty(token)) {
            return token;
        }
        String response = getRequest(getSetting(AUTH_TOKEN), getSetting(API_URL) + "/auth/token");
        JSONObject jsonObject = new JSONObject(response);
        token = jsonObject.getString("token");
        if (StringUtils.isNotEmpty(token)) {
            kvStore.put(AUTH_TOKEN, token, TOKEN_TTL);
        }
        return token;
    }

    @Override
    public String uploadFileToStore(Blob file) {
        log.debug(String.format("uploadFileToStore: fileName:[%s], digest:[%s]", file.getFilename(), file.getDigest()));

        String bearerToken = getBearerToken();
        String uploadUrlApi = getSetting(UPLOAD_API_URL) + "/api/v1/objects/upload-url";
        String filename = file.getFilename();
        String mimeType = file.getMimeType();
        String bodyJson = "{" +
                "    \"name\": \"" + filename + "\"," +
                "    \"mime_type\": \"" + mimeType + "\"" +
                "}";
        String uploadUrlResponse = postRequest(bearerToken, uploadUrlApi, bodyJson, true);
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

        String uploadedResponse = postRequest(bearerToken, getSetting(UPLOAD_API_URL) + "/api/v1/objects", bodyJsonObject, true);
        JSONObject uploadedResponseObject = new JSONObject(uploadedResponse);
        return uploadedResponseObject.getString("id");
    }

    @Override
    public String uploadS3FileToStore(Blob file, String s3Path) {
        String bearerToken = getBearerToken();

        String bodyJsonObject = "{\n" +
                "  \"name\": \"" + file.getFilename() + "\",\n" +
                "  \"content\": {\n" +
                "    \"source\": \"" + s3Path + "\",\n" +
                "    \"name\": \"" + file.getFilename() + "\",\n" +
                "    \"type\": \"" + file.getMimeType() + "\"\n" +
                "  }\n" +
                "}";

        log.debug(String.format("uploadS3FileToStore: fileName:[%s], digest:[%s], body:%s", file.getFilename(), file.getDigest(), bodyJsonObject));
        String uploadedResponse = postRequest(bearerToken, getSetting(UPLOAD_API_URL) + "/api/v1/objects", bodyJsonObject, true);
        JSONObject uploadedResponseObject = new JSONObject(uploadedResponse);
        return uploadedResponseObject.getString("id");
    }

    @Override
    public double[] textToEmbeddings(String queryText) {
        KeyValueStore kvStore = Framework.getService(KeyValueService.class).getKeyValueStore(EMBEDDINGS_KV);
        try {
            byte[] raw = kvStore.get(queryText);
            if (raw != null) {
                return objectMapper.readValue(raw, double[].class);
            }
        } catch (Exception e) {
            log.error("Failed to get embeddings from kvStore", e);
        }

        String bearerToken = getBearerToken();
        String requestBody = "{" +
                "    \"model\": \"" + getSetting(EMBEDDING_MODEL) + "\"," +
                "    \"text\": \"" + queryText + "\"" +
                "}";
        String response = postRequest(bearerToken, getSetting(API_URL) + "/api/v1/environments/" + getSetting(ENVIRONMENT_ID) + "/embeddings", requestBody, true);
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode embeddings = rootNode.path("values");
            double[] embeddingsAsDoubles = objectMapper.convertValue(embeddings, double[].class);
            kvStore.put(queryText, objectMapper.writeValueAsBytes(embeddingsAsDoubles), EMBEDDINGS_TTL);
            return embeddingsAsDoubles;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double[] getDocumentEmbeddings(String docId, String type) {
        String bearerToken = getBearerToken();
        String response = getRequest(bearerToken, getSetting(UPLOAD_API_URL) + "/api/v1/objects/" + docId + "?select=embeddings");
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode embeddings = rootNode.path("embeddings").path(type).path("values");
            return objectMapper.convertValue(embeddings, double[].class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Double> search(String vectorQuery, String type, int limit) {
        log.debug("Searching by type: " + type + " and query: " + vectorQuery);
        Map<String, Double> docIdScoreMap = new HashMap<>();
        String requestBody = String.format(
                "{\n" +
                        "  \"limit\": %s,\n" +
                        "  \"offset\": 0,\n" +
                        "  \"query\": {\n" +
                        "    \"vector\": {\n" +
                        "      \"values\": %s,\n" +
                        "      \"type\": \"%s\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", limit, vectorQuery, type);
        try {
            String response = postRequest(getBearerToken(), getSetting(UPLOAD_API_URL) + "/api/v1/objects/search", requestBody, false);
            objectMapper.readTree(response).forEach(obj -> {
                String id = obj.get("id").asText();
                double score = obj.get("score").asDouble();
                docIdScoreMap.put(id, score);
            });
        } catch (Exception e) {
            log.error(e);
        }
        return docIdScoreMap;
    }

    @Override
    public Map<String, Double> searchSimilarity(String storeId, String type, int limit) {
        log.debug("Searching similarity by type: " + type + " and storeId: " + storeId);
        Map<String, Double> docIdScoreMap = new HashMap<>();
        String requestBody = String.format(
                "{\n" +
                        "  \"limit\": %s,\n" +
                        "  \"offset\": 0,\n" +
                        "  \"query\": {\n" +
                        "    \"similarTo\": \"%s\",\n" +
                        "    \"vector\": {\n" +
                        "      \"objectId\": \"%s\",\n" +
                        "      \"type\": \"%s\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", limit, storeId, storeId, type);
        try {
            String response = postRequest(getBearerToken(), getSetting(UPLOAD_API_URL) + "/api/v1/objects/search", requestBody, false);
            objectMapper.readTree(response).forEach(obj -> {
                String id = obj.get("id").asText();
                double score = obj.get("score").asDouble();
                docIdScoreMap.put(id, score);
            });
        } catch (Exception e) {
            log.error(e);
        }
        return docIdScoreMap;
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

    private String postRequest(String apiToken, String url, String jsonBody, boolean withRetry) {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiToken)
                .post(body)
                .build();
        if (withRetry) {
            return getResponseWithRetry(request);
        }
        return executeHttpRequest(request);
    }

    private String getResponseWithRetry(Request request) {
        String responseBody = Retry.decorateSupplier(retry, () -> executeHttpRequest(request)).get();
        log.debug(responseBody);
        return responseBody;
    }

    private String getResponseWithStatusValidation(Request request, boolean withRetry) {
        if (withRetry) {
            String responseBody = Retry.decorateSupplier(retry, () -> {
                String response = executeHttpRequest(request);
                String status = new JSONObject(response).getString("status");
                if (!status.equals("completed")) {
                    throw new RuntimeException("Status is not completed, but:" + status);
                }
                return status;
            }).get();
            log.debug(responseBody);
            return responseBody;
        } else {
            String responseBody = executeHttpRequest(request);
            String status = new JSONObject(responseBody).getString("status");
            if (!status.equals("completed")) {
                throw new RuntimeException("Status is not completed, but:" + status);
            }
            log.debug(responseBody);
            return status;
        }

    }

    private String executeHttpRequest(Request request) {
        log.debug("Executing HttpRequest for URL: " + request.url());
        try (Response response = client.newCall(request).execute()) {
            String responseString = response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to get a successful from: " + request.url() + " response: " + responseString);
            }
            return responseString;
        } catch (Exception e) {
            // Handle exception (optional)
            log.error("Request failed: " + e.getMessage());
            throw new RuntimeException(e); // Rethrow to trigger retry
        }
    }

    private List<InteractionParameter> getInteractionParameters(String interactionId) {
        String url = getSetting(API_URL) + "/api/v1/interactions/" + interactionId;
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
                            String description = value.has("description") ? value.get("description").asText() : "";
                            InteractionParameter parameter = InteractionParameter.builder()
                                    .name(name)
                                    .type(type)
                                    .format(format)
                                    .description(description)
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

    public String checkProcessingStatusCompleted(String storeId, boolean withRetry) {
        String bearerToken = getBearerToken();
        Request request = new Request.Builder().url(getSetting(UPLOAD_API_URL) + "/api/v1/objects/" + storeId)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + bearerToken)
                .get()
                .build();
        if (withRetry) {
            return getResponseWithStatusValidation(request, withRetry);
        } else {
            return getResponseWithStatusValidation(request, false);
        }
    }

    private String getSetting(String name) {
        final RepositoryService repositoryService = Framework.getService(RepositoryService.class);
        String value = null;
        switch (name) {
            case API_URL:
                value = repositoryService.getSettingsProperty(API_URL, "https://api.vertesia.io");
                break;
            case UPLOAD_API_URL:
                value = repositoryService.getSettingsProperty(UPLOAD_API_URL, "https://zeno-server-production.api.vertesia.io");
                break;
            case AUTH_TOKEN:
                value = repositoryService.getSettingsProperty(AUTH_TOKEN, null);
                break;
            case PROJECT_ID:
                value = repositoryService.getSettingsProperty(PROJECT_ID, null);
                break;
            case ENVIRONMENT_ID:
                value = repositoryService.getSettingsProperty(ENVIRONMENT_ID, null);
                break;
            case EMBEDDING_MODEL:
                value = repositoryService.getSettingsProperty(EMBEDDING_MODEL, "amazon.titan-embed-text-v2:0");
                break;
        }
        return value;
    }

}
