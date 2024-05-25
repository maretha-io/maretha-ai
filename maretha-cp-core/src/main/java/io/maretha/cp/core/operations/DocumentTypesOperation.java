package io.maretha.cp.core.operations;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.runtime.api.Framework;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
@Operation(id = DocumentTypesOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get Document Types", description = "Get all docTypes")
public class DocumentTypesOperation {

    public static final String ID = "DocumentTypesOperation";

    @OperationMethod
    public List<String> run() {
        SchemaManager schemaManager = Framework.getService(SchemaManager.class);
        return Arrays.stream(schemaManager.getDocumentTypes()).map(Type::getName).collect(Collectors.toList());
    }
}
