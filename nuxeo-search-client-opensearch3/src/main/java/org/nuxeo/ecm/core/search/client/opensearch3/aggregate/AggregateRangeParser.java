/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.AggregateRangeDefinition
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.BucketRange
 *  org.opensearch.client.json.JsonData
 *  org.opensearch.client.opensearch._types.aggregations.Aggregation
 *  org.opensearch.client.opensearch._types.aggregations.AggregationBuilders
 *  org.opensearch.client.opensearch._types.aggregations.AggregationVariant
 *  org.opensearch.client.opensearch._types.aggregations.Buckets
 *  org.opensearch.client.opensearch._types.aggregations.RangeAggregation$Builder
 *  org.opensearch.client.opensearch._types.aggregations.RangeBucket
 *  org.opensearch.client.opensearch._types.query_dsl.Query
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.QueryBuilders
 *  org.opensearch.client.opensearch._types.query_dsl.QueryVariant
 *  org.opensearch.client.opensearch._types.query_dsl.RangeQuery$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import java.util.ArrayList;
import java.util.List;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateParserBase;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.AggregateRangeDefinition;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.BucketRange;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.AggregationVariant;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.RangeAggregation;
import org.opensearch.client.opensearch._types.aggregations.RangeBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;

public final class AggregateRangeParser {
    private AggregateRangeParser() {
    }

    public static Aggregation getAggregate(Aggregate<? extends Bucket> agg) {
        RangeAggregation.Builder builder = AggregationBuilders.range();
        builder.field(AggregateParserBase.getFieldName(agg.getField()));
        for (AggregateRangeDefinition range : agg.getRanges()) {
            if (range.getFrom() != null) {
                if (range.getTo() != null) {
                    builder.ranges(v -> v.key(range.getKey()).from(JsonData.of(range.getFrom().toString())).to(JsonData.of(range.getTo().toString())));
                    continue;
                }
                builder.ranges(v -> v.key(range.getKey()).from(JsonData.of(range.getFrom().toString())));
                continue;
            }
            if (range.getTo() == null) continue;
            builder.ranges(v -> v.key(range.getKey()).to(JsonData.of(range.getTo().toString())));
        }
        return new Aggregation((AggregationVariant)builder.build());
    }

    public static Query.Builder getFilter(Aggregate<? extends Bucket> agg) {
        if (agg.getSelection().isEmpty()) {
            return null;
        }
        ArrayList<Query> shouldFilter = new ArrayList<Query>();
        for (AggregateRangeDefinition range : agg.getRanges()) {
            if (!agg.getSelection().contains(range.getKey())) continue;
            RangeQuery.Builder rangeFilter = QueryBuilders.range();
            rangeFilter.field(AggregateParserBase.getFieldName(agg.getField()));
            if (range.getFrom() != null) {
                rangeFilter.gte(JsonData.of((Object)range.getFrom()));
            }
            if (range.getTo() != null) {
                rangeFilter.lt(JsonData.of((Object)range.getTo()));
            }
            shouldFilter.add(new Query((QueryVariant)rangeFilter.build()));
        }
        if (shouldFilter.isEmpty()) {
            return null;
        }
        Query.Builder ret = new Query.Builder();
        ret.bool(b -> b.should(shouldFilter));
        return ret;
    }

    public static List<BucketRange> parseBuckets(Buckets<RangeBucket> buckets) {
        if (buckets == null || !buckets.isArray() || buckets.array().isEmpty()) {
            return List.of();
        }
        ArrayList<BucketRange> ret = new ArrayList<BucketRange>(buckets.array().size());
        for (RangeBucket bucket : buckets.array()) {
            ret.add(new BucketRange(bucket.key(), (Number)bucket.from(), (Number)bucket.to(), bucket.docCount()));
        }
        return ret;
    }
}

