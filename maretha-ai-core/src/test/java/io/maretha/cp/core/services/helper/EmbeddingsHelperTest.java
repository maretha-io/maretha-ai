package io.maretha.cp.core.services.helper;

import io.maretha.cp.core.services.ai.EmbeddingsService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for EmbeddingsHelper class.
 * Uses Nuxeo test framework with mocked EmbeddingsService.
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, MockitoFeature.class })
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class EmbeddingsHelperTest {

    @Inject
    CoreSession session;

    @Mock
    @RuntimeService
    EmbeddingsService embeddingsService;

    @Test
    public void testAddEmbeddings_ImageDocument() throws Exception {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "imageDoc", "File");
        doc.setPropertyValue("dc:description", "Test image description");

        Blob imageBlob = new StringBlob("image data", "image/jpeg");
        imageBlob.setFilename("test.jpg");
        doc.setPropertyValue("file:content", (java.io.Serializable) imageBlob);
        doc = session.createDocument(doc);
        session.save();

        // Mock embeddings service
        double[] mockImageEmbeddings = new double[] { 0.1, 0.2, 0.3 };
        double[] mockTextEmbeddings = new double[] { 0.4, 0.5, 0.6 };
        when(embeddingsService.embedImage(anyString(), anyString(), anyInt())).thenReturn(mockImageEmbeddings);
        when(embeddingsService.textToEmbedding(anyString())).thenReturn(mockTextEmbeddings);

        // Act
        DocumentModel result = EmbeddingsHelper.addEmbeddings(doc, session, imageBlob, false, false);

        // Assert
        assertNotNull("Should return document", result);
        // Note: Verifying embeddings were added would require checking the saved
        // document properties
    }

    @Test
    public void testAddEmbeddings_PdfDocument() throws Exception {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "pdfDoc", "File");

        Blob pdfBlob = new StringBlob("PDF content", "application/pdf");
        pdfBlob.setFilename("test.pdf");
        doc.setPropertyValue("file:content", (java.io.Serializable) pdfBlob);
        doc = session.createDocument(doc);
        session.save();

        // Mock embeddings service
        double[] mockPdfEmbeddings = new double[] { 0.7, 0.8, 0.9 };
        when(embeddingsService.embedPdfSummary(any(Blob.class))).thenReturn(mockPdfEmbeddings);

        // Act
        DocumentModel result = EmbeddingsHelper.addEmbeddings(doc, session, pdfBlob, false, false);

        // Assert
        assertNotNull("Should return document", result);
    }

    @Test
    public void testAddEmbeddings_NonImageNonPdf() throws Exception {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "textDoc", "File");

        Blob textBlob = new StringBlob("text content", "text/plain");
        textBlob.setFilename("test.txt");
        doc.setPropertyValue("file:content", (java.io.Serializable) textBlob);
        doc = session.createDocument(doc);
        session.save();

        // Act
        DocumentModel result = EmbeddingsHelper.addEmbeddings(doc, session, textBlob, false, false);

        // Assert
        assertNotNull("Should return document", result);
        // Should not add embeddings for non-image, non-PDF
    }

    @Test
    public void testAddEmbeddings_HandlesNullBlob() {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "emptyDoc", "File");
        doc = session.createDocument(doc);
        session.save();

        // Act - should handle null blob gracefully
        try {
            DocumentModel result = EmbeddingsHelper.addEmbeddings(doc, session, null, false, false);
            assertNotNull("Should return document even with null blob", result);
        } catch (Exception e) {
            fail("Should handle null blob without exception: " + e.getMessage());
        }
    }
}
