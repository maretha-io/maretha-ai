/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.BucketTerm
 *  org.opensearch.client.opensearch._types.FieldValue
 *  org.opensearch.client.opensearch._types.aggregations.Buckets
 *  org.opensearch.client.opensearch._types.aggregations.LongTermsBucket
 *  org.opensearch.client.opensearch._types.aggregations.StringTermsBucket
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import java.util.ArrayList;
import java.util.List;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.BucketTerm;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;

final class AggregateTermParserBase {
    public static final int MAX_AGG_SIZE = 1000;

    AggregateTermParserBase() {
    }

    public static int getAggSize(String prop) {
        int size = Integer.parseInt(prop);
        return size == 0 ? 1000 : size;
    }

    public static Query.Builder getFilter(Aggregate<? extends Bucket> agg) {
        if (agg.getSelection().isEmpty()) {
            return null;
        }
        Query.Builder ret = new Query.Builder();
        ret.terms(t -> t.field(agg.getField()).terms(v -> v.value(agg.getSelection().stream().map(FieldValue::of).toList())));
        return ret;
    }

    public static List<BucketTerm> parseBuckets(Buckets<StringTermsBucket> buckets) {
        if (buckets == null || !buckets.isArray() || buckets.array().isEmpty()) {
            return List.of();
        }
        ArrayList<BucketTerm> nxBuckets = new ArrayList<BucketTerm>(buckets.array().size());
        for (StringTermsBucket bucket : buckets.array()) {
            nxBuckets.add(new BucketTerm(bucket.key(), bucket.docCount()));
        }
        return nxBuckets;
    }

    public static List<BucketTerm> parseLongBuckets(Buckets<LongTermsBucket> buckets) {
        if (buckets == null || !buckets.isArray() || buckets.array().isEmpty()) {
            return List.of();
        }
        ArrayList<BucketTerm> nxBuckets = new ArrayList<BucketTerm>(buckets.array().size());
        for (LongTermsBucket bucket : buckets.array()) {
            String key = bucket.keyAsString();
            nxBuckets.add(new BucketTerm(key, bucket.docCount()));
        }
        return nxBuckets;
    }
}

