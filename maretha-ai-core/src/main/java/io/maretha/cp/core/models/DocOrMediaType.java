package io.maretha.cp.core.models;

import lombok.Getter;

@Getter
public enum DocOrMediaType {
    DOCUMENT("document"),
    MEDIA("media");

    private final String typeName;

    DocOrMediaType(String typeName) {
        this.typeName = typeName;
    }

}
