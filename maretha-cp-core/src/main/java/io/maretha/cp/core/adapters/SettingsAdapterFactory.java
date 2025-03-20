package io.maretha.cp.core.adapters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.adapter.DocumentAdapterFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.maretha.cp.core.services.RepositoryService.SETTINGS_ELASTICSEARCH;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_SCHEMA_AWS_S3_ACCESS;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_SCHEMA_METADATA;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_VERTESIA;
import static io.maretha.cp.core.services.RepositoryServiceImpl.SETTINGS_DOCUMENT_TYPE;
import static io.maretha.cp.core.services.RepositoryServiceImpl.SETTINGS_SCHEMA_PATH;

public class SettingsAdapterFactory implements DocumentAdapterFactory {
    private static final Log log = LogFactory.getLog(SettingsAdapterFactory.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object getAdapter(DocumentModel doc, Class<?> aClass) {
        if (SETTINGS_DOCUMENT_TYPE.equals(doc.getType())) {
            Object interactions = doc.getPropertyValue(SETTINGS_SCHEMA_PATH);
            List<Interaction> cpInteractions = objectMapper
                    .convertValue(interactions, objectMapper.getTypeFactory().constructCollectionType(List.class, Interaction.class));
            Boolean awsS3AccessEnabled = (Boolean) Objects.requireNonNullElse(doc.getPropertyValue(SETTINGS_SCHEMA_AWS_S3_ACCESS), false);
            Map<String, DocTypeWithSchema.Schema> schemaSettingsMap = getPropertyValue(doc, SETTINGS_SCHEMA_METADATA, new TypeReference<>() {
            });
            Map<String, String> esSettingsMap = getPropertyValue(doc, SETTINGS_ELASTICSEARCH, new TypeReference<>() {
            });
            Map<String, String> settingsMap = getPropertyValue(doc, SETTINGS_VERTESIA, new TypeReference<>() {
            });
            return new Settings(cpInteractions, awsS3AccessEnabled, schemaSettingsMap, esSettingsMap, settingsMap);
        }
        return null;
    }

    private <T> T getPropertyValue(DocumentModel doc, String xpath, TypeReference<T> typeReference) {
        try {
            String schemaSettings = (String) doc.getPropertyValue(xpath);
            return objectMapper.readValue(schemaSettings, typeReference);
        } catch (IllegalArgumentException e) {
            //ignore
        } catch (Exception e) {
            log.error(e);
        }
        return null;
    }

}
