package io.maretha.cp.core.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.client.ESRestClient;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.client.Request;
import org.opensearch.client.Response;

import java.io.IOException;
import java.util.Map;


@Operation(id = GetEsInfoOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get ES Info", description = "Get ES Info")
public class GetEsInfoOperation {

    public static final String ID = "GetEsInfoOperation";
    private final Log log = LogFactory.getLog(GetEsInfoOperation.class);
    private final ElasticSearchAdmin elasticSearchAdmin = Framework.getService(ElasticSearchAdmin.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Context
    protected CoreSession session;

    @OperationMethod
    public Map<String, JsonNode> run() throws IOException {
        if (!session.getPrincipal().isAdministrator()) {
            return null;
        }
        ESRestClient esClient = (ESRestClient) elasticSearchAdmin.getClient();
        Response versionResponse = esClient.performRequestWithTracing(new Request("GET", "/"));
        Response pluginsResponse = esClient.performRequestWithTracing(new Request("GET", "_cat/plugins?v"));
        Response clusterSettingsResponse = esClient.performRequestWithTracing(new Request("GET", "_cluster/settings?include_defaults=true"));
        JsonNode version = objectMapper.readTree(versionResponse.getEntity().getContent());
        JsonNode plugins = objectMapper.readTree(pluginsResponse.getEntity().getContent());
        JsonNode clusterSettings = objectMapper.readTree(clusterSettingsResponse.getEntity().getContent());
        return Map.of("version", version, "plugins", plugins, "clusterSettings", clusterSettings);
    }


}
