package io.maretha.cp.core.services.helper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Integration tests for BlobExtractor helper class.
 * Uses Nuxeo test framework for real DocumentModel instances.
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class BlobExtractorTest {

    @Inject
    CoreSession session;

    @Test
    public void testGetFileContent_WithFile() {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "testDoc", "File");
        Blob expectedBlob = new StringBlob("test content", "text/plain", "UTF-8");
        expectedBlob.setFilename("test.txt");
        doc.setPropertyValue("file:content", (java.io.Serializable) expectedBlob);
        doc = session.createDocument(doc);
        session.save();

        // Act
        Blob result = BlobExtractor.getFileContent(doc);

        // Assert
        assertNotNull("Should return blob", result);
        assertEquals("Should return correct filename", "test.txt", result.getFilename());
    }

    @Test
    public void testGetFileContent_NoFile() {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "emptyDoc", "File");
        doc = session.createDocument(doc);
        session.save();

        // Act
        Blob result = BlobExtractor.getFileContent(doc);

        // Assert
        assertNull("Should return null when no file", result);
    }

    @Test
    public void testGetAttachments_WithFiles() {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "docWithAttachments", "File");

        List<Map<String, Object>> files = new ArrayList<>();
        Map<String, Object> file1 = new HashMap<>();
        file1.put("file", new StringBlob("attachment1", "application/pdf"));
        files.add(file1);

        Map<String, Object> file2 = new HashMap<>();
        file2.put("file", new StringBlob("attachment2", "image/png"));
        files.add(file2);

        doc.setPropertyValue("files:files", (java.io.Serializable) files);
        doc = session.createDocument(doc);
        session.save();

        // Act
        List<Blob> result = BlobExtractor.getAttachments(doc);

        // Assert
        assertNotNull("Should return list", result);
        assertEquals("Should return 2 attachments", 2, result.size());
    }

    @Test
    public void testGetAttachments_NoFilesSchema() {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "simpleDoc", "Workspace");
        doc = session.createDocument(doc);
        session.save();

        // Act
        List<Blob> result = BlobExtractor.getAttachments(doc);

        // Assert
        assertNotNull("Should return empty list", result);
        assertTrue("Should be empty", result.isEmpty());
    }

    @Test
    public void testGetAttachmentsMap_CombinesBlobs() {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "combinedDoc", "File");

        // Set main file
        Blob mainBlob = new StringBlob("main content", "application/pdf");
        mainBlob.setFilename("main.pdf");
        doc.setPropertyValue("file:content", (java.io.Serializable) mainBlob);

        // Set attachments
        List<Map<String, Object>> files = new ArrayList<>();
        Map<String, Object> file1 = new HashMap<>();
        Blob attachment = new StringBlob("attachment", "image/png");
        attachment.setFilename("image.png");
        file1.put("file", attachment);
        files.add(file1);

        doc.setPropertyValue("files:files", (java.io.Serializable) files);
        doc = session.createDocument(doc);
        session.save();

        // Act
        Map<String, Blob> result = BlobExtractor.getAttachmentsMap(doc);

        // Assert
        assertNotNull("Should return map", result);
        assertEquals("Should contain 2 blobs", 2, result.size());
    }

    @Test
    public void testIsImage_ImageMimeType() {
        // Arrange
        Blob blob = new StringBlob("image data", "image/jpeg");

        // Act
        boolean result = BlobExtractor.isImage(blob);

        // Assert
        assertTrue("Should detect image MIME type", result);
    }

    @Test
    public void testIsImage_NonImageMimeType() {
        // Arrange
        Blob blob = new StringBlob("pdf data", "application/pdf");

        // Act
        boolean result = BlobExtractor.isImage(blob);

        // Assert
        assertFalse("Should not detect PDF as image", result);
    }

    @Test
    public void testIsImage_NullMimeType() {
        // Arrange
        Blob blob = new StringBlob("data");
        // StringBlob with single arg has null MIME type

        // Act
        boolean result = BlobExtractor.isImage(blob);

        // Assert
        assertFalse("Should return false for null MIME type", result);
    }
}
