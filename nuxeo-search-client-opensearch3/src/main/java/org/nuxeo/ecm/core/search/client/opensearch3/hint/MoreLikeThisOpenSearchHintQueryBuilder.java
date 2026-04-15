/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.opensearch.client.opensearch._types.query_dsl.Like
 *  org.opensearch.client.opensearch._types.query_dsl.Like$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.opensearch.client.opensearch._types.query_dsl.Like;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class MoreLikeThisOpenSearchHintQueryBuilder
implements OpenSearchHintQueryBuilder {
    public static final int MORE_LIKE_THIS_MIN_TERM_FREQ = 1;
    public static final int MORE_LIKE_THIS_MIN_DOC_FREQ = 3;
    public static final int MORE_LIKE_THIS_MAX_QUERY_TERMS = 12;

    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        String[] stringArray;
        String[] indexFields = hint.getIndexFieldNames();
        if (indexFields.length > 0) {
            stringArray = indexFields;
        } else {
            String[] stringArray2 = new String[1];
            stringArray = stringArray2;
            stringArray2[0] = fieldName;
        }
        String[] fields = stringArray;
        Query.Builder ret = new Query.Builder();
        ret.moreLikeThis(m -> m.fields(Arrays.stream(fields).toList()).like(MoreLikeThisOpenSearchHintQueryBuilder.getItems(value)).analyzer(hint.analyzer).minDocFreq(Integer.valueOf(3)).minTermFreq(Integer.valueOf(1)).maxQueryTerms(Integer.valueOf(12)));
        return ret;
    }

    public static List<Like> getItems(Object value) {
        Object[] values = value instanceof Object[] ? (Object[])value : new Object[]{value};
        ArrayList<Like> ret = new ArrayList<Like>(values.length);
        int i = 0;
        while (i < values.length) {
            int finalI = i++;
            ret.add((Like)new Like.Builder().document(d -> d.id((String)values[finalI])).build());
        }
        return ret;
    }
}

