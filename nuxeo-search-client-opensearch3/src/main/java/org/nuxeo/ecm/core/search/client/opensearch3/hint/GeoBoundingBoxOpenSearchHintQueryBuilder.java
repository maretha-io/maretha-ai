/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.hint.AbstractGeoOpenSearchHintQueryBuilder;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class GeoBoundingBoxOpenSearchHintQueryBuilder
extends AbstractGeoOpenSearchHintQueryBuilder {
    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        String[] values = this.validate(value, 2, "bottomLeft and topRight point");
        String bottomLeft = values[0];
        String topRight = values[1];
        Query.Builder ret = new Query.Builder();
        ret.geoBoundingBox(q -> q.field(fieldName).boundingBox(b -> b.trbl(v -> v.bottomLeft(bl -> bl.text(bottomLeft)).topRight(tr -> tr.text(topRight)))));
        return ret;
    }
}

