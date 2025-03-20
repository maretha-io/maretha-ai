package io.maretha.cp.core.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {
    private List<Interaction> interactions = new ArrayList<>();
    private boolean awsS3AccessEnabled;
    private Map<String, DocTypeWithSchema.Schema> schemaSettings;
    private Map<String, String> esSettings;
    private Map<String, String> vertesiaSettings;
}
