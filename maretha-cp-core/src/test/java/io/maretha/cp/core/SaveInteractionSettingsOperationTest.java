package io.maretha.cp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        ctx.put("awsS3AccessEnabled", true);
        DocumentModel savedDocument = (DocumentModel) automationService.run(ctx, SaveInteractionSettingsOperation.ID);
        assertNotNull(savedDocument);
        Settings settings = savedDocument.getAdapter(Settings.class);
        int expectedSize = 2;
        assertEquals(expectedSize, settings.getInteractions().size());
        assertEquals(interaction_1.getId(), settings.getInteractions().get(0).getId());
        assertTrue(settings.isAwsS3AccessEnabled());
    }


}
