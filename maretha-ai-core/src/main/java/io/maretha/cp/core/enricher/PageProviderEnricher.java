package io.maretha.cp.core.enricher;

import com.fasterxml.jackson.core.JsonGenerator;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;

import java.io.IOException;

import static io.maretha.cp.core.search.CustomKnnSearch.RELEVANCE_SCORE;
import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

@Setup(mode = SINGLETON, priority = REFERENCE)
public class PageProviderEnricher extends AbstractJsonEnricher<DocumentModel> {

    public PageProviderEnricher() {
        super(RELEVANCE_SCORE);
    }

    @Override
    public void write(JsonGenerator jg, DocumentModel doc) throws IOException {
        Object value = doc.getContextData(RELEVANCE_SCORE);
        if (value != null) {
            jg.writeObjectField(RELEVANCE_SCORE, value);
        }
    }
}