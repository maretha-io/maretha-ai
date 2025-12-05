package io.maretha.cp.core.services.utils;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.model.DocumentModelResolver;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NuxeoDocResolverUtils {
    /**
     * Resolves a property (XPath) that may reference one or many documents by ID.
     * Supports both single docid and list-of-docid fields with a
     * "documentResolver".
     *
     * @param session CoreSession to use for fetching documents
     * @param doc     the source DocumentModel
     * @param xpath   the property XPath (e.g., "rel:job")
     * @return list of resolved DocumentModels, or empty list if none or invalid
     */
    public static List<DocumentModel> resolveDocumentReferences(CoreSession session, DocumentModel doc, String xpath) {
        List<DocumentModel> results = new ArrayList<>();
        try {
            Property prop = doc.getProperty(xpath);
            Field field = prop.getField();
            Type type = field.getType();
            ObjectResolver resolver = type.getObjectResolver();

            // Check if single-valued reference
            if (resolver instanceof DocumentModelResolver) {
                String id = (String) prop.getValue();
                if (id != null) {
                    results.add(session.getDocument(new IdRef(id)));
                }
                return results;
            }

            // Check if list of document references
            if (type.isListType()) {
                Type elementType = ((ListType) type).getFieldType();
                ObjectResolver elementResolver = elementType.getObjectResolver();

                if (elementResolver instanceof DocumentModelResolver) {
                    @SuppressWarnings("unchecked")
                    String[] ids = (String[]) prop.getValue();
                    if (ids != null) {
                        for (String id : ids) {
                            try {
                                results.add(session.getDocument(new IdRef(id)));
                            } catch (Exception e) {
                                // skip missing or invalid refs
                            }
                        }
                    }
                    return results;
                }
            }

        } catch (Exception e) {
            // Property missing or invalid; return empty list
        }
        return Collections.emptyList();
    }

    /**
     * Checks if a given property XPath points to a document reference (single or
     * multi-valued).
     *
     * @param doc   the DocumentModel
     * @param xpath the property XPath (e.g., "rel:job")
     * @return true if the field references one or more documents; false otherwise
     */
    public static boolean isDocumentReference(DocumentModel doc, String xpath) {
        try {
            Property prop = doc.getProperty(xpath);
            Field field = prop.getField();
            Type type = field.getType();

            // Single-valued document reference
            ObjectResolver resolver = type.getObjectResolver();
            if (resolver instanceof DocumentModelResolver) {
                return true;
            }

            // Multi-valued (list) document reference
            if (type.isListType()) {
                Type elementType = ((ListType) type).getFieldType();
                ObjectResolver elementResolver = elementType.getObjectResolver();
                if (elementResolver instanceof DocumentModelResolver) {
                    return true;
                }
            }

        } catch (Exception e) {
            // property missing or invalid XPath → treat as not a doc reference
        }
        return false;
    }
}
