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
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.maretha.cp.core.services.RepositoryService.PARAMETER_SCHEMA_ENABLE_DOC_SELECTION;
import static io.maretha.cp.core.services.RepositoryService.PARAMETER_SCHEMA_FORMAT;
import static io.maretha.cp.core.services.RepositoryService.PARAMETER_SCHEMA_IS_MAIN_DOCUMENT;
import static io.maretha.cp.core.services.RepositoryService.PARAMETER_SCHEMA_NAME;
import static io.maretha.cp.core.services.RepositoryService.PARAMETER_SCHEMA_READ_ONLY;
import static io.maretha.cp.core.services.RepositoryService.PARAMETER_SCHEMA_SELECTED_DOC_IDS;
import static io.maretha.cp.core.services.RepositoryService.PARAMETER_SCHEMA_SELECTION_READ_ONLY;
import static io.maretha.cp.core.services.RepositoryService.PARAMETER_SCHEMA_TYPE;
import static io.maretha.cp.core.services.RepositoryService.PARAMETER_SCHEMA_VALUE;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_DOCUMENT_NAME;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORED_DATE_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.RESULTS_STORE_ID_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_ACTIVE;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_ACTIVE_ON_ACTION_MENU;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_ACTIVE_ON_DOCUMENTS;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_ACTIVE_ON_FOLDER;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_CREATED_AT;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_CREATED_BY;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_DESCRIPTION;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_DOCUMENT_ID;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_ID;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_INTERACTION_ID;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_NAME;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_PARAMETERS;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_RESPONSE;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_STATUS;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_UPDATED_AT;
import static io.maretha.cp.core.services.RepositoryService.SCHEMA_VERSION;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_DOCUMENT_NAME;
import static io.maretha.cp.core.services.RepositoryServiceImpl.RESULTS_DOCUMENT_TYPE;
import static io.maretha.cp.core.services.RepositoryServiceImpl.RESULTS_FACET;
import static io.maretha.cp.core.services.RepositoryServiceImpl.RESULTS_SCHEMA;
import static io.maretha.cp.core.services.RepositoryServiceImpl.RESULTS_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryServiceImpl.SETTINGS_DOCUMENT_TYPE;
import static io.maretha.cp.core.services.RepositoryServiceImpl.SETTINGS_SCHEMA_NAME;
import static io.maretha.cp.core.services.RepositoryServiceImpl.SETTINGS_SCHEMA_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(FeaturesRunner.class)
@Features({CoreFeature.class, PlatformFeature.class, TransactionalFeature.class, CoreBulkFeature.class, MockitoFeature.class, AutomationFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({"org.nuxeo.ecm.core.schema", "org.nuxeo.ecm.platform.types"})
@Deploy({"org.nuxeo.ecm.platform.content.template", "org.nuxeo.ecm.platform.test"})
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class DocumentSchemasTest {

    @Inject
    protected CoreSession session;

    @Test
    public void shouldCreateInteractionSettingsDocument() {
        // Create the interaction objects
        List<HashMap<String, Object>> interactions = new ArrayList<>();

        // First interaction
        HashMap<String, Object> interaction1 = new HashMap<>();
        interaction1.put(SCHEMA_ID, "65f460c0fbdcc28ee99b65c9");
        interaction1.put(SCHEMA_NAME, "Metadata Extraction");
        interaction1.put(SCHEMA_STATUS, "draft");
        interaction1.put(SCHEMA_PARAMETERS, List.of());
        interaction1.put(SCHEMA_DESCRIPTION, "Extracts metadata from a given text");
        interaction1.put(SCHEMA_ACTIVE_ON_DOCUMENTS, List.of("Document"));
        interaction1.put(SCHEMA_UPDATED_AT, new GregorianCalendar());
        interaction1.put(SCHEMA_ACTIVE, true);
        interaction1.put(SCHEMA_ACTIVE_ON_FOLDER, true);
        interaction1.put(SCHEMA_ACTIVE_ON_ACTION_MENU, true);
        interaction1.put(SCHEMA_VERSION, "1");
        interactions.add(interaction1);

        HashMap<String, Object> interaction2 = new HashMap<>();
        interaction2.put(SCHEMA_ID, "65f460c0fbdcc28ee99b65c8");
        interaction2.put(SCHEMA_NAME, "Metadata Extraction");
        interaction2.put(SCHEMA_STATUS, "draft");
        interaction2.put(SCHEMA_PARAMETERS, null);
        interaction2.put(SCHEMA_DESCRIPTION, "Extracts metadata from a given text");
        interaction2.put(SCHEMA_ACTIVE_ON_DOCUMENTS, null);
        interaction2.put(SCHEMA_UPDATED_AT, null);
        interaction2.put(SCHEMA_ACTIVE, false);
        interactions.add(interaction2);

        DocumentModel doc = session.createDocumentModel("/", SETTINGS_DOCUMENT_NAME, SETTINGS_DOCUMENT_TYPE);
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
        interaction1.put(SCHEMA_ID, "65f460c0fbdcc28ee99b65c9");
        interactions.add(interaction1);

        HashMap<String, Object> interaction2 = new HashMap<>();
        interaction2.put(SCHEMA_ID, "65f460c0fbdcc28ee99b65c8");
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
        List<Map<String, Object>> interactions = new ArrayList<>();
        List<Map<String, Object>> parameters = new ArrayList<>();

        // Create the first parameter map
        Map<String, Object> param1 = new HashMap<>();
        param1.put(PARAMETER_SCHEMA_NAME, "question");
        param1.put(PARAMETER_SCHEMA_TYPE, "string");
        param1.put(PARAMETER_SCHEMA_VALUE, "What is your name?");
        param1.put(PARAMETER_SCHEMA_FORMAT, "");
        param1.put(PARAMETER_SCHEMA_READ_ONLY, true);
        param1.put(PARAMETER_SCHEMA_ENABLE_DOC_SELECTION, true);
        param1.put(PARAMETER_SCHEMA_IS_MAIN_DOCUMENT, false);
        param1.put(PARAMETER_SCHEMA_SELECTION_READ_ONLY, true);
        param1.put(PARAMETER_SCHEMA_SELECTED_DOC_IDS, List.of("test"));
        parameters.add(param1);

        // Create the second parameter map
        Map<String, Object> param2 = new HashMap<>();
        param2.put(PARAMETER_SCHEMA_NAME, "name");
        param2.put(PARAMETER_SCHEMA_TYPE, "string");
        param2.put(PARAMETER_SCHEMA_VALUE, "My name is John.");
        param2.put(PARAMETER_SCHEMA_FORMAT, "");
        param2.put(PARAMETER_SCHEMA_READ_ONLY, false);
        param2.put(PARAMETER_SCHEMA_ENABLE_DOC_SELECTION, false);
        param2.put(PARAMETER_SCHEMA_IS_MAIN_DOCUMENT, true);
        param2.put(PARAMETER_SCHEMA_SELECTION_READ_ONLY, false);
        param2.put(PARAMETER_SCHEMA_SELECTED_DOC_IDS, List.of());
        parameters.add(param2);

        // First interaction
        HashMap<String, Object> interaction1 = new HashMap<>();
        interaction1.put(SCHEMA_ID, "random-id-1");
        interaction1.put(SCHEMA_INTERACTION_ID, "interaction-1");
        interaction1.put(SCHEMA_DOCUMENT_ID, "document-id-1");
        interaction1.put(SCHEMA_RESPONSE, "This is a response");
        interaction1.put(SCHEMA_NAME, "Name 1");
        interaction1.put(SCHEMA_STATUS, "Completed");
        interaction1.put(SCHEMA_CREATED_AT, new GregorianCalendar());
        interaction1.put(SCHEMA_CREATED_BY, "User1");
        interaction1.put(SCHEMA_PARAMETERS, parameters);
        interactions.add(interaction1);

        HashMap<String, Object> interaction2 = new HashMap<>();
        interaction2.put(SCHEMA_ID, "random-id-2");
        interaction2.put(SCHEMA_INTERACTION_ID, "interaction-2");
        interaction2.put(SCHEMA_DOCUMENT_ID, "document-id-2");
        interaction2.put(SCHEMA_RESPONSE, "This is a response");
        interaction2.put(SCHEMA_NAME, "Name 2");
        interaction2.put(SCHEMA_STATUS, "Completed");
        interaction2.put(SCHEMA_CREATED_AT, null);
        interaction2.put(SCHEMA_CREATED_BY, "User2");
        interaction2.put(SCHEMA_PARAMETERS, parameters);
        interactions.add(interaction2);

        DocumentModel doc = session.createDocumentModel("/", RESULTS_DOCUMENT_NAME, RESULTS_DOCUMENT_TYPE);
        doc = session.createDocument(doc);
        doc.setPropertyValue(RESULTS_SCHEMA_PATH, (Serializable) interactions);
        doc.setPropertyValue(RESULTS_STORE_ID_SCHEMA_PATH, "store-id");
        doc.setPropertyValue(RESULTS_STORED_DATE_SCHEMA_PATH, new GregorianCalendar());
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
    public void testDocumentCreation() {
        DocumentModel doc = session.createDocumentModel("/", "testFile", "File");
        doc.setPropertyValue("dc:title", "Test Document");
        doc.setPropertyValue("dc:description", "This is a test description");
        doc = session.createDocument(doc);
        session.save();
        DocumentModel createdDoc = session.getDocument(doc.getRef());
        assertNotNull(createdDoc);
        assertEquals("Test Document", createdDoc.getTitle());
        assertEquals("This is a test description", createdDoc.getPropertyValue("dc:description"));
        assertEquals("File", createdDoc.getType());
    }

}
