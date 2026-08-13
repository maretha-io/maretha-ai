package io.maretha.cp.core.events;

import io.maretha.cp.core.bulk.actions.BulkInteractionAction;
import io.maretha.cp.core.models.EventWithInteraction;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.services.RepositoryService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.maretha.cp.core.events.InteractionsEventListener.ORIGINAL_EVENT_NAME;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_DOCUMENT_TYPE;
import static io.maretha.cp.core.services.utils.AuditUtils.logAIAudit;

public class BulkEventListener implements PostCommitEventListener {
    public static final String DISABLE_BULK_LISTENER = "disableInteractionListener";
    public static final String BULK_INTERACTIONS_EVENT = "bulkInteractionsEvent";
    private final RepositoryService repositoryService = Framework.getService(RepositoryService.class);
    private final Log log = LogFactory.getLog(BulkEventListener.class);
    private final AuditLogger auditLogger = Framework.getService(AuditLogger.class);

    @Override
    public void handleEvent(EventBundle events) {
        Settings savedSettings = repositoryService.getSavedSettings(null, true);
        List<EventWithInteraction> eventsSettings = savedSettings.getEventsSettings();
        if (CollectionUtils.isEmpty(eventsSettings)) {
            log.error(SETTINGS_DOCUMENT_TYPE + " - eventsSettings is empty.");
            return;
        }
        //TODO Collect all docIds by event and interaction to trigger
        events.forEach(ev -> handleEvent(ev, savedSettings));
    }

    public void handleEvent(Event event, Settings savedSettings) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }
        DocumentModel doc = ((DocumentEventContext) ctx).getSourceDocument();
        if (doc == null) {
            return;
        }

        Boolean block = (Boolean) doc.getContextData().get(DISABLE_BULK_LISTENER);
        if (Boolean.TRUE.equals(block)) {
            return;
        }

        String originalEventName = (String) event.getContext().getProperty(ORIGINAL_EVENT_NAME);

        Set<EventWithInteraction> eventsWithInteractions = savedSettings.getEventsSettings().stream()
                .filter(ev -> ev.getDocType().equals(doc.getType()))
                .filter(ev -> ev.getEvents().contains(originalEventName))
                .filter(EventWithInteraction::isActive)
                .collect(Collectors.toSet());

        eventsWithInteractions.forEach(ev -> {
            List<String> interactions = ev.getInteractions();
            interactions.forEach(interactionId -> {
                String nxqlQuery = String.format("SELECT * FROM Document WHERE ecm:uuid = '%s' AND ecm:isTrashed = 0 AND ecm:isProxy = 0 AND ecm:isVersion = 0 " +
                        " AND file:content IS NOT NULL", doc.getId());
                Optional<Interaction> optionalInteraction = savedSettings.getInteractions().stream()
                        .filter(interaction -> interaction.getId().equals(interactionId)).findFirst();
                if (optionalInteraction.isPresent()) {
                    doc.putContextData(DISABLE_BULK_LISTENER, true);
                    Interaction interaction = optionalInteraction.get();
                    BulkCommand.Builder builder = new BulkCommand.Builder(BulkInteractionAction.ACTION_NAME, nxqlQuery, "system")
                            .param("interactionId", interactionId)
                            .param("parameters", (Serializable) interaction.getParameters())
                            .param("saveMetadata", interaction.isSaveMetadata())
                            .param("saveMetadataModeOverride", false);
                    BulkCommand command = builder.build();
                    BulkService bulkService = Framework.getService(BulkService.class);
                    String commandId = bulkService.submit(command);
                    log.debug("InteractionEventListener-> running interaction: " + interaction.getId());
                    log.debug("InteractionEventListener created commandId: " + commandId);
                    logAIAudit(ctx.getCoreSession(), doc, commandId, "AI Interaction triggered by event: " + originalEventName);
                }
            });
        });

    }
}
