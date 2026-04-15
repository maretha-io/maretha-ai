/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class CommonOpenSearchHintQueryBuilder
implements OpenSearchHintQueryBuilder {
    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        Query.Builder ret = new Query.Builder();
        ret.common(q -> q.field(fieldName).query((String)value).analyzer(hint.analyzer));
        return ret;
    }
}

