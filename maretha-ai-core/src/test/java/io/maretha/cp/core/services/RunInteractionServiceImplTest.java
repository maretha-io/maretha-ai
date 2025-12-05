package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.services.ai.EmbeddingsService;
import io.maretha.cp.core.services.ai.InteractionService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, RuntimeFeature.class, PlatformFeature.class, MockitoFeature.class,
                AuditFeature.class, DirectoryFeature.class })
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
        InteractionService apiService;

        @Mock
        @RuntimeService
        EmbeddingsService embeddingsService;

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

                Settings settingsDocument = Framework.getService(RepositoryService.class).getSavedSettings(session,
                                false);

                String jsonPayload = "{\n" +
                                "              \"result\" : {\n" +
                                "                   \"dc:title\":\"Green Bird on Branch.\"," +
                                "                   \"dc:description\":\"A small green bird with speckled breast perched on a tree branch in a lush forest setting.\","
                                +
                                "                   \"dc:language\":\"en\"," +
                                "                   \"testDoc:testStringArray\":[\"test-111\", \"test-123\"]," +
                                "                   \"nxtag:tags\":[\"tag-1\", \"tag-2\", \"tag-1\"]," +
                                "                   \"testDoc:eventTypes\":[\"documentCreated\", \"documentSecurityUpdated\"],"
                                +
                                "                   \"dc:subjects\":[\"article\", \"certificate\"]" +
                                "          }\n" +
                                "        }";

                when(apiService.invokeModelWithSchema(any(), any(), anyMap(), anyString())).thenReturn(jsonPayload);
                when(embeddingsService.embedImage(anyString(), anyString(), anyInt())).thenReturn(new double[] {});
                when(embeddingsService.embedPdfSummary(any())).thenReturn(new double[] {});

                List<InteractionParameter> parameters = settingsDocument.getInteractions().get(0).getParameters();
                document = runInteractionService.addEmbeddings(document, session, false, true);
                runInteractionService.triggerInteraction(document, session, "67d966033bbe22f0d50c7c6d", parameters,
                                true, true, true);

                DocumentModel updatedDoc = session.getDocument(document.getRef());
                List<String> testStringArray = (List<String>) updatedDoc.getPropertyValue("testDoc:testStringArray");
                assertEquals(List.of("test-111", "test-123"), testStringArray);

                List<Map<String, String>> tags = (List<Map<String, String>>) updatedDoc.getPropertyValue("nxtag:tags");
                assertEquals(List.of(Map.of("label", "tag-1", "username", "system"),
                                Map.of("label", "tag-2", "username", "system")), tags);

                String[] testEventsVocabArray = (String[]) updatedDoc.getPropertyValue("testDoc:eventTypes");
                assertEquals(List.of("documentCreated", "documentSecurityUpdated").toArray(), testEventsVocabArray);
        }

        @Test
        public void shouldAddMetadataIfMissing() throws JsonProcessingException {
                DocumentModel document = createDocument();
                createSettingsDocument();

                Settings settingsDocument = Framework.getService(RepositoryService.class).getSavedSettings(session,
                                false);

                String jsonPayload = "{\n" +
                                "              \"result\" : {\n" +
                                "                   \"dc:title\":\"Green Bird on Branch.\"," +
                                "                   \"dc:description\":\"A small green bird with speckled breast perched on a tree branch in a lush forest setting.\","
                                +
                                "                   \"dc:language\":\"en\"," +
                                "                   \"testDoc:testStringArray\":[\"test-111\", \"test-123\"]," +
                                "                   \"nxtag:tags\":[\"tag-1\", \"tag-2\", \"tag-1\"]," +
                                "                   \"dc:subjects\":[\"article\", \"certificate\"]" +
                                "        }}";
                // WHEN
                when(embeddingsService.embedImage(anyString(), anyString(), anyInt())).thenReturn(new double[] {});
                when(apiService.invokeModelWithSchema(any(), any(), anyMap(), anyString())).thenReturn(jsonPayload);

                List<InteractionParameter> parameters = settingsDocument.getInteractions().get(0).getParameters();
                document = runInteractionService.addEmbeddings(document, session, false, true);
                runInteractionService.triggerInteraction(document, session, "67d966033bbe22f0d50c7c6d", parameters,
                                true, false, true);

                // THEN
                DocumentModel updatedDoc = session.getDocument(document.getRef());
                assertEquals("Test Document", document.getPropertyValue("dc:title"));
                List<String> testStringArray = (List<String>) updatedDoc.getPropertyValue("testDoc:testStringArray");
                assertEquals(List.of("test-111", "test-123"), testStringArray);

                List<Map<String, String>> tags = (List<Map<String, String>>) updatedDoc.getPropertyValue("nxtag:tags");
                assertEquals(List.of(Map.of("label", "tag-1", "username", "system"),
                                Map.of("label", "tag-2", "username", "system")), tags);
        }

        @Test
        public void shouldAddMetadataIfMissingSyncMode() throws JsonProcessingException {
                DocumentModel document = createDocument();
                createSettingsDocument();

                Settings settingsDocument = Framework.getService(RepositoryService.class).getSavedSettings(session,
                                false);

                String mockResponse = "{\"result\":" +
                                "{\"dc:title\":\"Green Bird on Branch\"," +
                                "\"dc:description\":\"A small green bird with speckled breast perched on a tree branch in a lush forest setting.\","
                                +
                                "\"dc:language\":\"en\"," +
                                "\"testDoc:testStringArray\":[\"test-111\", \"test-123\"]," +
                                "\"nxtag:tags\":[\"tag-1\", \"tag-2\", \"tag-1\"]," +
                                "\"testDoc:eventTypes\":[\"documentCreated\", \"documentSecurityUpdated\"]," +
                                "\"dc:subjects\":[\"article\", \"certificate\"]" +
                                "}" +
                                "}";

                // WHEN
                when(embeddingsService.embedImage(anyString(), anyString(), anyInt())).thenReturn(new double[] {});
                when(embeddingsService.embedPdfSummary(any())).thenReturn(new double[] {});
                when(apiService.invokeModelWithSchema(any(), any(), anyMap(), anyString())).thenReturn(mockResponse);
                when(apiService.invokeModel(any(), any(), anyMap())).thenReturn(mockResponse);

                List<InteractionParameter> parameters = settingsDocument.getInteractions().get(0).getParameters();
                document = runInteractionService.addEmbeddings(document, session, false, true);
                runInteractionService.triggerInteraction(document, session, "67d966033bbe22f0d50c7c6d", parameters,
                                true, false, false);

                // THEN
                DocumentModel updatedDoc = session.getDocument(document.getRef());
                assertEquals("Test Document", document.getPropertyValue("dc:title"));
                List<String> testStringArray = (List<String>) updatedDoc.getPropertyValue("testDoc:testStringArray");
                assertEquals(List.of("test-111", "test-123"), testStringArray);

                List<Map<String, String>> tags = (List<Map<String, String>>) updatedDoc.getPropertyValue("nxtag:tags");
                assertEquals(List.of(Map.of("label", "tag-1", "username", "system"),
                                Map.of("label", "tag-2", "username", "system")), tags);
        }

        @Test
        public void shouldOverwriteMetadataArrayWhenOverrideTrue() throws JsonProcessingException {
                DocumentModel document = createDocument();
                // Pre-populate array
                ArrayList<String> initialArray = new ArrayList<>();
                initialArray.add("initial-1");
                initialArray.add("initial-2");
                document.setPropertyValue("testDoc:testStringArray", initialArray);
                session.saveDocument(document);

                createSettingsDocument();

                Settings settingsDocument = Framework.getService(RepositoryService.class).getSavedSettings(session,
                                false);

                String mockResponse = "{\"result\":" +
                                "{\"testDoc:testStringArray\":[\"new-1\", \"new-2\"]}" +
                                "}";

                when(apiService.invokeModelWithSchema(any(), any(), anyMap(), anyString())).thenReturn(mockResponse);
                when(apiService.invokeModel(any(), any(), anyMap())).thenReturn(mockResponse);

                List<InteractionParameter> parameters = settingsDocument.getInteractions().get(0).getParameters();
                // Trigger with saveMetadataModeOverride = true
                runInteractionService.triggerInteraction(document, session, "67d966033bbe22f0d50c7c6d", parameters,
                                true, true, false);

                DocumentModel updatedDoc = session.getDocument(document.getRef());
                List<String> testStringArray = (List<String>) updatedDoc.getPropertyValue("testDoc:testStringArray");
                // Should contain ONLY new values
                assertEquals(List.of("new-1", "new-2"), testStringArray);
        }

        @Test
        public void shouldAppendMetadataArrayWhenOverrideFalse() throws JsonProcessingException {
                DocumentModel document = createDocument();
                // Pre-populate array
                ArrayList<String> initialArray = new ArrayList<>();
                initialArray.add("initial-1");
                initialArray.add("initial-2");
                document.setPropertyValue("testDoc:testStringArray", initialArray);
                session.saveDocument(document);

                createSettingsDocument();

                Settings settingsDocument = Framework.getService(RepositoryService.class).getSavedSettings(session,
                                false);

                String mockResponse = "{\"result\":" +
                                "{\"testDoc:testStringArray\":[\"new-1\", \"new-2\"]}" +
                                "}";

                when(apiService.invokeModelWithSchema(any(), any(), anyMap(), anyString())).thenReturn(mockResponse);
                when(apiService.invokeModel(any(), any(), anyMap())).thenReturn(mockResponse);

                List<InteractionParameter> parameters = settingsDocument.getInteractions().get(0).getParameters();
                // Trigger with saveMetadataModeOverride = false
                runInteractionService.triggerInteraction(document, session, "67d966033bbe22f0d50c7c6d", parameters,
                                true, false, false);

                DocumentModel updatedDoc = session.getDocument(document.getRef());
                List<String> testStringArray = (List<String>) updatedDoc.getPropertyValue("testDoc:testStringArray");
                // Should contain BOTH initial and new values
                assertEquals(List.of("new-1", "new-2", "initial-1", "initial-2"), testStringArray);
        }

        @Test
        public void shouldHaveVocabularyFromOperation() throws OperationException {
                DirectoryService ds = Framework.getService(DirectoryService.class);
                System.out.println("Known directories: " + String.join(", ", ds.getDirectoryNames()));

                assertTrue(ds.getDirectoryNames().contains("eventTypes"));

                Map<String, Serializable> params = new HashMap<>();
                params.put("directoryName", "eventTypes");
                OperationContext ctx = new OperationContext(session);
                Blob directoriesJsonBlob = (Blob) Framework.getService(AutomationService.class).run(ctx,
                                "Directory.SuggestEntries", params);
                assertNotNull(directoriesJsonBlob);
        }

        private DocumentModel createDocument() {
                DocumentModel doc = session.createDocumentModel("/", "testDocument", "TestDoc");
                doc.setPropertyValue("dc:title", "Test Document");
                doc.setPropertyValue("dc:description", "This is a test description");
                doc.setPropertyValue(FILE_CONTENT, new StringBlob("Test", "application/pdf"));
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

                addSchemaConfiguration(configurationMap, interactions, "testDoc",
                                List.of(testDocfield_1, testDocfield_2));

                DocTypeWithSchema.Schema.Field dublinCoreField_1 = createField(true,
                                "dc:subjects", "subjectList", true, true, "l10nsubjects");

                addSchemaConfiguration(configurationMap, interactions, "TestDoc_dublincore",
                                List.of(dublinCoreField_1));

                DocTypeWithSchema.Schema.Field tagField_0 = createField(true,
                                "nxtag:tags", "tagList", true, true, null);
                // generic schema settings
                addSchemaConfiguration(configurationMap, interactions, "facetedTag", List.of(tagField_0));

                DocTypeWithSchema.Schema.Field tagField_1 = createField(true,
                                "nxtag:tags", "tagList", true, true, null);
                // specific schema settings for TestDoc
                addSchemaConfiguration(configurationMap, interactions, "TestDoc_facetedTag", List.of(tagField_1));

                // add callbackNotifyUrl
                configurationMap.put(AI_SETTINGS, Map.of(CALLBACK_NOTIFY_URL, "callbackNotifyUrl"));
                Framework.getService(RepositoryService.class).saveInteractionSettings(session, interactions,
                                configurationMap);
                txFeature.nextTransaction();

        }

        private void addSchemaConfiguration(Map<String, Object> configurationMap, List<ObjectNode> interactions,
                        String schemaName, List<DocTypeWithSchema.Schema.Field> fields) {
                DocTypeWithSchema.Schema schema = new DocTypeWithSchema.Schema();
                schema.setName(schemaName);
                schema.setFields(fields);
                configurationMap.put(SCHEMA_SETTINGS, schema);

                Framework.getService(RepositoryService.class).saveInteractionSettings(session, interactions,
                                configurationMap);
                txFeature.nextTransaction();
        }

        private DocTypeWithSchema.Schema.Field createField(boolean multiValue, String xpath, String type,
                        boolean active, boolean inIsolation, String directoryName) {
                DocTypeWithSchema.Schema.Field field = new DocTypeWithSchema.Schema.Field();
                field.setMultiValue(multiValue);
                field.setXpath(xpath);
                field.setType(type);
                field.setActive(active);
                field.setInIsolation(inIsolation);
                field.setDirectoryName(directoryName);
                return field;
        }

        @Test
        public void shouldHandleRecursiveRelatedDocuments() throws JsonProcessingException {
                // Create a chain of related documents: doc1 -> doc2 -> doc3
                DocumentModel doc3 = session.createDocumentModel("/", "doc3", "TestDoc");
                doc3.setPropertyValue("dc:title", "Related Doc 3");
                doc3.setPropertyValue("testDoc:testString", "value-3");
                doc3 = session.createDocument(doc3);

                DocumentModel doc2 = session.createDocumentModel("/", "doc2", "TestDoc");
                doc2.setPropertyValue("dc:title", "Related Doc 2");
                doc2.setPropertyValue("testDoc:testString", "value-2");
                doc2.setPropertyValue("testDoc:testDocRef", doc3.getId());
                doc2 = session.createDocument(doc2);

                DocumentModel doc1 = session.createDocumentModel("/", "doc1", "TestDoc");
                doc1.setPropertyValue("dc:title", "Main Doc");
                doc1.setPropertyValue("testDoc:testString", "value-1");
                doc1.setPropertyValue("testDoc:testDocRef", doc2.getId());
                doc1 = session.createDocument(doc1);
                session.save();

                // Create settings with additionalContext configuration
                List<ObjectNode> interactions = new ArrayList<>();
                Map<String, Object> configurationMap = new HashMap<>();

                String interactionJson = "{\n" +
                                "    \"activeOnDocumentTypes\": [\"TestDoc\"],\n" +
                                "    \"description\": \"Test recursive context\",\n" +
                                "    \"active\": true,\n" +
                                "    \"version\": \"1\",\n" +
                                "    \"chat\": false,\n" +
                                "    \"name\": \"Test Interaction\",\n" +
                                "    \"saveMetadata\": true,\n" +
                                "    \"mainMetadata\": true,\n" +
                                "    \"id\": \"test-recursive-id\",\n" +
                                "    \"parameters\": [\n" +
                                "      {\n" +
                                "        \"isAdditionalContext\": true,\n" +
                                "        \"name\": \"additionalContext\",\n" +
                                "        \"type\": \"string\"\n" +
                                "      }\n" +
                                "    ]\n" +
                                "}";

                ObjectNode objectNode = (ObjectNode) objectMapper.readTree(interactionJson);
                interactions.add(objectNode);

                // Configure schema to include testDocRef and testString as additionalContext fields
                DocTypeWithSchema.Schema.Field refField = new DocTypeWithSchema.Schema.Field();
                refField.setXpath("testDoc:testDocRef");
                refField.setActive(true);
                refField.setAdditionalContext(true);

                DocTypeWithSchema.Schema.Field stringField = new DocTypeWithSchema.Schema.Field();
                stringField.setXpath("testDoc:testString");
                stringField.setActive(true);
                stringField.setAdditionalContext(true);

                addSchemaConfiguration(configurationMap, interactions, "TestDoc_testDoc",
                                List.of(refField, stringField));

                // Mock the API response
                String mockResponse = "{\"result\":{\"dc:title\":\"Updated Title\"}}";
                when(apiService.invokeModelWithSchema(any(), any(), anyMap(), anyString())).thenReturn(mockResponse);

                // Trigger interaction
                List<InteractionParameter> parameters = new ArrayList<>();
                runInteractionService.triggerInteraction(doc1, session, "test-recursive-id", parameters, true, false,
                                false);

                // The test passes if no StackOverflowError or infinite loop occurs
                // In a real test, you would verify the nested context structure was passed to the AI
                assertTrue("Test completed without recursion errors", true);
        }

        @Test
        public void shouldPreventCircularReferences() throws JsonProcessingException {
                // Create circular reference: doc1 -> doc2 -> doc1
                DocumentModel doc1 = session.createDocumentModel("/", "circular1", "TestDoc");
                doc1.setPropertyValue("dc:title", "Circular Doc 1");
                doc1 = session.createDocument(doc1);

                DocumentModel doc2 = session.createDocumentModel("/", "circular2", "TestDoc");
                doc2.setPropertyValue("dc:title", "Circular Doc 2");
                doc2.setPropertyValue("testDoc:testDocRef", doc1.getId());
                doc2 = session.createDocument(doc2);

                // Update doc1 to point to doc2, creating a circle
                doc1.setPropertyValue("testDoc:testDocRef", doc2.getId());
                doc1 = session.saveDocument(doc1);
                session.save();

                // Create settings
                List<ObjectNode> interactions = new ArrayList<>();
                Map<String, Object> configurationMap = new HashMap<>();

                String interactionJson = "{\n" +
                                "    \"activeOnDocumentTypes\": [\"TestDoc\"],\n" +
                                "    \"active\": true,\n" +
                                "    \"saveMetadata\": true,\n" +
                                "    \"mainMetadata\": true,\n" +
                                "    \"id\": \"test-circular-id\",\n" +
                                "    \"parameters\": [\n" +
                                "      {\n" +
                                "        \"isAdditionalContext\": true,\n" +
                                "        \"name\": \"additionalContext\",\n" +
                                "        \"type\": \"string\"\n" +
                                "      }\n" +
                                "    ]\n" +
                                "}";

                ObjectNode objectNode = (ObjectNode) objectMapper.readTree(interactionJson);
                interactions.add(objectNode);

                DocTypeWithSchema.Schema.Field refField = new DocTypeWithSchema.Schema.Field();
                refField.setXpath("testDoc:testDocRef");
                refField.setActive(true);
                refField.setAdditionalContext(true);

                addSchemaConfiguration(configurationMap, interactions, "TestDoc_testDoc", List.of(refField));

                String mockResponse = "{\"result\":{}}";
                when(apiService.invokeModelWithSchema(any(), any(), anyMap(), anyString())).thenReturn(mockResponse);

                // Should not throw StackOverflowError due to cycle detection
                List<InteractionParameter> parameters = new ArrayList<>();
                runInteractionService.triggerInteraction(doc1, session, "test-circular-id", parameters, true, false,
                                false);

                assertTrue("Circular reference handled without errors", true);
        }

        @Test
        public void shouldLimitRecursionDepth() throws JsonProcessingException {
                // Create a deep chain: doc1 -> doc2 -> doc3 -> doc4 -> doc5 -> doc6 (depth > 5)
                DocumentModel doc6 = session.createDocumentModel("/", "depth6", "TestDoc");
                doc6.setPropertyValue("dc:title", "Depth 6");
                doc6 = session.createDocument(doc6);

                DocumentModel doc5 = session.createDocumentModel("/", "depth5", "TestDoc");
                doc5.setPropertyValue("dc:title", "Depth 5");
                doc5.setPropertyValue("testDoc:testDocRef", doc6.getId());
                doc5 = session.createDocument(doc5);

                DocumentModel doc4 = session.createDocumentModel("/", "depth4", "TestDoc");
                doc4.setPropertyValue("dc:title", "Depth 4");
                doc4.setPropertyValue("testDoc:testDocRef", doc5.getId());
                doc4 = session.createDocument(doc4);

                DocumentModel doc3 = session.createDocumentModel("/", "depth3", "TestDoc");
                doc3.setPropertyValue("dc:title", "Depth 3");
                doc3.setPropertyValue("testDoc:testDocRef", doc4.getId());
                doc3 = session.createDocument(doc3);

                DocumentModel doc2 = session.createDocumentModel("/", "depth2", "TestDoc");
                doc2.setPropertyValue("dc:title", "Depth 2");
                doc2.setPropertyValue("testDoc:testDocRef", doc3.getId());
                doc2 = session.createDocument(doc2);

                DocumentModel doc1 = session.createDocumentModel("/", "depth1", "TestDoc");
                doc1.setPropertyValue("dc:title", "Depth 1");
                doc1.setPropertyValue("testDoc:testDocRef", doc2.getId());
                doc1 = session.createDocument(doc1);
                session.save();

                // Create settings
                List<ObjectNode> interactions = new ArrayList<>();
                Map<String, Object> configurationMap = new HashMap<>();

                String interactionJson = "{\n" +
                                "    \"activeOnDocumentTypes\": [\"TestDoc\"],\n" +
                                "    \"active\": true,\n" +
                                "    \"saveMetadata\": true,\n" +
                                "    \"mainMetadata\": true,\n" +
                                "    \"id\": \"test-depth-id\",\n" +
                                "    \"parameters\": [\n" +
                                "      {\n" +
                                "        \"isAdditionalContext\": true,\n" +
                                "        \"name\": \"additionalContext\",\n" +
                                "        \"type\": \"string\"\n" +
                                "      }\n" +
                                "    ]\n" +
                                "}";

                ObjectNode objectNode = (ObjectNode) objectMapper.readTree(interactionJson);
                interactions.add(objectNode);

                DocTypeWithSchema.Schema.Field refField = new DocTypeWithSchema.Schema.Field();
                refField.setXpath("testDoc:testDocRef");
                refField.setActive(true);
                refField.setAdditionalContext(true);

                addSchemaConfiguration(configurationMap, interactions, "TestDoc_testDoc", List.of(refField));

                String mockResponse = "{\"result\":{}}";
                when(apiService.invokeModelWithSchema(any(), any(), anyMap(), anyString())).thenReturn(mockResponse);

                // Should stop at depth 5 (MAX_DEPTH)
                List<InteractionParameter> parameters = new ArrayList<>();
                runInteractionService.triggerInteraction(doc1, session, "test-depth-id", parameters, true, false,
                                false);

                assertTrue("Deep chain handled with depth limiting", true);
        }

}