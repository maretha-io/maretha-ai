package io.maretha.cp.core.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class CpInteraction {
    private String id;
    private String description;
    private String name;
    private String status;
    private String updated_at;
    private List<Object> prompts;

    private List<String> activeOnDocumentTypes = new ArrayList<>();
    private Boolean isActive = Boolean.FALSE;
}
