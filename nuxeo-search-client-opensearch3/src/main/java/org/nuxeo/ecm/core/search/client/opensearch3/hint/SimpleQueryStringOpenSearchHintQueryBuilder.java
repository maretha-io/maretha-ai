/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.nuxeo.ecm.core.query.sql.model.EsHint$FieldHint
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.QueryBuilders
 *  org.opensearch.client.opensearch._types.query_dsl.SimpleQueryStringQuery$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchQueryTransformer;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.SimpleQueryStringQuery;

public class SimpleQueryStringOpenSearchHintQueryBuilder
implements OpenSearchHintQueryBuilder {
    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        SimpleQueryStringQuery.Builder querySimpleString = QueryBuilders.simpleQueryString();
        querySimpleString.query((String)value);
        querySimpleString.analyzer(hint.analyzer);
        if (hint.index == null) {
            querySimpleString.fields(fieldName, new String[0]);
        } else {
            for (EsHint.FieldHint fieldHint : hint.getIndex()) {
                querySimpleString.fields(OpenSearchQueryTransformer.getBoostedField(fieldHint), new String[0]);
            }
        }
        Query.Builder ret = new Query.Builder();
        ret.simpleQueryString(querySimpleString.build());
        return ret;
    }
}

