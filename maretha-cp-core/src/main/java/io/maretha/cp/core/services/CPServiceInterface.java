package io.maretha.cp.core.services;

import io.maretha.cp.core.models.CpInteraction;

import java.util.List;

public interface CPServiceInterface {
    List<CpInteraction> getInteractions();
    String getInteractionResult(String request);
}
