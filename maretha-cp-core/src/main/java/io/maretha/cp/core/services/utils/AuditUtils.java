package io.maretha.cp.core.services.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.impl.LogEntryImpl;
import org.nuxeo.runtime.api.Framework;

import java.util.Date;
import java.util.List;

public class AuditUtils {
    private static Log log = LogFactory.getLog(AuditUtils.class);
    private static final String AUDIT_LOG_EVENT_CATEGORY = "document";

    public static void logAIAudit(CoreSession session, DocumentModel doc, String commandId, String details) {
        String comment = details;
        if (StringUtils.isNotEmpty(commandId)) {
            comment = comment + ". bulkCommandId: " + commandId;
        }

        LogEntry entry = new LogEntryImpl();
        entry.setEventId("AI");
        entry.setCategory(AUDIT_LOG_EVENT_CATEGORY);
        entry.setDocUUID(doc.getId());
        entry.setDocPath(doc.getPathAsString());
        entry.setDocType(doc.getType());
        entry.setPrincipalName(session.getPrincipal().getName());
        entry.setRepositoryId(session.getRepositoryName());
        entry.setComment(comment);
        entry.setEventDate(new Date());

        getAuditLogger().addLogEntries(List.of(entry));
    }

    public static AuditLogger getAuditLogger() {
        AuditLogger auditLogger = Framework.getService(AuditLogger.class);
        if (auditLogger == null) {
            log.error("AuditLogger not available");
        }
        return auditLogger;
    }
}
