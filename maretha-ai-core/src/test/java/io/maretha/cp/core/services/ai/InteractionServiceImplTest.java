package io.maretha.cp.core.services.ai;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(FeaturesRunner.class)
@Features({ MockitoFeature.class })
public class InteractionServiceImplTest {

    @Mock
    DocumentModel doc;

    @Test
    public void testProcessParamsAndBlobs() throws Exception {
        // GIVEN
        Map<String, Object> paramsData = new HashMap<>();
        Blob pdfBlob = new StringBlob("PDF Content", "application/pdf");
        pdfBlob.setFilename("test.pdf");
        paramsData.put("attachment1", pdfBlob);

        List<Blob> blobList = new ArrayList<>();
        Blob imageBlob = new StringBlob("Fake Image Bytes", "image/jpeg");
        imageBlob.setFilename("test.jpg");
        blobList.add(imageBlob);
        paramsData.put("gallery", blobList);

        paramsData.put("simpleString", "value");

        List<Message> userMessages = new ArrayList<>();

        when(doc.getPropertyValue("files:files")).thenReturn(null); // Mock getBlob(doc) behavior

        // WHEN
        InteractionServiceImpl.processParamsAndBlobs(doc, paramsData, userMessages);

        // THEN
        // We expect:
        // 1. PDF Message (if parsed, otherwise error logged but loop continues)
        // 2. Image Message (if valid)
        // 3. JSON Params Message

        // Note: StringBlob bytes might not be valid JPEG/PDF, so internal extraction
        // might log errors and NOT add messages.
        // We should check that placeholders were set in paramsData.

        assertTrue("Placeholder should replace Blob",
                paramsData.get("attachment1").toString().contains("Content attached as test.pdf"));
        assertTrue("Placeholder should replace List", paramsData.get("gallery").toString().contains("Values list of"));

        // Check params message was added
        boolean paramsMessageAdded = userMessages.stream()
                .anyMatch(msg -> {
                    try {
                        String text = msg.content().get(0).text();
                        return text.contains("simpleString") && text.contains("[Content attached as");
                    } catch (Exception e) {
                        return false;
                    }
                });
        assertTrue("Params JSON message should be added", paramsMessageAdded);
    }

