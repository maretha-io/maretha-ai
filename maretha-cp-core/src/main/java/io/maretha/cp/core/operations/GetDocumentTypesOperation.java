package io.maretha.cp.core.operations;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.DirectoryEntryResolver;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.runtime.api.Framework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 */
@Operation(id = GetDocumentTypesOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get Document Types", description = "Get all docTypes")
public class GetDocumentTypesOperation {

    public static final String ID = "GetDocumentTypesOperation";
    private final Log log = LogFactory.getLog(GetDocumentTypesOperation.class);
    @Param(name = "withSchemas", required = false)
    protected boolean withSchemas;

    @OperationMethod
    public List<? extends Object> run() {
        SchemaManager schemaManager = Framework.getService(SchemaManager.class);
        if (withSchemas) {
            return getDocTypesWithSchemaDetails(schemaManager);
        }
        return Arrays.stream(schemaManager.getDocumentTypes()).map(Type::getName).collect(Collectors.toList());
    }

    private List<DocTypeWithSchema> getDocTypesWithSchemaDetails(SchemaManager schemaManager) {
        List<DocTypeWithSchema> docTypes = new ArrayList<>();
        Arrays.stream(schemaManager.getDocumentTypes()).forEach(docType -> {
            DocTypeWithSchema docTypeWithSchema = new DocTypeWithSchema();
            docTypeWithSchema.setName(docType.getName());
            docTypes.add(docTypeWithSchema);
            Collection<Schema> schemas = docType.getSchemas();

            schemas.forEach(schema -> {
                DocTypeWithSchema.Schema docTypeSchema = new DocTypeWithSchema.Schema();
                docTypeSchema.setName(schema.getName());
                docTypeWithSchema.getSchemas().add(docTypeSchema);

                schema.getFields().forEach(field -> {
                    Type type = field.getType();
                    DocTypeWithSchema.Schema.Field customField = new DocTypeWithSchema.Schema.Field();
                    customField.setXpath(field.getName().getPrefixedName());
                    customField.setDescription("");
                    docTypeSchema.getFields().add(customField);
                    ObjectResolver objectResolver = type.getObjectResolver();
                    if (objectResolver instanceof DirectoryEntryResolver) {
                        Directory directory = ((DirectoryEntryResolver) objectResolver).getDirectory();

                        try (Session session = Framework.getService(DirectoryService.class).open(directory.getName())) {
                            DocumentModelList entries = session.query(Map.of());
                            entries.forEach(entry -> {
                                String id = entry.getId();
                                try {
                                    String label = (String) (entry.getProperty("label")).getValue();
                                    DocTypeWithSchema.Schema.Field.DirectoryEntry directoryEntry = new DocTypeWithSchema.Schema.Field.DirectoryEntry(id, label, "");
                                    customField.getDirectoryEntries().add(directoryEntry);
                                } catch (PropertyNotFoundException e) {
                                    log.debug("Label not found for directory: " + id);
                                }
                            });
                        }
                    }
                });
            });
        });
        return docTypes;
    }

    @Data
    public static class DocTypeWithSchema {
        private String name;
        private List<Schema> schemas = new ArrayList<>();

        @Data
        public static class Schema {
            private String name;
            private List<Field> fields = new ArrayList<>();

            @Data
            public static class Field {
                private String xpath;
                private String description;
                private List<DirectoryEntry> directoryEntries = new ArrayList<>();

                @Data
                @AllArgsConstructor
                public static class DirectoryEntry {
                    private String id;
                    private String label;
                    private String description;
                }
            }
        }
    }
}
