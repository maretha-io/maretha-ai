/*
 * Copyright 2023 Maretha Solutions LLC - https://maretha.io.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.maretha.cp.core.listeners;

import io.maretha.cp.core.models.CpInteraction;
import io.maretha.cp.core.services.CPLocalSettingsServiceInterface;
import io.maretha.cp.core.stream.StreamDocumentProcessor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

import java.util.List;

import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.*;

public class DocumentChangeListener implements EventListener {
    public static final String DISABLE_DOCUMENT_CHANGE_LISTENER = "disableDocumentChangeListener";
    private static final Log log = LogFactory.getLog(DocumentChangeListener.class);
    String FILE_CONTENT = "file:content";

    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        CoreSession session = doc.getCoreSession();

        if (Boolean.TRUE.equals(doc.getContextData().get(DISABLE_DOCUMENT_CHANGE_LISTENER))) {
            return;
        }

        CPLocalSettingsServiceInterface cpSettingsService = Framework.getService(CPLocalSettingsServiceInterface.class);
        List<CpInteraction> interactionsForDocumentType = cpSettingsService.getActiveInteractionsForDocumentType(doc.getType());
        if (CollectionUtils.isEmpty(interactionsForDocumentType)) {
            log.debug("No interactions for this documentType" + doc.getType());
            return;
        }

        if (!doc.hasSchema("file")) {
            return;
        }

        if ((DOCUMENT_CREATED.equals(event.getName()) || DOCUMENT_IMPORTED.equals(event.getName()))
                && blobIsSupportedMimeType(doc)) {
            processDocument(doc, session);
            return;
        }

        if (BEFORE_DOC_UPDATE.equals(event.getName()) && blobIsDirty(doc) && blobIsSupportedMimeType(doc)) {
            processDocument(doc, session);
            return;
        }
        return;
    }

    private void processDocument(DocumentModel doc, CoreSession session) {
        BulkService bulkService = Framework.getService(BulkService.class);
        String commandId = bulkService.submit(
                new BulkCommand.Builder(StreamDocumentProcessor.ACTION_NAME, "ignored", "system")
                        .param("docId", doc.getId())
                        .useExternalScroller()
                        .build());
    }

    private boolean blobIsSupportedMimeType(DocumentModel doc) {
        try {
            Property blobProperty = doc.getProperty(FILE_CONTENT);
            Blob blob = (Blob) blobProperty.getValue();
            String mimeType = blob.getMimeType();
            return getSupportedMimeTyes().contains(mimeType);
        } catch (Exception ex) {
            return false;
        }
    }

    private String getSupportedMimeTyes() {
        return Framework.getProperty("cp.supported.mime-types", "");
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
