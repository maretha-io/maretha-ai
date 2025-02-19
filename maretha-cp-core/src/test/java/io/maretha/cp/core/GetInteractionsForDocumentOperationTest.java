package io.maretha.cp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.operations.GetInteractionsForDocumentOperation;
import io.maretha.cp.core.services.RepositoryService;
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
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, RuntimeFeature.class, MockitoFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class GetInteractionsForDocumentOperationTest {

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
    public void shouldGetInteractionsForDocument() throws OperationException {
        //Given a File document
        DocumentModel doc = createDocument();
        OperationContext ctx = new OperationContext(session);

        //AND one Interaction that matches doc requirements
        Interaction interaction_1 = Interaction.builder()
                .id("test")
                .activeOnActionMenu(true)
                .activeOnFolder(false)
                .activeOnDocumentTypes(List.of("Document", "File"))
                .parameters(List.of())
                .isActive(true)
                .build();
        //AND one Interaction that does not match doc requirements
        Interaction interaction_2 = Interaction.builder()
                .id("test_2")
                .activeOnActionMenu(true)
                .activeOnFolder(true)
                .activeOnDocumentTypes(List.of("Folder"))
                .parameters(List.of())
                .isActive(false)
                .build();
        createInteractionSettings(List.of(interaction_1, interaction_2));

        //WHEN
        ctx.setInput(doc);

        Collection<Interaction> interactions = (Collection<Interaction>) automationService.run(ctx, GetInteractionsForDocumentOperation.ID);
        //THEN
        assertNotNull(interactions);
        int expectedSize = 1;
        assertEquals(expectedSize, interactions.size());
        assertEquals(interaction_1, interactions.stream().findFirst().get());
    }

    @Test
    public void shouldGetChatInteractionsForDocument() throws OperationException {
        //Given a File document
        DocumentModel doc = createDocument();
        OperationContext ctx = new OperationContext(session);

        //AND one Interaction that matches doc requirements
        Interaction interaction_1 = Interaction.builder()
                .id("test")
                .activeOnActionMenu(true)
                .activeOnFolder(false)
                .activeOnDocumentTypes(List.of("Document", "File"))
                .parameters(List.of())
                .isActive(true)
                .isChat(true)
                .build();
        //AND one Interaction that does not match doc requirements
        Interaction interaction_2 = Interaction.builder()
                .id("test_2")
                .activeOnActionMenu(true)
                .activeOnFolder(true)
                .activeOnDocumentTypes(List.of("Folder"))
                .parameters(List.of())
                .isActive(true)
                .isChat(true)
                .build();
        createInteractionSettings(List.of(interaction_1, interaction_2));

        //WHEN
        ctx.setInput(doc);
        ctx.put("isChat", true);

        Collection<Interaction> interactions = (Collection<Interaction>) automationService.run(ctx, GetInteractionsForDocumentOperation.ID);
        //THEN
        assertNotNull(interactions);
        int expectedSize = 1;
        assertEquals(expectedSize, interactions.size());
        assertEquals(interaction_1, interactions.stream().findFirst().get());
    }

    private List<ObjectNode> createInteractionSettings(List<Interaction> interactions) {
        List<ObjectNode> interactionsAsObjectNodes = interactions.stream()
                .map(interaction -> mapper.convertValue(interaction, ObjectNode.class))
                .collect(Collectors.toList());
        Map<String, Object> configuration = Map.of("awsS3AccessEnabled", true);
        repositoryService.saveInteractionSettings(session, interactionsAsObjectNodes, configuration);
        txFeature.nextTransaction();
        return interactionsAsObjectNodes;
    }

    private DocumentModel createDocument() {
        DocumentModel doc = session.createDocumentModel("/", "testFile", "File");
        doc.setPropertyValue("dc:title", "Test Document");
        doc.setPropertyValue("dc:description", "This is a test description");
        doc = session.createDocument(doc);
        session.save();
        return doc;
    }
}
