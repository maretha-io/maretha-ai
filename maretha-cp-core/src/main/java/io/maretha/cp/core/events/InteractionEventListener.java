package io.maretha.cp.core.events;

import io.maretha.cp.core.models.EventWithInteraction;
import io.maretha.cp.core.services.RepositoryService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class InteractionEventListener implements EventListener {
    public static final String DISABLE_LISTENER = "disableInteractionListener";
    private final RepositoryService repositoryService = Framework.getService(RepositoryService.class);
    private final Log log = LogFactory.getLog(InteractionEventListener.class);

    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }
        DocumentModel doc = ((DocumentEventContext) ctx).getSourceDocument();
        if (doc == null) {
            return;
        }
        CoreSession session = doc.getCoreSession();

        Boolean block = (Boolean) doc.getContextData().get(DISABLE_LISTENER);
        if (Boolean.TRUE.equals(block)) {
            return;
        }

        String docType = doc.getType();
        Map<String, List<EventWithInteraction>> eventsSettings = repositoryService.getSavedSettings(session).getEventsSettings();
        if (!Objects.isNull(eventsSettings) && eventsSettings.containsKey(docType)) {
            List<EventWithInteraction> events = eventsSettings.get(docType);
            Set<EventWithInteraction> eventsWithInteractions = events.stream()
                    .filter(ev -> ev.getEventName().equals(event.getName()))
                    .filter(EventWithInteraction::isActive)
                    .collect(Collectors.toSet());
            eventsWithInteractions.forEach(ev -> {
                //TODO BulkAction
                String interactionId = ev.getInteractionId();
                log.debug("InteractionEventListener-> running interaction: " + interactionId);
                doc.putContextData(DISABLE_LISTENER, true);
            });
        }
    }

}
