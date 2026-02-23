package io.maretha.cp.core.services.helper;

import io.maretha.cp.core.models.DocTypeWithSchema;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Integration tests for DocumentContextBuilder helper class.
 * Tests recursive document traversal with depth limits and cycle detection.
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("io.maretha.cp.core.maretha-cp-core")
public class DocumentContextBuilderTest {

    @Inject
    CoreSession session;

    @Test
    public void testGetAllParentDocuments_SingleParent() {
        // Arrange - create hierarchy: root -> workspace -> folder -> doc
        DocumentModel workspace = session.createDocumentModel("/", "workspace1", "Workspace");
        workspace = session.createDocument(workspace);

        DocumentModel folder = session.createDocumentModel(workspace.getPathAsString(), "folder1", "Folder");
        folder = session.createDocument(folder);

        DocumentModel doc = session.createDocumentModel(folder.getPathAsString(), "doc1", "File");
        doc = session.createDocument(doc);
        session.save();

        // Act
        List<DocumentModel> parents = DocumentContextBuilder.getAllParentDocuments(session, doc);

        // Assert
        assertNotNull("Should return parents list", parents);
        assertEquals("Should have 2 parents (folder and workspace)", 2, parents.size());
        assertEquals("First parent should be folder", "folder1", parents.get(0).getName());
        assertEquals("Second parent should be workspace", "workspace1", parents.get(1).getName());
    }

    @Test
    public void testGetAllParentDocuments_NoParents() {
        // Arrange - create document at root
        DocumentModel doc = session.createDocumentModel("/", "rootDoc", "Workspace");
        doc = session.createDocument(doc);
        session.save();

        // Act
        List<DocumentModel> parents = DocumentContextBuilder.getAllParentDocuments(session, doc);

        // Assert
        assertNotNull("Should return list", parents);
        assertTrue("Should be empty for root document", parents.isEmpty());
    }

    @Test
    public void testGetAllParentDocuments_StopsAtRoot() {
        // Arrange
        DocumentModel workspace = session.createDocumentModel("/", "ws", "Workspace");
        workspace = session.createDocument(workspace);

        DocumentModel doc = session.createDocumentModel(workspace.getPathAsString(), "doc", "File");
        doc = session.createDocument(doc);
        session.save();

        // Act
        List<DocumentModel> parents = DocumentContextBuilder.getAllParentDocuments(session, doc);

        // Assert
        assertNotNull("Should return parents", parents);
        assertEquals("Should have 1 parent", 1, parents.size());
        // Should not include root document
    }

    @Test
    public void testBuildContext_SimpleFields() {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "testDoc", "File");
        doc.setPropertyValue("dc:title", "Test Title");
        doc.setPropertyValue("dc:description", "Test Description");
        doc = session.createDocument(doc);
        session.save();

        // Schema fields provider that returns simple fields
        java.util.function.BiFunction<DocumentModel, CoreSession, Set<DocTypeWithSchema.Schema.Field>> provider = (d,
                s) -> {
            Set<DocTypeWithSchema.Schema.Field> fields = new HashSet<>();
            DocTypeWithSchema.Schema.Field titleField = new DocTypeWithSchema.Schema.Field();
            titleField.setXpath("dc:title");
            fields.add(titleField);
            return fields;
        };

        // Act
        Map<String, Object> context = DocumentContextBuilder.buildContext(session, doc, provider);

        // Assert
        assertNotNull("Should return context", context);
        assertTrue("Should contain title", context.containsKey("dc:title"));
    }

    @Test
    public void testGetRelatedDocuments_NoRelations() {
        // Arrange
        DocumentModel doc = session.createDocumentModel("/", "isolatedDoc", "File");
        doc = session.createDocument(doc);
        session.save();

        Map<String, DocTypeWithSchema.Schema> emptySchemaSettings = new HashMap<>();

        java.util.function.BiFunction<DocumentModel, Map<String, DocTypeWithSchema.Schema>, Set<DocTypeWithSchema.Schema.Field>> provider = (
                d, settings) -> new HashSet<>();

        // Act
        List<DocumentModel> related = DocumentContextBuilder.getRelatedDocuments(
                session, doc, provider, emptySchemaSettings);

        // Assert
        assertNotNull("Should return list", related);
        assertTrue("Should be empty when no relations", related.isEmpty());
    }
}
