package io.maretha.cp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.models.InteractionResponse;
import io.maretha.cp.core.operations.SaveInteractionResponseOperation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import jakarta.inject.Inject;
import java.util.List;

import static io.maretha.cp.core.services.RepositoryService.RESULTS_SCHEMA_PATH;
import static io.maretha.cp.core.services.RunInteractionService.FILE_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, RuntimeFeature.class, CoreFeature.class, PlatformFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
@Deploy("org.nuxeo.ecm.relations")
@Deploy("org.nuxeo.ecm.relations.api")
@Deploy("io.maretha.cp.core.maretha-cp-core:test-relation-core-contrib.xml")
public class SaveInteractionResponseOperationTest {
    @Inject
    CoreSession session;

    @Inject
    AutomationService automationService;

    @Inject
    TransactionalFeature txFeature;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldSaveInteractionResponseAsMetadata() throws OperationException {
        //GIVEN
        DocumentModel document = createDocument();
        //AND A list of interactions to save
        String mockResponse = "this is a test";
        InteractionResponse interactionResponse = InteractionResponse.builder()
                .id("test")
                .response(mockResponse)
                .interactionId("interaction-id-test")
                .documentId(document.getId())
                .parameters(List.of())
                .build();
        boolean saveOnMetadata = true;
        ObjectNode jsonNodes = mapper.convertValue(interactionResponse, ObjectNode.class);
        List<ObjectNode> interactionsToSave = List.of(jsonNodes);

        //WHEN the operation is called
        OperationContext ctx = new OperationContext(session);
        ctx.put("interactions", interactionsToSave);
        ctx.put("saveOnMetadata", saveOnMetadata);
        ctx.setInput(document);
        DocumentModel response = (DocumentModel) automationService.run(ctx, SaveInteractionResponseOperation.ID);
        //THEN
        assertNotNull(response);
        DocumentModel returnedDocument = session.getDocument(document.getRef());
        Object interactionResponsesReturned = returnedDocument.getPropertyValue(RESULTS_SCHEMA_PATH);
        List<InteractionResponse> interactionsList = mapper.convertValue(interactionResponsesReturned, new TypeReference<>() {
        });
        assertEquals(interactionResponse.getId(), interactionsList.get(0).getId());
    }

    @Test
    public void shouldSaveInteractionResponseAsRelatedDocument() throws OperationException {
        //GIVEN
        DocumentModel document = createDocument();
        txFeature.nextTransaction();
        //AND A list of interactions to save
        String mockResponse = "this is a test";
        InteractionResponse interactionResponse = InteractionResponse.builder()
                .id("test")
                .response(mockResponse)
                .interactionId("interaction-id-test")
                .documentId(document.getId())
                .parameters(List.of())
                .build();
        boolean saveOnMetadata = false;
        ObjectNode jsonNodes = mapper.convertValue(interactionResponse, ObjectNode.class);
        List<ObjectNode> interactionsToSave = List.of(jsonNodes);

        //WHEN the operation is called
        OperationContext ctx = new OperationContext(session);
        ctx.put("interactions", interactionsToSave);
        ctx.put("saveOnMetadata", saveOnMetadata);
        ctx.setInput(document);
        DocumentModel response = (DocumentModel) automationService.run(ctx, SaveInteractionResponseOperation.ID);
        //THEN
        assertNotNull(response);

        OperationContext relationsCtx = new OperationContext(session);
        relationsCtx.put("outgoing", true);
        relationsCtx.put("predicate", "http://www.nuxeo.org/ecm/schemas/relation#isRelatedTo");
        relationsCtx.setInput(document);
        DocumentModelList docRelations = (DocumentModelList) automationService.run(relationsCtx, "Relations.GetRelations");

        assertNotNull(docRelations);
        DocumentModel returnedDocument = docRelations.get(0);
        Object interactionResponsesReturned = returnedDocument.getPropertyValue(RESULTS_SCHEMA_PATH);
        List<InteractionResponse> interactionsList = mapper.convertValue(interactionResponsesReturned, new TypeReference<>() {
        });
        assertEquals(interactionResponse.getId(), interactionsList.get(0).getId());
    }

    private DocumentModel createDocument() {
        DocumentModel doc = session.createDocumentModel("/", "testFile", "File");
        doc.setPropertyValue("dc:title", "Test Document");
        doc.setPropertyValue("dc:description", "This is a test description");
        doc.setPropertyValue(FILE_CONTENT, new StringBlob("Test", "text/plain"));
        doc = session.createDocument(doc);
        session.save();
        return doc;
    }
}
