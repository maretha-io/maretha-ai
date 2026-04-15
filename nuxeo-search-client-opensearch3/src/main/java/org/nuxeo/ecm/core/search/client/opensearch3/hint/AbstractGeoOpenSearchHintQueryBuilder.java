/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.api.NuxeoException
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import java.util.Arrays;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;

public abstract class AbstractGeoOpenSearchHintQueryBuilder
implements OpenSearchHintQueryBuilder {
    protected String[] validate(Object value, int arrayLength, String message) {
        if (!(value instanceof Object[])) {
            throw new NuxeoException(String.format("Expected an array, found %s", value.getClass()), 400);
        }
        Object[] values = (Object[])value;
        if (values.length != arrayLength) {
            throw new NuxeoException(String.format("Hints: %s requires %s parameters: %s", this.getClass().getSimpleName(), arrayLength, message), 400);
        }
        return (String[])Arrays.asList(values).toArray(String[]::new);
    }
}

