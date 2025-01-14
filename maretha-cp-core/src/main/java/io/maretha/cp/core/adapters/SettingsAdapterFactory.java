package io.maretha.cp.core.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.adapter.DocumentAdapterFactory;

import java.util.List;
import java.util.Objects;

import static io.maretha.cp.core.services.RepositoryService.SETTINGS_SCHEMA_AWS_S3_ACCESS;
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
            return new Settings(cpInteractions, awsS3AccessEnabled);
        }
        return null;
    }
}