    @Test
    public void testImageBlobCreatesImageMessage() throws Exception {
        // GIVEN - Create a minimal valid image blob
        Map<String, Object> paramsData = new HashMap<>();

        // Create a minimal 1x1 pixel JPEG (base64 decoded)
        byte[] minimalJpeg = new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46,
                0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06,
                0x05, 0x08, 0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D,
                0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F,
                0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C,
                0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30, 0x31, 0x34, 0x34, 0x34,
                0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32,
                (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01,
                0x01, 0x11, 0x00, (byte) 0xFF, (byte) 0xC4, 0x00, 0x14, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x03, (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00,
                0x00, 0x3F, 0x00, 0x37, (byte) 0xFF, (byte) 0xD9
        };

        Blob imageBlob = new org.nuxeo.ecm.core.api.impl.blob.ByteArrayBlob(minimalJpeg, "image/jpeg");
        imageBlob.setFilename("test-image.jpg");
        paramsData.put("photo", imageBlob);

        List<Message> userMessages = new ArrayList<>();
        when(doc.getPropertyValue("files:files")).thenReturn(null);

        // WHEN
        InteractionServiceImpl.processParamsAndBlobs(doc, paramsData, userMessages);

        // THEN
        // Should have at least 2 messages: 1 for image, 1 for params JSON
        assertTrue("Should have messages", userMessages.size() >= 2);

        // Find the image message
        boolean hasImageMessage = userMessages.stream()
                .anyMatch(msg -> {
                    try {
                        return msg.content().get(0).image() != null;
                    } catch (Exception e) {
                        return false;
                    }
                });
        assertTrue("Should contain an image message", hasImageMessage);

        // Verify placeholder was set
        assertTrue("Placeholder should replace image Blob",
                paramsData.get("photo").toString().contains("Content attached as test-image.jpg"));
    }

    @Test
    public void testPdfBlobCreatesTextMessage() throws Exception {
        // GIVEN - Create a minimal valid PDF
        Map<String, Object> paramsData = new HashMap<>();

        // Minimal valid PDF with "Test" text
        String pdfContent = "%PDF-1.4\n" +
                "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n"
                +
                "4 0 obj<</Length 44>>stream\n" +
                "BT /F1 12 Tf 100 700 Td (Test Content) Tj ET\n" +
                "endstream endobj\n" +
                "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n" +
                "xref\n0 6\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\n" +
                "0000000255 00000 n\n0000000351 00000 n\ntrailer<</Size 6/Root 1 0 R>>\nstartxref\n425\n%%EOF";

        Blob pdfBlob = new StringBlob(pdfContent, "application/pdf");
        pdfBlob.setFilename("test-document.pdf");
        paramsData.put("document", pdfBlob);

        List<Message> userMessages = new ArrayList<>();
        when(doc.getPropertyValue("files:files")).thenReturn(null);

        // WHEN
        InteractionServiceImpl.processParamsAndBlobs(doc, paramsData, userMessages);

        // THEN
        // Should have at least 2 messages: 1 for PDF text, 1 for params JSON
        assertTrue("Should have messages", userMessages.size() >= 2);

        // Find the PDF text message
        boolean hasPdfTextMessage = userMessages.stream()
                .anyMatch(msg -> {
                    try {
                        String text = msg.content().get(0).text();
                        return text != null && text.contains("<DOCUMENT name=\"test-document.pdf\">");
                    } catch (Exception e) {
                        return false;
                    }
                });
        assertTrue("Should contain a PDF text message with document tag", hasPdfTextMessage);

        // Verify placeholder was set
        assertTrue("Placeholder should replace PDF Blob",
                paramsData.get("document").toString().contains("Content attached as test-document.pdf"));
    }

    @Test
    public void testMixedBlobsAndRegularParams() throws Exception {
        // GIVEN - Mix of images, PDFs, and regular parameters
        Map<String, Object> paramsData = new HashMap<>();

        // Add a simple image
        byte[] simpleImage = new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46,
                0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xD9
        };
        Blob imageBlob = new org.nuxeo.ecm.core.api.impl.blob.ByteArrayBlob(simpleImage, "image/png");
        imageBlob.setFilename("chart.png");
        paramsData.put("chart", imageBlob);

        // Add regular params
        paramsData.put("title", "Analysis Report");
        paramsData.put("count", 42);
        paramsData.put("metadata", Map.of("author", "Test User", "date", "2025-12-05"));

        List<Message> userMessages = new ArrayList<>();
        when(doc.getPropertyValue("files:files")).thenReturn(null);

        // WHEN
        InteractionServiceImpl.processParamsAndBlobs(doc, paramsData, userMessages);

        // THEN
        // Should have at least 2 messages
        assertTrue("Should have multiple messages", userMessages.size() >= 2);

        // Verify image message exists
        boolean hasImageMessage = userMessages.stream()
                .anyMatch(msg -> {
                    try {
                        return msg.content().get(0).image() != null;
                    } catch (Exception e) {
                        return false;
                    }
                });
        assertTrue("Should contain image message", hasImageMessage);

        // Verify params JSON contains regular params and placeholder
        boolean hasParamsMessage = userMessages.stream()
                .anyMatch(msg -> {
                    try {
                        String text = msg.content().get(0).text();
                        return text.contains("Analysis Report") &&
                                text.contains("Test User") &&
                                text.contains("[Content attached as chart.png]");
                    } catch (Exception e) {
                        return false;
                    }
                });
        assertTrue("Should contain params message with regular data and blob placeholder", hasParamsMessage);
    }
}
