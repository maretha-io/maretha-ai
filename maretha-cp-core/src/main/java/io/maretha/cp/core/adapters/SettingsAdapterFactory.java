package io.maretha.cp.core.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.Interaction;
import io.maretha.cp.core.models.Settings;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.adapter.DocumentAdapterFactory;

import java.util.List;

import static io.maretha.cp.core.services.RepositoryService.SETTINGS_SCHEMA_PATH;
import static io.maretha.cp.core.services.RepositoryService.SETTINGS_DOCUMENT_TYPE;

public class SettingsAdapterFactory implements DocumentAdapterFactory {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object getAdapter(DocumentModel doc, Class<?> aClass) {
        if (SETTINGS_DOCUMENT_TYPE.equals(doc.getType())) {
            Object interactions = doc.getPropertyValue(SETTINGS_SCHEMA_PATH);
            List<Interaction> cpInteractions = objectMapper
                    .convertValue(interactions, objectMapper.getTypeFactory().constructCollectionType(List.class, Interaction.class));
            return new Settings(cpInteractions);
        }
        return null;
    }
}
