package io.maretha.cp.core.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class InteractionParameter {
    private String name;
    private String type;
    private String format;
    private String description;

    private String value;
    private boolean readOnly;
    private boolean enableDocSelection;
    @JsonProperty("isMainDocument")
    private boolean isMainDocument;
    private boolean selectionReadOnly;
    @JsonProperty("isContextMemoryParameter")
    private boolean isContextMemoryParameter;
    @JsonProperty("isAdditionalContext")
    private boolean isAdditionalContext;
    private List<String> selectedDocumentIds;
    private String selectedDocumentId;
}
