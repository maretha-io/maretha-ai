package io.maretha.cp.core.operations;

import io.maretha.cp.core.services.CPServiceInterface;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.util.Map;

import static io.maretha.cp.core.listeners.DocumentChangeListener.FILE_CONTENT;

/**
 *
 */
@Operation(id = RuntInteractionOperation.ID, category = Constants.CAT_DOCUMENT, label = "Run Interactions", description = "")
public class RuntInteractionOperation {

    public static final String ID = "RunInteractionOperation";
    @Context
    protected CoreSession session;
    @Param(name = "data")
    protected Map<String, String> data;
    @Param(name = "interactionId")
    protected String interactionId;

    @OperationMethod
    public String run(DocumentModel doc) throws IOException {
        Property blobProperty = doc.getProperty(FILE_CONTENT);
        Blob blob = (Blob) blobProperty.getValue();
        data.put("text", getText(blob));
        return Framework.getService(CPServiceInterface.class).getInteractionResult(interactionId, data);
    }

    private String getText(Blob blob) throws IOException {
        String text = "";
        if (blob != null) {
            // Extract text based on the blob type (Word, PDF, etc.)
            String mimeType = blob.getMimeType();
            if (mimeType.equals("application/pdf")) {
                // Use PDFBox to extract text from PDF
                try (PDDocument pdfDocument = PDDocument.load(blob.getStream())) {
                    PDFTextStripper pdfStripper = new PDFTextStripper();
                    text = pdfStripper.getText(pdfDocument);
                }
            } else if (mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                // Use Apache POI to extract text from Word document
                try (XWPFDocument doc = new XWPFDocument(blob.getStream())) {
                    XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
                    text = extractor.getText();
                }
            } else {
                throw new RuntimeException("Cannot extract text from document");
            }
        }
        return removeRepetitiveCharacters(text);
    }

    protected String removeRepetitiveCharacters(String text) {
        return text.replaceAll("(\\s*(.)\\s*)\\1+", "$1");
    }


}
