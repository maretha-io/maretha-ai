package io.maretha.cp.core.services;

import io.maretha.cp.core.models.CpInteraction;

import java.util.List;

public interface CPLocalSettingsServiceInterface {
    List<CpInteraction> getInteractionSettings();
    List<CpInteraction> getActiveInteractionsForDocumentType(String documentType);
}
