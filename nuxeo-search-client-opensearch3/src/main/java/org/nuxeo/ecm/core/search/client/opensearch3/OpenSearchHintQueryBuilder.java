/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3;

import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public interface OpenSearchHintQueryBuilder {
    public Query.Builder make(EsHint var1, String var2, Object var3);
}

