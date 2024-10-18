package io.maretha.cp.core.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CpInteraction {
    private String id;
    private String description;
    private String name;
    private String status;
    private String updated_at;
    private List<Object> prompts;
    private List<Object> parameters;

    private List<String> activeOnDocumentTypes = new ArrayList<>();
    private Boolean isActive = Boolean.FALSE;
}
