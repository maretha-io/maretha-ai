/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.opensearch.client.opensearch._types.GeoShapeRelation
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.hint.AbstractGeoOpenSearchHintQueryBuilder;
import org.opensearch.client.opensearch._types.GeoShapeRelation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class GeoShapeOpenSearchHintQueryBuilder
extends AbstractGeoOpenSearchHintQueryBuilder {
    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        String[] values = this.validate(value, 4, "shapeId, type (unused), index and path");
        Query.Builder ret = new Query.Builder();
        ret.geoShape(q -> q.field(fieldName).shape(s -> s.relation(GeoShapeRelation.Within).indexedShape(i -> i.id(values[0]).index(values[2]).path(values[3]))));
        return ret;
    }
}

