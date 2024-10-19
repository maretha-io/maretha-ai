package io.maretha.cp.core;

import org.junit.Test;
import org.junit.runner.RunWith;
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
import java.util.Map;

import static io.maretha.cp.core.services.RepositoryService.SETTINGS_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_DOCUMENT_TYPE;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_SCHEMA_NAME;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_DOCUMENT_TYPE;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_FACET;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_SCHEMA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(FeaturesRunner.class)
@Features({CoreFeature.class, PlatformFeature.class, TransactionalFeature.class, CoreBulkFeature.class, MockitoFeature.class, AutomationFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({"org.nuxeo.ecm.core.schema", "org.nuxeo.ecm.platform.types"})
@Deploy({"org.nuxeo.ecm.platform.content.template", "org.nuxeo.ecm.platform.test"})
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class TestCustomDocumentSchemas {

    @Inject
    protected CoreSession session;

    @Test
    public void shouldCreateInteractionSettingsDocument() {
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

        DocumentModel doc = session.createDocumentModel("/", "testDocument", SETTINGS_DOCUMENT_TYPE);
        doc.setPropertyValue(SETTINGS_SCHEMA_PATH, (Serializable) interactions);
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        session.save();
        assertNotNull(doc);

        // Fetch the document to check if it was created correctly
        DocumentModel fetchedDoc = session.getDocument(doc.getRef());
        assertNotNull(fetchedDoc);
        assertTrue(fetchedDoc.hasSchema(SETTINGS_SCHEMA_NAME));
    }

    @Test
    public void shouldAddInteractionResultsFacetToDocument() {
        // Create the interaction objects
        List<HashMap<String, Object>> interactions = new ArrayList<>();

        // First interaction
        HashMap<String, Object> interaction1 = new HashMap<>();
        interaction1.put("id", "65f460c0fbdcc28ee99b65c9");
        interactions.add(interaction1);

        HashMap<String, Object> interaction2 = new HashMap<>();
        interaction2.put("id", "65f460c0fbdcc28ee99b65c8");
        interactions.add(interaction2);

        DocumentModel doc = session.createDocumentModel("/", "testDocument", "File");
        doc = session.createDocument(doc);
        doc.addFacet(RESULTS_FACET);
        doc.setPropertyValue(RESULTS_SCHEMA_PATH, (Serializable) interactions);
        doc = session.saveDocument(doc);
        session.save();
        assertNotNull(doc);

        // Fetch the document to check if it was created correctly
        DocumentModel fetchedDoc = session.getDocument(doc.getRef());
        assertNotNull(fetchedDoc);
        assertTrue(fetchedDoc.hasSchema(RESULTS_SCHEMA));
    }


    @Test
    public void shouldCreateInteractionResponseDocument() {
        // Create the interaction objects
        List<HashMap<String, Object>> interactions = new ArrayList<>();

        List<Map<String, Serializable>> parameters = new ArrayList<>();

        // Create the first parameter map
        Map<String, Serializable> param1 = new HashMap<>();
        param1.put("name", "question");
        param1.put("type", "string");
        param1.put("value", "What is your name?");
        param1.put("format", "");
        parameters.add(param1);  // Add to the parameters list

        // Create the second parameter map
        Map<String, Serializable> param2 = new HashMap<>();
        param2.put("name", "response");
        param2.put("type", "string");
        param2.put("value", "My name is John.");
        param2.put("format", "");
        parameters.add(param2);  // Add to the parameters list

        // First interaction
        HashMap<String, Object> interaction1 = new HashMap<>();
        interaction1.put("id", "random-id-1");
        interaction1.put("interactionId", "interaction-1");
        interaction1.put("documentId", "document-id-1");
        interaction1.put("response", "This is a response");
        interaction1.put("name", "Name 1");
        interaction1.put("status", "Completed");
        interaction1.put("createdAt", null);
        interaction1.put("createdBy", "User1");
        interaction1.put("parameters", parameters);
        interactions.add(interaction1);

        HashMap<String, Object> interaction2 = new HashMap<>();
        interaction2.put("id", "random-id-2");
        interaction2.put("interactionId", "interaction-2");
        interaction2.put("documentId", "document-id-2");
        interaction2.put("response", "This is another response");
        interaction2.put("name", "Name 2");
        interaction2.put("status", "In Progress");
        interaction2.put("createdAt", null);
        interaction2.put("createdBy", "User2");
        interaction2.put("parameters", parameters);
        interactions.add(interaction2);

        DocumentModel doc = session.createDocumentModel("/", "InteractionResults", RESULTS_DOCUMENT_TYPE);
        doc = session.createDocument(doc);
        doc.setPropertyValue(RESULTS_SCHEMA_PATH, (Serializable) interactions);
        doc = session.saveDocument(doc);
        assertNotNull(doc);
        session.save();

        // Fetch the document to check if it was created correctly
        DocumentModel fetchedDoc = session.getDocument(doc.getRef());
        assertNotNull(fetchedDoc);
        assertTrue(fetchedDoc.hasSchema(RESULTS_SCHEMA));
        assertEquals(interactions, doc.getPropertyValue(RESULTS_SCHEMA_PATH));
    }

    @Test
    public void testDocumentCreation() throws Exception {
        // Step 1: Create a document of type "File"
        DocumentModel doc = session.createDocumentModel("/", "testFile", "File");

        // Step 2: Set the title and description
        doc.setPropertyValue("dc:title", "Test Document");
        doc.setPropertyValue("dc:description", "This is a test description");

        // Step 3: Create and save the document in the repository
        doc = session.createDocument(doc);
        session.save();  // Ensure changes are committed

        // Step 4: Verify the document was created correctly
        DocumentModel createdDoc = session.getDocument(doc.getRef());

        // Assert that the document is not null
        assertNotNull(createdDoc);

        // Assert that the document title and description were set correctly
        assertEquals("Test Document", createdDoc.getTitle());
        assertEquals("This is a test description", createdDoc.getPropertyValue("dc:description"));

        // Optionally, verify that the document is of type "File"
        assertEquals("File", createdDoc.getType());
    }

}
