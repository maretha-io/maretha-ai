package io.maretha.cp.core.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventWithInteraction {
    private String docType;
    private List<String> events = new ArrayList<>();
    private List<String> interactions = new ArrayList<>();
    private boolean active;
}
