package io.maretha.cp.core;

import io.maretha.cp.core.operations.GetDocumentTypesOperation;
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

import javax.inject.Inject;
import java.util.List;

import static io.maretha.cp.core.services.RepositoryService.RESULTS_DOCUMENT_NAME;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_DOCUMENT_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, RuntimeFeature.class, MockitoFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class GetDocumentTypesOperationTest {

    @Inject
    CoreSession session;

    @Inject
    AutomationService automationService;

    @Test
    public void shouldSaveInteractionSettings() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        List<String> documentTypes = (List<String>) automationService.run(ctx, GetDocumentTypesOperation.ID);
        assertNotNull(documentTypes);
        int expectedSize = 18;
        assertEquals(expectedSize, documentTypes.size());
        assertTrue(documentTypes.containsAll(List.of(RESULTS_DOCUMENT_NAME, SETTINGS_DOCUMENT_NAME)));
    }


}
