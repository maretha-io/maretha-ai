package io.maretha.cp.core.adapters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.DocTypeWithSchema;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.adapter.DocumentAdapterFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.maretha.cp.core.services.RepositoryService.SETTINGS_ELASTICSEARCH;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_SCHEMA_AWS_S3_ACCESS;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_SCHEMA_METADATA;
import static io.maretha.cp.core.services.RepositoryServiceImpl.SETTINGS_DOCUMENT_TYPE;
import static io.maretha.cp.core.services.RepositoryServiceImpl.SETTINGS_SCHEMA_PATH;

public class SettingsAdapterFactory implements DocumentAdapterFactory {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object getAdapter(DocumentModel doc, Class<?> aClass) {
        if (SETTINGS_DOCUMENT_TYPE.equals(doc.getType())) {
            Object interactions = doc.getPropertyValue(SETTINGS_SCHEMA_PATH);
            List<Interaction> cpInteractions = objectMapper
                    .convertValue(interactions, objectMapper.getTypeFactory().constructCollectionType(List.class, Interaction.class));
            Boolean awsS3AccessEnabled = (Boolean) Objects.requireNonNullElse(doc.getPropertyValue(SETTINGS_SCHEMA_AWS_S3_ACCESS), false);
            Map<String, DocTypeWithSchema.Schema> schemaSettingsMap = getSchemaSettingsMap(doc);
            Map<String, String> esSettingsMap = getEsSettingsMap(doc);
            return new Settings(cpInteractions, awsS3AccessEnabled, schemaSettingsMap, esSettingsMap);
        }
        return null;
    }

    private Map<String, DocTypeWithSchema.Schema> getSchemaSettingsMap(DocumentModel doc) {
        Map<String, DocTypeWithSchema.Schema> schemaSettingsMap = Map.of();
        try {
            String schemaSettings = (String) doc.getPropertyValue(SETTINGS_SCHEMA_METADATA);
            schemaSettingsMap = objectMapper.readValue(schemaSettings, new TypeReference<>() {
            });
        } catch (Exception e) {
            //ignore
        }
        return schemaSettingsMap;
    }

    private Map<String, String> getEsSettingsMap(DocumentModel doc) {
        Map<String, String> esSettingsMap = Map.of();
        try {
            String schemaSettings = (String) doc.getPropertyValue(SETTINGS_ELASTICSEARCH);
            esSettingsMap = objectMapper.readValue(schemaSettings, new TypeReference<>() {
            });
        } catch (Exception e) {
            //ignore
        }
        return esSettingsMap;
    }
}
