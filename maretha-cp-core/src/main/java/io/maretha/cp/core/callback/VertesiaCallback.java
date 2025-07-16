package io.maretha.cp.core.callback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.services.RunInteractionService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.runtime.api.Framework;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

@Path("/vertesia-callback")
@WebObject(type = "vertesia-callback")
public class VertesiaCallback extends ModuleRoot {

    public static final String VERTESIA_CALLBACK_API_KEY = "vertesia-callback-key";
    protected static final Log log = LogFactory.getLog(VertesiaCallback.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RunInteractionService runInteractionService = Framework.getService(RunInteractionService.class);

    @POST
    public Object doPost(@Context HttpServletRequest request,
                         @QueryParam("documentId") String documentId,
                         @QueryParam("interactionId") String interactionId,
                         @QueryParam("metadataOverride") String metadataOverride,
                         @QueryParam("runId") String runId,
                         @QueryParam("apiKey") String apiKey) {

        if (StringUtils.isEmpty(apiKey) || !VERTESIA_CALLBACK_API_KEY.equals(apiKey)) {
            log.warn("Unauthorized attempt with invalid API key");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        log.debug("callback documentId: " + documentId);

        JsonNode jsonPayload;
        try (InputStream in = request.getInputStream()) {
            jsonPayload = objectMapper.readTree(in);
        } catch (IOException e) {
            log.error("Failed to parse JSON input", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid JSON format").build();
        }

        log.debug("Received JSON payload: " + jsonPayload.toPrettyString());

        runInteractionService.validateAndSaveMetadata(jsonPayload.get("data"), documentId, Boolean.parseBoolean(metadataOverride));

        return Response.ok(documentId).build();
    }
}
