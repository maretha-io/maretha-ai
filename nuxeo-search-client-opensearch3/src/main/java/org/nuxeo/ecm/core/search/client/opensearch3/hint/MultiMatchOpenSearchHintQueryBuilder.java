/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import java.util.List;
import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchQueryTransformer;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class MultiMatchOpenSearchHintQueryBuilder
implements OpenSearchHintQueryBuilder {
    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        List<String> fields = hint.getIndex().stream().map(fieldHint -> OpenSearchQueryTransformer.getBoostedField(fieldHint)).toList();
        Query.Builder ret = new Query.Builder();
        ret.multiMatch(m -> m.query((String)value).analyzer(hint.analyzer).fields(fields));
        return ret;
    }
}

