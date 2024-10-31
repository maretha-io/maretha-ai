package io.maretha.cp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.operations.GetInteractionSettingsOperation;
import io.maretha.cp.core.operations.SaveInteractionSettingsOperation;
import io.maretha.cp.core.services.APIService;
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
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

    @Mock
    @RuntimeService
    APIService apiService;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldReturnInteractionSettingsFromAPI() throws OperationException {
        Interaction interaction = Interaction.builder().id("test").build();
        when(apiService.getInteractions()).thenReturn(List.of(interaction));
        OperationContext ctx = new OperationContext(session);
        Collection<Interaction> interactions = (Collection<Interaction>) automationService.run(ctx, GetInteractionSettingsOperation.ID);
        assertNotNull(interactions);
        assertEquals(interaction.getId(), interactions.stream().findFirst().get().getId());
    }

    @Test
    public void shouldReturnInteractionSettingsFromDocument() throws OperationException {
        Interaction interaction = Interaction.builder().id("test").build();
        ObjectNode objectNode = mapper.convertValue(interaction, ObjectNode.class);
        repositoryService.saveInteractionSettings(session, List.of(objectNode));
        txFeature.nextTransaction();
        OperationContext ctx = new OperationContext(session);
        Collection<Interaction> interactions = (Collection<Interaction>) automationService.run(ctx, GetInteractionSettingsOperation.ID);
        assertNotNull(interactions);
        assertEquals(interaction.getId(), interactions.stream().findFirst().get().getId());
    }

    @Test
    public void shouldSaveInteractionSettings() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        Interaction interaction = Interaction.builder().id("test").build();
        Interaction interaction_2 = Interaction.builder().id("test_2").build();
        ObjectNode interaction_1_json = mapper.convertValue(interaction, ObjectNode.class);
        ObjectNode interaction_2_json = mapper.convertValue(interaction_2, ObjectNode.class);
        ctx.put("interactions", List.of(interaction_1_json, interaction_2_json));
        DocumentModel savedDocument = (DocumentModel) automationService.run(ctx, SaveInteractionSettingsOperation.ID);
        assertNotNull(savedDocument);
        Settings interactionSettings = savedDocument.getAdapter(Settings.class);
        int expectedSize = 2;
        assertEquals(expectedSize, interactionSettings.getInteractions().size());
        assertEquals(interaction.getId(), interactionSettings.getInteractions().get(0).getId());
    }


}
