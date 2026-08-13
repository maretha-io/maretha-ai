package io.maretha.cp.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
@Deploy("io.maretha.cp.core.maretha-cp-core-test:OSGI-INF/cp-test-doc-schema-contrib-test.xml")
public class RepositoryServiceImplTest {

    @Inject
    CoreSession session;

    @Inject
    TransactionalFeature txFeature;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldSaveAndRetrieveNewToggleFields() throws JsonProcessingException {
        // GIVEN a list of interactions with the new toggle fields set to true
        List<ObjectNode> interactions = new ArrayList<>();
        Map<String, Object> configurationMap = new HashMap<>();

        String interactionJson = "{\n" +
                "    \"activeOnDocumentTypes\": [\"File\"],\n" +
                "    \"description\": \"Test new toggles\",\n" +
                "    \"active\": true,\n" +
                "    \"version\": \"1\",\n" +
                "    \"chat\": false,\n" +
                "    \"name\": \"Test Interaction\",\n" +
                "    \"saveMetadata\": false,\n" +
                "    \"id\": \"test-id-123\",\n" +
                "    \"sendParentsContext\": true,\n" +
                "    \"sendAttachments\": true,\n" +
                "    \"requiresUserInput\": true,\n" +
                "    \"parameters\": []\n" +
                "}";

        ObjectNode objectNode = (ObjectNode) objectMapper.readTree(interactionJson);
        interactions.add(objectNode);

        // WHEN saving the settings
        RepositoryService repositoryService = Framework.getService(RepositoryService.class);
        repositoryService.saveInteractionSettings(session, interactions, configurationMap);

        txFeature.nextTransaction();

        // THEN the saved settings should reflect the new boolean values
        Settings settings = repositoryService.getSavedSettings(session, false);
        assertNotNull(settings);
        assertNotNull(settings.getInteractions());
        assertTrue(settings.getInteractions().size() > 0);

        Interaction savedInteraction = settings.getInteractions().get(0);
        assertTrue("sendParentsContext should be true", savedInteraction.isSendParentsContext());
        assertTrue("sendAttachments should be true", savedInteraction.isSendAttachments());
        assertTrue("requiresUserInput should be true", savedInteraction.isRequiresUserInput());
    }
}
