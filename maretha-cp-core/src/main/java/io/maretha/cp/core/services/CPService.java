package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.json.JSONException;
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
    private final String API_URL = Framework.getProperty("cp.api.url", "https://api.becomposable.com/api/v1");
    private final String AUTH_TOKEN = Framework.getProperty("cp.auth.token");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Interaction> getInteractions() {
        String url = API_URL + "/interactions";
        String interactions = getRequest(AUTH_TOKEN, url);
        try {
            List<Interaction> cpInteractions = objectMapper.readValue(interactions, new TypeReference<>() {
            });
            return cpInteractions.stream().peek(interaction -> {
                List<InteractionParameter> interactionParameters = getInteractionParameters(interaction.getId());
                interaction.setParameters(interactionParameters);
            }).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<InteractionParameter> getInteractionParameters(String interactionId) {
        String url = API_URL + "/interactions/" + interactionId;
        String interactionParametersResponse = getRequest(AUTH_TOKEN, url);
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
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonNode getInteractionResult(String interaction, Map<String, String> data) {
        boolean stream = false;
        String dataParameters = "";
        try {
            dataParameters = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        String jsonBody = "{\n" +
                "    \"interaction\": \"" + interaction + "\",\n" +
                "    \"data\": " + dataParameters + ",\n" +
                "    \"stream\": " + stream + "\n" +
                "}";
        String url = API_URL + "/runs";
        String response = postRequest(AUTH_TOKEN, url, jsonBody);
        try {
            return objectMapper.readTree(response).get("result");
        } catch (JsonProcessingException e) {
            throw new NuxeoException(e); //TODO ADD logging
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
            throw new NuxeoException(e);
        }
    }
}
