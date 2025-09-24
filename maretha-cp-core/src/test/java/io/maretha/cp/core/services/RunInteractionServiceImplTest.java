package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.models.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nuxeo.directory.test.DirectoryFeature;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.SCHEMA_SETTINGS;
import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.AI_SETTINGS;
import static io.maretha.cp.core.services.RunInteractionService.FILE_CONTENT;
import static io.maretha.cp.core.services.RunInteractionServiceImpl.CALLBACK_NOTIFY_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, RuntimeFeature.class, PlatformFeature.class, MockitoFeature.class, AuditFeature.class, DirectoryFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
@Deploy("org.nuxeo.ecm.platform.tag")
@Deploy("io.maretha.cp.core.maretha-cp-core-test:OSGI-INF/cp-test-doc-schema-contrib-test.xml")
@Deploy("io.maretha.cp.core.maretha-cp-core-test:OSGI-INF/cp-test-directories-contrib.xml")
@Deploy({
        "org.nuxeo.ecm.platform.audit",
        "org.nuxeo.ecm.platform.audit.api"
})
public class RunInteractionServiceImplTest {

    @Inject
    CoreSession session;

    @Mock
    @RuntimeService
    APIService apiService;

    @Inject
    RunInteractionService runInteractionService;

    @Inject
    TransactionalFeature txFeature;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testAuditLoggerIsAvailable() {
        AuditLogger logger = Framework.getService(AuditLogger.class);
        assertNotNull("AuditLogger should not be null", logger);
    }

    @Test
    public void shouldSaveMetadataArraysAndTags() throws JsonProcessingException {
        DocumentModel document = createDocument();
        createSettingsDocument();

        Settings settingsDocument = Framework.getService(RepositoryService.class).getSavedSettings(session, false);

        when(apiService.uploadFileToStore(anyObject(), anyString(), anyBoolean())).thenReturn("vertesia-store-id");
        when(apiService.getDocumentEmbeddings(anyString(), anyString())).thenReturn(new double[]{});

        List<InteractionParameter> parameters = settingsDocument.getInteractions().get(0).getParameters();
        document = runInteractionService.addFacetAndEmbeddings(document, session, false, true);
        runInteractionService.triggerInteraction(document, session, "67d966033bbe22f0d50c7c6d", parameters, true, true, true);

        String jsonPayload = "{\n" +
                "          \"event\" : \"workflow_finished\",\n" +
                "          \"data\" : {\n" +
                "            \"interaction_run_id\" : \"686d5cf79e02af71b2ec7b32\",\n" +
                "            \"workflow_id\" : \"generation:ExecuteInteractionWorkflow:DynamicMetadata:jief03ard\",\n" +
                "            \"workflow_run_id\" : \"0197eb33-271e-7f2d-9dbd-1f0c730619fd\",\n" +
                "            \"status\" : \"completed\",\n" +
                "            \"result\" : {\n" +
                "              \"result\" : {\n" +
                "                   \"dc:title\":\"Green Bird on Branch.\"," +
                "                   \"dc:description\":\"A small green bird with speckled breast perched on a tree branch in a lush forest setting.\"," +
                "                   \"dc:language\":\"en\"," +
                "                   \"testDoc:testStringArray\":[\"test-111\", \"test-123\"]," +
                "                   \"nxtag:tags\":[\"tag-1\", \"tag-2\", \"tag-1\"]," +
                "                   \"testDoc:eventTypes\":[\"documentCreated\", \"documentSecurityUpdated\"]," +
                "                   \"dc:subjects\":[\"article\", \"certificate\"]" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonPayload);
        runInteractionService.validateAndSaveMetadata(jsonNode.get("data"), document.getId(), true);

        DocumentModel updatedDoc = session.getDocument(document.getRef());
        List<String> testStringArray = (List<String>) updatedDoc.getPropertyValue("testDoc:testStringArray");
        assertEquals(List.of("test-111", "test-123"), testStringArray);

        List<Map<String, String>> tags = (List<Map<String, String>>) updatedDoc.getPropertyValue("nxtag:tags");
        assertEquals(List.of(Map.of("label", "tag-1", "username", "system"), Map.of("label", "tag-2", "username", "system")), tags);

