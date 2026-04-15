/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.search.AbstractSearchClient
 *  org.nuxeo.ecm.core.search.SearchClientFactory
 *  org.nuxeo.runtime.model.ComponentContext
 *  org.nuxeo.runtime.model.DefaultComponent
 */
package org.nuxeo.ecm.core.search.client.opensearch3;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.nuxeo.ecm.core.search.AbstractSearchClient;
import org.nuxeo.ecm.core.search.SearchClientFactory;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintDescriptor;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchSearchClient;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchSearchClientDescriptor;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

public class OpenSearchSearchClientFactory
extends DefaultComponent
implements SearchClientFactory<OpenSearchSearchClient> {
    protected static final String XP_HINT = "hint";
    protected static final String XP_SEARCH_CLIENT = "searchClient";
    protected Map<String, OpenSearchHintQueryBuilder> hints;
    protected Map<String, OpenSearchSearchClient> searchClients;

    public void start(ComponentContext context) {
        this.hints = this.getDescriptors(XP_HINT).stream().map(d -> (OpenSearchHintDescriptor) d).collect(Collectors.toMap(OpenSearchHintDescriptor::getName, OpenSearchHintDescriptor::newInstance));
        this.searchClients = this.getDescriptors(XP_SEARCH_CLIENT).stream().map(d -> (OpenSearchSearchClientDescriptor) d).filter(OpenSearchSearchClientDescriptor::isEnabled).map(descriptor -> new OpenSearchSearchClient((OpenSearchSearchClientDescriptor)descriptor, this.hints)).collect(Collectors.toMap(AbstractSearchClient::getName, Function.identity()));
    }

    public void stop(ComponentContext context) {
        this.hints = null;
        this.searchClients = null;
    }

    public OpenSearchSearchClient getSearchClient(String name) {
        OpenSearchSearchClient client = this.searchClients.get(name);
        if (client == null) {
            throw new IllegalStateException("The search client with name: " + name + " does not exist");
        }
        return client;
    }

    protected Map<String, OpenSearchHintQueryBuilder> getHints() {
        return this.hints;
    }
}

