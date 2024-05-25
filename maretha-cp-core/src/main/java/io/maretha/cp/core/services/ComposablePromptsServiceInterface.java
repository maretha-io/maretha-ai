package io.maretha.cp.core.services;

import io.maretha.cp.core.models.CpInteraction;
import org.json.JSONArray;

import java.util.List;

public interface ComposablePromptsServiceInterface {
    List<CpInteraction> getInteractions();
}
