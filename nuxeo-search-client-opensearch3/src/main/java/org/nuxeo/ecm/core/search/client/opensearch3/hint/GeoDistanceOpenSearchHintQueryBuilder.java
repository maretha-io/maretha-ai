/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.opensearch.client.opensearch._types.GeoDistanceType
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.hint.AbstractGeoOpenSearchHintQueryBuilder;
import org.opensearch.client.opensearch._types.GeoDistanceType;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class GeoDistanceOpenSearchHintQueryBuilder
extends AbstractGeoOpenSearchHintQueryBuilder {
    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        String[] values = this.validate(value, 2, "point and distance");
        String center = values[0];
        String dist = values[1];
        Query.Builder ret = new Query.Builder();
        ret.geoDistance(q -> q.field(fieldName).distanceType(GeoDistanceType.Arc).distance(dist).location(g -> g.text(center)));
        return ret;
    }
}