        String[] testEventsVocabArray = (String[]) updatedDoc.getPropertyValue("testDoc:eventTypes");
        assertEquals(List.of("documentCreated", "documentSecurityUpdated").toArray(), testEventsVocabArray);
    }

    @Test
    public void shouldAddMetadataIfMissing() throws JsonProcessingException {
        DocumentModel document = createDocument();
        createSettingsDocument();

        Settings settingsDocument = Framework.getService(RepositoryService.class).getSavedSettings(session, false);

        //WHEN
        when(apiService.uploadFileToStore(anyObject(), anyString(), anyBoolean())).thenReturn("vertesia-store-id");
        when(apiService.getDocumentEmbeddings(anyString(), anyString())).thenReturn(new double[]{});
        when(apiService.getInteractionResultWithResultSchemaAsync(anyString(), anyMap(), anyString(), anyString(), anyString())).thenReturn("apiResponse");

        List<InteractionParameter> parameters = settingsDocument.getInteractions().get(0).getParameters();
        document = runInteractionService.addFacetAndEmbeddings(document, session, false, true);
        runInteractionService.triggerInteraction(document, session, "67d966033bbe22f0d50c7c6d", parameters, true, false, true);

        String jsonPayload = "{\n" +
                "          \"event\" : \"workflow_finished\",\n" +
                "          \"data\" : {\n" +
                "            \"interaction_run_id\" : \"686d5cf79e02af71b2ec7b32\",\n" +
                "            \"workflow_id\" : \"generation:ExecuteInteractionWorkflow:DynamicMetadata:jief03ard\",\n" +
                "            \"workflow_run_id\" : \"0197eb33-271e-7f2d-9dbd-1f0c730619fd\",\n" +
                "            \"status\" : \"completed\",\n" +
                "            \"result\" : {\n" +
                "              \"result\" : {\n" +
                "                   \"dc:title\":\"Green Bird on Branch.\"," +
                "                   \"dc:description\":\"A small green bird with speckled breast perched on a tree branch in a lush forest setting.\"," +
                "                   \"dc:language\":\"en\"," +
                "                   \"testDoc:testStringArray\":[\"test-111\", \"test-123\"]," +
                "                   \"nxtag:tags\":[\"tag-1\", \"tag-2\", \"tag-1\"]," +
                "                   \"dc:subjects\":[\"article\", \"certificate\"]" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonPayload);
        runInteractionService.validateAndSaveMetadata(jsonNode.get("data"), document.getId(), false);

        //THEN
        DocumentModel updatedDoc = session.getDocument(document.getRef());
        assertEquals("Test Document", document.getPropertyValue("dc:title"));
        List<String> testStringArray = (List<String>) updatedDoc.getPropertyValue("testDoc:testStringArray");
        assertEquals(List.of("test-111", "test-123"), testStringArray);

        List<Map<String, String>> tags = (List<Map<String, String>>) updatedDoc.getPropertyValue("nxtag:tags");
        assertEquals(List.of(Map.of("label", "tag-1", "username", "system"), Map.of("label", "tag-2", "username", "system")), tags);
    }

    @Test
    public void shouldAddMetadataIfMissingSyncMode() throws JsonProcessingException {
        DocumentModel document = createDocument();
        createSettingsDocument();

        Settings settingsDocument = Framework.getService(RepositoryService.class).getSavedSettings(session, false);

        String mockResponse = "{\"result\":{\"result\":" +
                "{\"dc:title\":\"Green Bird on Branch\"," +
                "\"dc:description\":\"A small green bird with speckled breast perched on a tree branch in a lush forest setting.\"," +
                "\"dc:language\":\"en\"," +
                "\"testDoc:testStringArray\":[\"test-111\", \"test-123\"]," +
                "\"nxtag:tags\":[\"tag-1\", \"tag-2\", \"tag-1\"]," +
                "\"testDoc:eventTypes\":[\"documentCreated\", \"documentSecurityUpdated\"]," +
                "\"dc:subjects\":[\"article\", \"certificate\"]" +
                "}" +
                "}}";

        //WHEN
        when(apiService.uploadFileToStore(anyObject(), anyString(), anyBoolean())).thenReturn("vertesia-store-id");
        when(apiService.getDocumentEmbeddings(anyString(), anyString())).thenReturn(new double[]{});
        when(apiService.getInteractionResultWithResultSchema(anyString(), anyMap(), anyString(), anyBoolean())).thenReturn(mockResponse);

        List<InteractionParameter> parameters = settingsDocument.getInteractions().get(0).getParameters();
        document = runInteractionService.addFacetAndEmbeddings(document, session, false, true);
        runInteractionService.triggerInteraction(document, session, "67d966033bbe22f0d50c7c6d", parameters, true, false, false);



        //THEN
        DocumentModel updatedDoc = session.getDocument(document.getRef());
        assertEquals("Test Document", document.getPropertyValue("dc:title"));
        List<String> testStringArray = (List<String>) updatedDoc.getPropertyValue("testDoc:testStringArray");
        assertEquals(List.of("test-111", "test-123"), testStringArray);

        List<Map<String, String>> tags = (List<Map<String, String>>) updatedDoc.getPropertyValue("nxtag:tags");
        assertEquals(List.of(Map.of("label", "tag-1", "username", "system"), Map.of("label", "tag-2", "username", "system")), tags);
    }

    @Test
    public void shouldHaveVocabularyFromOperation() throws OperationException {
        DirectoryService ds = Framework.getService(DirectoryService.class);
        System.out.println("Known directories: " + String.join(", ", ds.getDirectoryNames()));

        assertTrue(ds.getDirectoryNames().contains("eventTypes"));

        Map<String, Serializable> params = new HashMap<>();
        params.put("directoryName", "eventTypes");
        OperationContext ctx = new OperationContext(session);
        Blob directoriesJsonBlob = (Blob) Framework.getService(AutomationService.class).run(ctx, "Directory.SuggestEntries", params);
        assertNotNull(directoriesJsonBlob);
    }

    private DocumentModel createDocument() {
        DocumentModel doc = session.createDocumentModel("/", "testDocument", "TestDoc");
        doc.setPropertyValue("dc:title", "Test Document");
        doc.setPropertyValue("dc:description", "This is a test description");
        doc.setPropertyValue(FILE_CONTENT, new StringBlob("Test", "text/plain"));
        doc.addFacet("NXTag");
        doc = session.createDocument(doc);
        session.save();
        return doc;
    }

    private void createSettingsDocument() throws JsonProcessingException {
        List<ObjectNode> interactions = new ArrayList<>();
        Map<String, Object> configurationMap = new HashMap<>();

        String dynamicMetadataInteractionJson = "{\n" +
                "    \"activeOnDocumentTypes\": [\n" +
                "      \"TestDoc\",\n" +
                "      \"Picture\",\n" +
                "      \"Collection\",\n" +
                "      \"Folder\"\n" +
                "    ],\n" +
                "    \"description\": \"Save metadata into Nuxeo.\",\n" +
                "    \"active\": true,\n" +
                "    \"version\": \"1\",\n" +
                "    \"activeOnMimeTypes\": null,\n" +
                "    \"activeOnFolder\": true,\n" +
                "    \"chat\": false,\n" +
                "    \"name\": \"Dynamic Metadata\",\n" +
                "    \"endpoint\": \"DynamicMetadata\",\n" +
                "    \"saveMetadata\": true,\n" +
                "    \"id\": \"67d966033bbe22f0d50c7c6d\",\n" +
                "    \"activeOnActionMenu\": false,\n" +
                "    \"parameters\": [\n" +
                "      {\n" +
                "        \"isContextMemoryParameter\": false,\n" +
                "        \"isMainDocument\": true,\n" +
                "        \"selectedDocumentId\": null,\n" +
                "        \"name\": \"document\",\n" +
                "        \"format\": \"document\",\n" +
                "        \"selectionReadOnly\": false,\n" +
                "        \"selectedDocumentIds\": [],\n" +
                "        \"description\": \"\",\n" +
                "        \"readOnly\": true,\n" +
                "        \"enableDocSelection\": false,\n" +
                "        \"type\": \"\",\n" +
                "        \"value\": null\n" +
                "      },\n" +
                "      {\n" +
                "        \"isContextMemoryParameter\": false,\n" +
                "        \"isMainDocument\": false,\n" +
                "        \"selectedDocumentId\": null,\n" +
                "        \"name\": \"schema\",\n" +
                "        \"format\": \"\",\n" +
                "        \"selectionReadOnly\": false,\n" +
                "        \"selectedDocumentIds\": [],\n" +
                "        \"description\": \"\",\n" +
                "        \"readOnly\": true,\n" +
                "        \"enableDocSelection\": false,\n" +
                "        \"type\": \"string\",\n" +
                "        \"value\": null\n" +
                "      },\n" +
                "      {\n" +
                "        \"isContextMemoryParameter\": false,\n" +
                "        \"isMainDocument\": false,\n" +
                "        \"selectedDocumentId\": null,\n" +
                "        \"name\": \"dictionaries\",\n" +
                "        \"format\": \"\",\n" +
                "        \"selectionReadOnly\": false,\n" +
                "        \"selectedDocumentIds\": [],\n" +
                "        \"description\": \"\",\n" +
                "        \"readOnly\": true,\n" +
                "        \"enableDocSelection\": false,\n" +
                "        \"type\": \"string\",\n" +
                "        \"value\": null\n" +
                "      },\n" +
                "      {\n" +
                "        \"isContextMemoryParameter\": false,\n" +
                "        \"isMainDocument\": true,\n" +
                "        \"selectedDocumentId\": null,\n" +
                "        \"name\": \"media\",\n" +
                "        \"format\": \"media\",\n" +
                "        \"selectionReadOnly\": false,\n" +
                "        \"selectedDocumentIds\": [],\n" +
                "        \"description\": \"\",\n" +
                "        \"readOnly\": true,\n" +
                "        \"enableDocSelection\": false,\n" +
                "        \"type\": \"\",\n" +
                "        \"value\": null\n" +
                "      }\n" +
                "    ],\n" +
                "    \"status\": \"draft\",\n" +
                "    \"updatedAt\": null\n" +
                "}";

        ObjectNode objectNode = (ObjectNode) objectMapper.readTree(dynamicMetadataInteractionJson);
        interactions.add(objectNode);

        DocTypeWithSchema.Schema.Field testDocfield_1 = createField(true,
                "testDoc:testStringArray", "stringList", true, true, null);
        DocTypeWithSchema.Schema.Field testDocfield_2 = createField(true,
                "testDoc:eventTypes", "stringList", true, true, "eventTypes");

        addSchemaConfiguration(configurationMap, interactions, "testDoc", List.of(testDocfield_1, testDocfield_2));

        DocTypeWithSchema.Schema.Field dublinCoreField_1 = createField(true,
                "dc:subjects", "subjectList", true, true, "l10nsubjects");

        addSchemaConfiguration(configurationMap, interactions, "TestDoc_dublincore", List.of(dublinCoreField_1));

        DocTypeWithSchema.Schema.Field tagField_0 = createField(true,
                "nxtag:tags", "tagList", true, true, null);
        //generic schema settings
        addSchemaConfiguration(configurationMap, interactions, "facetedTag", List.of(tagField_0));

        DocTypeWithSchema.Schema.Field tagField_1 = createField(true,
                "nxtag:tags", "tagList", true, true, null);
        //specific schema settings for TestDoc
        addSchemaConfiguration(configurationMap, interactions, "TestDoc_facetedTag", List.of(tagField_1));


        //add callbackNotifyUrl
        configurationMap.put(AI_SETTINGS, Map.of(CALLBACK_NOTIFY_URL, "callbackNotifyUrl"));
        Framework.getService(RepositoryService.class).saveInteractionSettings(session, interactions, configurationMap);
        txFeature.nextTransaction();

    }

    private void addSchemaConfiguration(Map<String, Object> configurationMap, List<ObjectNode> interactions,
                                        String schemaName, List<DocTypeWithSchema.Schema.Field> fields) {
        DocTypeWithSchema.Schema schema = new DocTypeWithSchema.Schema();
        schema.setName(schemaName);
        schema.setFields(fields);
        configurationMap.put(SCHEMA_SETTINGS, schema);

        Framework.getService(RepositoryService.class).saveInteractionSettings(session, interactions, configurationMap);
        txFeature.nextTransaction();
    }

    private DocTypeWithSchema.Schema.Field createField(boolean multiValue, String xpath, String type, boolean active, boolean inIsolation, String directoryName) {
        DocTypeWithSchema.Schema.Field field = new DocTypeWithSchema.Schema.Field();
        field.setMultiValue(multiValue);
        field.setXpath(xpath);
        field.setType(type);
        field.setActive(active);
        field.setInIsolation(inIsolation);
        field.setDirectoryName(directoryName);
        return field;
    }

}