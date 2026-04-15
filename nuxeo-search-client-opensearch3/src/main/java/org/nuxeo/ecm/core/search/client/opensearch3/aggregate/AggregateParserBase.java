/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import org.nuxeo.ecm.platform.query.api.Aggregate;

public final class AggregateParserBase {
    public static final String FULLTEXT_FIELD = "all_field";
    public static final String AGG_FILTER_SUFFIX = "_filter";
    public static final char XPATH_SEP = '/';
    public static final char ES_MULTI_LEVEL_SEP = '.';

    public static String getFilterId(Aggregate<?> agg) {
        return agg.getId() + AGG_FILTER_SUFFIX;
    }

    public static String getFieldName(String name) {
        if ("ecm:fulltext".equals(name)) {
            return FULLTEXT_FIELD;
        }
        return name.replace('/', '.');
    }
}

