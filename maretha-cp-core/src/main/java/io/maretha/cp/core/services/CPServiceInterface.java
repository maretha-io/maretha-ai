package io.maretha.cp.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.maretha.cp.core.models.CpInteraction;

import java.util.List;
import java.util.Map;

public interface CPServiceInterface {
    List<CpInteraction> getInteractions();
    JsonNode getInteractionResult(String request, Map<String, String> data);
}
