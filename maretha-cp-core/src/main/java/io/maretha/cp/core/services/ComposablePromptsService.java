package io.maretha.cp.core.services;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;

public class ComposablePromptsService implements ComposablePromptsServiceInterface {
    private static final Log log = LogFactory.getLog(ComposablePromptsService.class);
    String API_URL = Framework.getProperty("cp.api.url", "https://api.composableprompts.com/api/v1");
    String AUTH_TOKEN = Framework.getProperty("cp.auth.token");
    OkHttpClient client = new OkHttpClient();

    @Override
    public String getInteractions() {
        String url = API_URL + "/interactions";
        return getRequest(AUTH_TOKEN, url);
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
}
