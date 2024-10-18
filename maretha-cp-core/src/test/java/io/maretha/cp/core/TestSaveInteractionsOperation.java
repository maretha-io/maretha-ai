package io.maretha.cp.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.maretha.cp.core.services.LocalSettingsService.CPS_INTERACTIONS_PATH;
import static io.maretha.cp.core.services.LocalSettingsService.CP_SETTINGS_DOCUMENT_TYPE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(FeaturesRunner.class)
@Features({CoreFeature.class, PlatformFeature.class, TransactionalFeature.class, CoreBulkFeature.class, MockitoFeature.class, AutomationFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({"org.nuxeo.ecm.core.schema", "org.nuxeo.ecm.platform.types"})
@Deploy({"org.nuxeo.ecm.platform.content.template", "org.nuxeo.ecm.platform.test"})
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class TestSaveInteractionsOperation {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Test
    public void shouldCreateCPSettingsDocument() {
        // Create the interaction objects
        List<HashMap<String, Object>> interactions = new ArrayList<>();

        // First interaction
        HashMap<String, Object> interaction1 = new HashMap<>();
        interaction1.put("id", "65f460c0fbdcc28ee99b65c9");
        interaction1.put("description", "Extracts metadata from a given text");
        interaction1.put("name", "Metadata Extraction");
        interaction1.put("status", "draft");
        interaction1.put("updatedAt", "2024-04-24T18:16:13.492Z");
        interaction1.put("activeOnDocumentTypes", new ArrayList<String>());
        interaction1.put("active", true);
        interactions.add(interaction1);

        HashMap<String, Object> interaction2 = new HashMap<>();
        interaction2.put("id", "65f460c0fbdcc28ee99b65c8");
        interaction2.put("description", "Extracts metadata from a given text");
        interaction2.put("name", "Metadata Extraction");
        interaction2.put("status", "draft");
        interaction2.put("activeOnDocumentTypes", null);
        interaction2.put("active", false);
        interactions.add(interaction2);

        DocumentModel doc = session.createDocumentModel("/", "testDocument", CP_SETTINGS_DOCUMENT_TYPE);
        doc.setPropertyValue(CPS_INTERACTIONS_PATH, (Serializable) interactions);
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        session.save();
        assertNotNull(doc);

        // Fetch the document to check if it was created correctly
        DocumentModel fetchedDoc = session.getDocument(doc.getRef());
        assertNotNull(fetchedDoc);
        assertTrue(fetchedDoc.hasSchema("interactions"));
    }
}
