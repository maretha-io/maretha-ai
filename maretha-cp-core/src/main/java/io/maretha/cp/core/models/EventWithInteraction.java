package io.maretha.cp.core.models;

import lombok.Data;

import java.util.Map;

@Data
public class EventWithInteraction {
    private String eventName;
    private String interactionId;
    private Map<String, String> parameters;
    private boolean active;
}
