package io.maretha.cp.core.services.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
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

        LogEntry entry = LogEntry.builder("AI", new Date())
                .category(AUDIT_LOG_EVENT_CATEGORY)
                .docUUID(doc.getId())
                .docPath(doc.getPathAsString())
                .docType(doc.getType())
                .principalName(session.getPrincipal().getName())
                .repositoryId(session.getRepositoryName())
                .comment(comment)
                .build();

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
