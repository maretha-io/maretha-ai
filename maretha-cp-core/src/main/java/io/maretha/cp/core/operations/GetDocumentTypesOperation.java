package io.maretha.cp.core.operations;

import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.Settings;
import io.maretha.cp.core.services.RepositoryService;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.ListTypeImpl;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.DirectoryEntryResolver;
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
    private final RepositoryService cpLocalSettingsService = Framework.getService(RepositoryService.class);
    @Param(name = "withSchemas", required = false)
    protected boolean withSchemas;
    @Param(name = "documentType", required = false)
    protected String documentType;
    @Context
    protected CoreSession session;

    @OperationMethod
    public List<? extends Object> run() {
        SchemaManager schemaManager = Framework.getService(SchemaManager.class);
        if (withSchemas) {
            Settings settings = cpLocalSettingsService.getSavedSettings(session);
            return getDocTypesWithSchemaDetails(schemaManager, documentType, settings.getSchemaSettings());
        }
        return Arrays.stream(schemaManager.getDocumentTypes()).map(Type::getName).sorted().collect(Collectors.toList());
    }

    private List<DocTypeWithSchema> getDocTypesWithSchemaDetails(SchemaManager schemaManager, String documentType, Map<String, DocTypeWithSchema.Schema> schemaSettings) {
        List<DocTypeWithSchema> docTypes = new ArrayList<>();
        List<DocumentType> documentTypes;
        if (StringUtils.isNotEmpty(documentType)) {
            DocumentType docType = schemaManager.getDocumentType(documentType);
            documentTypes = List.of(docType);
        } else {
            documentTypes = List.of(schemaManager.getDocumentTypes());
        }
        documentTypes.stream().sorted().forEach(docType -> {
            DocTypeWithSchema docTypeWithSchema = new DocTypeWithSchema();
            docTypeWithSchema.setName(docType.getName());
            docTypes.add(docTypeWithSchema);
            Collection<Schema> schemas = docType.getSchemas();

            schemas.forEach(schema -> {
                String schemaName = schema.getName();
                if (MapUtils.isNotEmpty(schemaSettings) && schemaSettings.containsKey(schemaName)) {
                    //add existing saved schema settings
                    docTypeWithSchema.getSchemas().add(schemaSettings.get(schemaName));
                    return;
                }
                DocTypeWithSchema.Schema docTypeSchema = new DocTypeWithSchema.Schema();
                docTypeSchema.setName(schemaName);

                docTypeWithSchema.getSchemas().add(docTypeSchema);

                schema.getFields().forEach(field -> {
                    Type type = field.getType();
                    DocTypeWithSchema.Schema.Field customField = new DocTypeWithSchema.Schema.Field();
                    customField.setXpath(field.getName().getPrefixedName());
                    customField.setType(field.getType().getName());
                    customField.setDescription("");
                    docTypeSchema.getFields().add(customField);
                    ObjectResolver objectResolver;
                    if (type instanceof ListType) {
                        customField.setMultiValue(true);
                        objectResolver = ((ListTypeImpl) type).getFieldType().getObjectResolver();
                    } else {
                        objectResolver = type.getObjectResolver();
                    }
                    if (objectResolver instanceof DirectoryEntryResolver) {
                        Directory directory = ((DirectoryEntryResolver) objectResolver).getDirectory();
                        customField.setDirectoryName(directory.getName());
                    }
                });
            });
        });
        return docTypes;
    }

}
