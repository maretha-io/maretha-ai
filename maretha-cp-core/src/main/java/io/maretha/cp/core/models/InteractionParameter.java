package io.maretha.cp.core.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    private String value;
    private boolean readOnly;
    private boolean enableDocSelection;
    private List<String> selectedDocumentIds;
}
