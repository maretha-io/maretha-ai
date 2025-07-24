/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *
 */

package io.maretha.cp.core.callback;

import com.fasterxml.jackson.databind.JsonNode;
import io.maretha.cp.core.services.RunInteractionService;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;

import javax.inject.Inject;
import java.io.IOException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, RestServerFeature.class, RuntimeFeature.class, PlatformFeature.class, MockitoFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({
        "io.maretha.cp.core.maretha-cp-core",
        "org.nuxeo.ecm.platform.oauth"
})
public class TestVertesiaCallback {

    public static final String BASE_URL = "http://localhost";

    @Inject
    protected ServletContainerFeature servletContainerFeature;

    @Mock
    @RuntimeService
    RunInteractionService runInteractionService;

    @Test
    public void testReceiveCallback() throws IOException {
        int port = servletContainerFeature.getPort();
        String url = BASE_URL + ":" + port + "/vertesia-callback?documentId=123&runId=abc&apiKey=" + VertesiaCallback.VERTESIA_CALLBACK_API_KEY;

        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(url);

        String jsonPayload = "{\n" +
                "          \"event\" : \"workflow_finished\",\n" +
                "          \"data\" : {\n" +
                "            \"interaction_run_id\" : \"686d5cf79e02af71b2ec7b32\",\n" +
                "            \"workflow_id\" : \"generation:ExecuteInteractionWorkflow:DynamicMetadata:jief03ard\",\n" +
                "            \"workflow_run_id\" : \"0197eb33-271e-7f2d-9dbd-1f0c730619fd\",\n" +
                "            \"status\" : \"completed\",\n" +
                "            \"result\" : {\n" +
                "              \"result\" : {\n" +
                "                \"dc:description\" : \"This is a picture.\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }";
        StringEntity entity = new StringEntity(jsonPayload);
        entity.setContentType("application/json");
        post.setEntity(entity);

        doNothing().when(runInteractionService)
                .validateAndSaveMetadata(any(JsonNode.class), anyString(), anyBoolean());

        HttpResponse response = client.execute(post);

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

        /*
        {
          "event" : "workflow_finished",
          "data" : {
            "interaction_run_id" : "686d5cf79e02af71b2ec7b32",
            "workflow_id" : "generation:ExecuteInteractionWorkflow:DynamicMetadata:jief03ard",
            "workflow_run_id" : "0197eb33-271e-7f2d-9dbd-1f0c730619fd",
            "status" : "completed",
            "result" : {
              "result" : {
                "dc:description" : "This is a picture."
              }
            }
          }
        }

     */

}
