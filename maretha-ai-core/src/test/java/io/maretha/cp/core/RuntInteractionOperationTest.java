package io.maretha.cp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.maretha.cp.core.operations.RuntInteractionOperation;
import io.maretha.cp.core.services.RunInteractionService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
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
import java.util.ArrayList;
import java.util.List;

import static io.maretha.cp.core.services.RunInteractionService.FILE_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.when;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, RuntimeFeature.class, MockitoFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
@Deploy("io.maretha.cp.core.maretha-cp-core-test:OSGI-INF/cp-bulk-action-contrib-test.xml")
public class RuntInteractionOperationTest {
    @Inject
    CoreSession session;

    @Inject
    AutomationService automationService;

    @Mock
    @RuntimeService
    RunInteractionService runInteractionService;

    @Inject
    TransactionalFeature txFeature;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldReturnInteractionResponse() throws OperationException {
        //GIVEN
        DocumentModel document = createDocument();
        List<ObjectNode> data = new ArrayList<>();
        String interactionId = "test-id";
        String mockResponse = mapper.convertValue("{\"response\": \"test\"}", String.class);
        //WHEN the service is mocked
        when(runInteractionService.addEmbeddings(document, session, true, false)).thenReturn(document);
        when(runInteractionService.triggerInteraction(document, session, interactionId, new ArrayList<>(), false, false, false)).thenReturn(mockResponse);

        //AND the operation is called
        OperationContext ctx = new OperationContext(session);
        ctx.put("interactionId", interactionId);
        ctx.put("data", data);
        ctx.setInput(document);
        String response = (String) automationService.run(ctx, RuntInteractionOperation.ID);
        //THEN
        assertNotNull(response);
        assertEquals("{\"response\": \"test\"}", response);
    }

    @Test
    public void shouldReturnInteractionResponseForFolder() throws OperationException {
        //GIVEN Folder exists
        DocumentModel folderDoc = session.createDocumentModel("/", "Folder", "Folder");
        folderDoc.setPropertyValue("dc:title", "Test Folder");
        folderDoc.setPropertyValue("dc:description", "This is a test description");
        folderDoc = session.createDocument(folderDoc);

        //And document
        DocumentModel document = session.createDocumentModel("/Folder", "testFile", "File");
        document.setPropertyValue("dc:title", "Test Document");
        document.setPropertyValue("dc:description", "This is a test description");
        document.setPropertyValue(FILE_CONTENT, new StringBlob("Test", "text/plain"));
        document = session.createDocument(document);

        List<ObjectNode> data = new ArrayList<>();
        String interactionId = "test-id";
        String mockResponse = mapper.convertValue("{\"response\": \"test\"}", String.class);
        //WHEN the service is mocked
        when(runInteractionService.addEmbeddings(any(), any(), anyBoolean(), anyBoolean())).thenReturn(document);

        //AND the operation is called
        OperationContext ctx = new OperationContext(session);
        ctx.put("interactionId", interactionId);
        ctx.put("data", data);
        ctx.setInput(folderDoc);
        String response = (String) automationService.run(ctx, RuntInteractionOperation.ID);
        //THEN
        assertNotNull(response);
    }

    private DocumentModel createDocument() {
        DocumentModel doc = session.createDocumentModel("/", "testFile", "File");
        doc.setPropertyValue("dc:title", "Test Document");
        doc.setPropertyValue("dc:description", "This is a test description");
        doc.setPropertyValue(FILE_CONTENT, new StringBlob("Test", "text/plain"));
        doc = session.createDocument(doc);
        session.save();
        txFeature.nextTransaction();
        return doc;
    }
}
