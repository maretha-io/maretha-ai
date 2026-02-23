package io.maretha.cp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.operations.SaveInteractionSettingsOperation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.AWS_S3_ACCESS_ENABLED;
import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.ES_SETTINGS;
import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.SCHEMA_SETTINGS;
import static io.maretha.cp.core.operations.SaveInteractionSettingsOperation.AI_SETTINGS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, RuntimeFeature.class, MockitoFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class SaveInteractionSettingsOperationTest {

    @Inject
    CoreSession session;

    @Inject
    AutomationService automationService;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldSaveInteractionSettings() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        Interaction interaction_1 = Interaction.builder().id("test").build();
        Interaction interaction_2 = Interaction.builder().id("test_2").build();
        ObjectNode interaction_1_json = mapper.convertValue(interaction_1, ObjectNode.class);
        ObjectNode interaction_2_json = mapper.convertValue(interaction_2, ObjectNode.class);
        ctx.put("interactions", List.of(interaction_1_json, interaction_2_json));
        ctx.put(AWS_S3_ACCESS_ENABLED, true);
        Settings settings = (Settings) automationService.run(ctx, SaveInteractionSettingsOperation.ID);
        assertNotNull(settings);
        int expectedSize = 2;
        assertEquals(expectedSize, settings.getInteractions().size());
        assertEquals(interaction_1.getId(), settings.getInteractions().get(0).getId());
        assertTrue(settings.isAwsS3AccessEnabled());
    }

    @Test
    public void shouldSaveSchemaSettings() throws OperationException {
        //GIVEN
        OperationContext ctx = new OperationContext(session);
        Interaction interaction_1 = Interaction.builder().id("test").build();
        Interaction interaction_2 = Interaction.builder().id("test_2").build();
        DocTypeWithSchema.Schema schemaSettings = new DocTypeWithSchema.Schema();
        String testSchemaName = "TestSchema";
        schemaSettings.setName(testSchemaName);
        schemaSettings.setFields(new ArrayList<>());
        ObjectNode schemaSettingsObj = mapper.convertValue(schemaSettings, ObjectNode.class);
        ObjectNode interaction_1_json = mapper.convertValue(interaction_1, ObjectNode.class);
        ObjectNode interaction_2_json = mapper.convertValue(interaction_2, ObjectNode.class);
        //WHEN
        ctx.put("interactions", List.of(interaction_1_json, interaction_2_json));
        ctx.put(AWS_S3_ACCESS_ENABLED, true);
        ctx.put(SCHEMA_SETTINGS, schemaSettingsObj);
        Settings settings = (Settings) automationService.run(ctx, SaveInteractionSettingsOperation.ID);
        //THEN
        assertNotNull(settings);
        Map<String, DocTypeWithSchema.Schema> schemaSettingsSaved = settings.getSchemaSettings();
        assertNotNull(schemaSettingsSaved);
        assertEquals(testSchemaName, schemaSettingsSaved.get(testSchemaName).getName());
    }

    @Test
    public void shouldSaveEsSettings() throws OperationException {
        //GIVEN
        OperationContext ctx = new OperationContext(session);
        Map<String, String> esSettings = Map.of("TEXT_WEIGHT", "1.0", "IMAGE_WEIGHT", "1.5");
        ObjectNode esSettingsObj = mapper.convertValue(esSettings, ObjectNode.class);

        //WHEN
        ctx.put(ES_SETTINGS, esSettingsObj);
        Settings settings = (Settings) automationService.run(ctx, SaveInteractionSettingsOperation.ID);
        //THEN
        assertNotNull(settings);
        Map<String, String> savedEsSettings = settings.getEsSettings();
        assertNotNull(savedEsSettings);
        assertEquals("1.0", savedEsSettings.get("TEXT_WEIGHT"));
        assertEquals("1.5", savedEsSettings.get("IMAGE_WEIGHT"));
    }

    @Test
    public void shouldSaveAISettings() throws OperationException {
        //GIVEN
        OperationContext ctx = new OperationContext(session);
        Map<String, String> aiSettingsMap = Map.of("apiUrl", "http://test.com");
        ObjectNode settingsAsJson = mapper.convertValue(aiSettingsMap, ObjectNode.class);

        //WHEN
        ctx.put(AI_SETTINGS, settingsAsJson);
        Settings settings = (Settings) automationService.run(ctx, SaveInteractionSettingsOperation.ID);
        //THEN
        assertNotNull(settings);
        Map<String, Object> savedSettings = settings.getAiSettings();
        assertNotNull(savedSettings);
        assertEquals("http://test.com", savedSettings.get("apiUrl"));
    }


}
