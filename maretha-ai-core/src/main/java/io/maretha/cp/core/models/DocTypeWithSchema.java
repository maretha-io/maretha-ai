package io.maretha.cp.core.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class DocTypeWithSchema {
    private String name;
    private List<Schema> schemas = new ArrayList<>();

    @Data
    public static class Schema {
        private String name;
        private String docType;
        private List<Field> fields = new ArrayList<>();

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Field {
            private String xpath;
            private String description;
            private String type;
            private String directoryName;
            private Map<String, DictionaryWithDescription> directoryValues;
            private String interactionId;
            private boolean active;
            private boolean multiValue;
            private boolean inIsolation;
            private boolean additionalContext;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class DictionaryWithDescription {
            private String label;
            private String description;
        }
    }
}