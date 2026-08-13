package io.maretha.cp.core.events;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

import static io.maretha.cp.core.events.BulkEventListener.BULK_INTERACTIONS_EVENT;
import static io.maretha.cp.core.services.RunInteractionService.FILE_CONTENT;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BEFORE_DOC_UPDATE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_IMPORTED;

public class InteractionsEventListener implements EventListener {

    public static final String DISABLE_INTERACTIONS_LISTENER = "disableInteractionsListener";
    public static final String ORIGINAL_EVENT_NAME = "originalEventName";
    public static final String PICTURE_VIEWS_GENERATION_DONE_EVENT = "pictureViewsGenerationDone";
    private static final Log log = LogFactory.getLog(InteractionsEventListener.class);

    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        CoreSession session = doc.getCoreSession();

        if (Boolean.TRUE.equals(doc.getContextData().get(DISABLE_INTERACTIONS_LISTENER))) {
            return;
        }

        if (!doc.hasSchema("file")) {
            return;
        }

        doc.putContextData(DISABLE_INTERACTIONS_LISTENER, Boolean.TRUE);
        doc.putContextData(BULK_INTERACTIONS_EVENT, Boolean.TRUE);

        if ((DOCUMENT_CREATED.equals(event.getName())
                || DOCUMENT_IMPORTED.equals(event.getName())
                || PICTURE_VIEWS_GENERATION_DONE_EVENT.equals(event.getName()))) {
            fireEvent(session, doc, event.getName());
            return;
        }

        if (BEFORE_DOC_UPDATE.equals(event.getName()) && blobIsDirty(doc)) {
            fireEvent(session, doc, event.getName());
        }

    }

    private void fireEvent(CoreSession session, DocumentModel document, String originalEventName) {
        DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), document);
        ctx.setProperty(ORIGINAL_EVENT_NAME, originalEventName);
        Event event = ctx.newEvent(BulkEventListener.BULK_INTERACTIONS_EVENT);
        Framework.getService(EventService.class).fireEvent(event);
    }

    private boolean blobIsDirty(DocumentModel doc) {
        try {
            Property blobProperty = doc.getProperty(FILE_CONTENT);
            Blob blob = (Blob) blobProperty.getValue();
            if (blob != null && blobProperty.isDirty()) {
                return true;
            }
        } catch (PropertyException ex) {
            return false;
        }
        return false;
    }
}
