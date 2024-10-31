package io.maretha.cp.core.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class Interaction {
    private String id;
    private String description;
    private String name;
    private String status;
    private String version;
    private String updatedAt;
    private String createdAt;
    private String createdBy;
    private List<InteractionParameter> parameters = new ArrayList<>();
    private List<String> activeOnDocumentTypes = new ArrayList<>();
    private boolean isActive;
    private boolean activeOnFolder;
    private boolean activeOnActionMenu;
}
