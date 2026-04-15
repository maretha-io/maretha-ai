/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.nuxeo.ecm.core.query.sql.model.EsHint$FieldHint
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.QueryBuilders
 *  org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchQueryTransformer;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;

public class QueryStringOpenSearchHintQueryBuilder
implements OpenSearchHintQueryBuilder {
    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        QueryStringQuery.Builder qs = QueryBuilders.queryString();
        qs.query((String)value).analyzer(hint.analyzer);
        if (hint.index == null) {
            qs.fields(fieldName, new String[0]);
        } else {
            for (EsHint.FieldHint fieldHint : hint.getIndex()) {
                qs.fields(OpenSearchQueryTransformer.getBoostedField(fieldHint), new String[0]);
            }
        }
        Query.Builder ret = new Query.Builder();
        ret.queryString(qs.build());
        return ret;
    }
}

