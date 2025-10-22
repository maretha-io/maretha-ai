package io.maretha.cp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.operations.GetSettingsOperation;
import io.maretha.cp.core.operations.SaveInteractionSettingsOperation;
import io.maretha.cp.core.services.RepositoryService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static io.maretha.cp.core.services.RepositoryService.SETTINGS_DOCUMENT_NAME;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_DOCUMENT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, RuntimeFeature.class, MockitoFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class GetInteractionSettingsOperationTest {

    @Inject
    CoreSession session;

    @Inject
    AutomationService automationService;

    @Inject
    RepositoryService repositoryService;

    @Inject
    TransactionalFeature txFeature;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldReturnInteractionSettingsFromAPI() throws OperationException {
        //GIVEN
        DocumentModel cpSettingsDoc = session.createDocumentModel("/", SETTINGS_DOCUMENT_NAME, SETTINGS_DOCUMENT_TYPE);
        cpSettingsDoc = session.createDocument(cpSettingsDoc);
        txFeature.nextTransaction();
        Interaction interaction = Interaction.builder().id("test").build();
        //WHEN
        OperationContext ctx = new OperationContext(session);
        //THEN
        Settings settings = (Settings) automationService.run(ctx, GetSettingsOperation.ID);
        assertNotNull(settings);
        assertEquals("1758639814291", settings.getInteractions().stream().findFirst().get().getId());
    }

    @Test
    public void shouldReturnInteractionSettingsFromDocument() throws OperationException {
        Interaction interaction = Interaction.builder().id("test").build();
        ObjectNode objectNode = mapper.convertValue(interaction, ObjectNode.class);
        Map<String, Object> configuration = Map.of("awsS3AccessEnabled", true);
        repositoryService.saveInteractionSettings(session, List.of(objectNode), configuration);
        txFeature.nextTransaction();
        OperationContext ctx = new OperationContext(session);
        Settings interactionsSettings = (Settings) automationService.run(ctx, GetSettingsOperation.ID);
        assertNotNull(interactionsSettings);
        assertEquals(interaction.getId(), interactionsSettings.getInteractions().stream().findFirst().get().getId());
        assertTrue(interactionsSettings.isAwsS3AccessEnabled());
    }

    @Test
    public void shouldSaveInteractionSettings() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        Interaction interaction = Interaction.builder().id("test").build();
        Interaction interaction_2 = Interaction.builder().id("test_2").build();
        ObjectNode interaction_1_json = mapper.convertValue(interaction, ObjectNode.class);
        ObjectNode interaction_2_json = mapper.convertValue(interaction_2, ObjectNode.class);
        ctx.put("interactions", List.of(interaction_1_json, interaction_2_json));
        ctx.put("awsS3AccessEnabled", false);
        Settings interactionSettings = (Settings) automationService.run(ctx, SaveInteractionSettingsOperation.ID);
        assertNotNull(interactionSettings);
        int expectedSize = 2;
        assertEquals(expectedSize, interactionSettings.getInteractions().size());
        assertEquals(interaction.getId(), interactionSettings.getInteractions().get(0).getId());
    }


}
