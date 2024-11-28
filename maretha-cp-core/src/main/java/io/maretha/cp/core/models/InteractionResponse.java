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
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class InteractionResponse {
    private String id;
    private String documentId;
    private String interactionId;
    private String status;
    private String name;
    private String updatedAt;
    private String createdAt;
    private String createdBy;
    private String response;
    private List<InteractionParameter> parameters = new ArrayList<>();
}
