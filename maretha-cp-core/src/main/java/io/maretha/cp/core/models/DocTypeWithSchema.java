package io.maretha.cp.core.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocTypeWithSchema {
    private String name;
    private List<Schema> schemas = new ArrayList<>();

    @Data
    public static class Schema {
        private String name;
        private List<Field> fields = new ArrayList<>();

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Field {
            private String xpath;
            private String description;
            private String type;
            private String directoryName;
            private boolean active;
        }
    }
}