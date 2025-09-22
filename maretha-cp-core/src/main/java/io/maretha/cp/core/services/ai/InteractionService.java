package io.maretha.cp.core.services.ai;

import io.maretha.cp.core.models.Interaction;

import java.util.Map;

public interface InteractionService {
    String invokeModel(Interaction interaction, Map<String, Object> paramsData, String resultSchema);
}
