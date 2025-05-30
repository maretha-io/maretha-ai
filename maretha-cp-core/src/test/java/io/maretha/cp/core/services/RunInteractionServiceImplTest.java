package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.InteractionParameter;
import io.maretha.cp.core.models.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.SCHEMA_SETTINGS;
import static io.maretha.cp.core.services.RunInteractionService.FILE_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, RuntimeFeature.class, PlatformFeature.class, MockitoFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
@Deploy("org.nuxeo.ecm.platform.tag")
@Deploy("io.maretha.cp.core.maretha-cp-core-test:OSGI-INF/cp-test-doc-schema-contrib-test.xml")
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
    public void shouldSaveMetadataArraysAndTags() throws JsonProcessingException {
        DocumentModel document = createDocument();
        createSettingsDocument();

        Settings settingsDocument = Framework.getService(RepositoryService.class).getSavedSettings(session, false);

        String mockResponse = "{\"result\":{\"result\":" +
                "{\"dc:title\":\"Green Bird on Branch\"," +
                "\"dc:description\":\"A small green bird with speckled breast perched on a tree branch in a lush forest setting.\"," +
                "\"dc:language\":\"en\"," +
                "\"testDoc:testStringArray\":[\"test-111\", \"test-123\"]," +
                "\"nxtag:tags\":[\"tag-1\", \"tag-2\", \"tag-1\"]," +
                "\"dc:subjects\":[]" +
                "}" +
                "}}";

        when(apiService.getInteractionResultWithResultSchema(anyString(), anyMap(), anyString(), anyBoolean())).thenReturn(mockResponse);
        when(apiService.uploadFileToStore(anyObject())).thenReturn("vertesia-store-id");
        when(apiService.getDocumentEmbeddings(anyString(), anyString())).thenReturn(new double[]{});

        List<InteractionParameter> parameters = settingsDocument.getInteractions().get(0).getParameters();
        document = runInteractionService.uploadFileAndSetStoreId(document, session, false);
        runInteractionService.triggerInteraction(document, session, "interactionID", parameters, true, true);


        DocumentModel updatedDoc = session.getDocument(document.getRef());
        List<String> testStringArray = (List<String>) updatedDoc.getPropertyValue("testDoc:testStringArray");
        assertEquals(List.of("test-111", "test-123"), testStringArray);

        List<Map<String, String>> tags = (List<Map<String, String>>) updatedDoc.getPropertyValue("nxtag:tags");
        assertEquals(List.of(Map.of("label", "tag-1", "username", "system"), Map.of("label", "tag-2", "username", "system")), tags);
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

        DocTypeWithSchema.Schema schemaSettingsTestDoc = new DocTypeWithSchema.Schema();
        schemaSettingsTestDoc.setName("testDoc");
        ArrayList<DocTypeWithSchema.Schema.Field> fields = new ArrayList<>();

        DocTypeWithSchema.Schema.Field field = new DocTypeWithSchema.Schema.Field();
        field.setMultiValue(true);
        field.setXpath("testDoc:testStringArray");
        field.setType("stringList");
        field.setActive(true);
        field.setInIsolation(true);

        fields.add(field);
        schemaSettingsTestDoc.setFields(fields);
        configurationMap.put(SCHEMA_SETTINGS, schemaSettingsTestDoc);

        Framework.getService(RepositoryService.class).saveInteractionSettings(session, interactions, configurationMap);
        txFeature.nextTransaction();

        DocTypeWithSchema.Schema schemaSettingsTags = new DocTypeWithSchema.Schema();
        schemaSettingsTags.setName("facetedTag");
        ArrayList<DocTypeWithSchema.Schema.Field> tagFields = new ArrayList<>();

        DocTypeWithSchema.Schema.Field tagField = new DocTypeWithSchema.Schema.Field();
        tagField.setMultiValue(true);
        tagField.setXpath("nxtag:tags");
        tagField.setType("tagList");
        tagField.setActive(true);
        tagField.setInIsolation(true);

        tagFields.add(tagField);
        schemaSettingsTags.setFields(tagFields);
        configurationMap.put(SCHEMA_SETTINGS, schemaSettingsTags);

        Framework.getService(RepositoryService.class).saveInteractionSettings(session, interactions, configurationMap);
        txFeature.nextTransaction();
    }

}