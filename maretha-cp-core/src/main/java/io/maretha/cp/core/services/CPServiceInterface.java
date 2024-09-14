package io.maretha.cp.core.services;

import io.maretha.cp.core.models.CpInteraction;

import java.util.List;
import java.util.Map;

public interface CPServiceInterface {
    List<CpInteraction> getInteractions();
    List<Object> getInteractionParameters(String interactionId);
    String getInteractionResult(String request, Map<String, String> data);
}
