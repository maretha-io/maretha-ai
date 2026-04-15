/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.AggregateRangeDateDefinition
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.AggregateDateRange
 *  org.nuxeo.ecm.platform.query.core.BucketRangeDate
 *  org.opensearch.client.json.JsonData
 *  org.opensearch.client.opensearch._types.aggregations.Aggregation
 *  org.opensearch.client.opensearch._types.aggregations.AggregationBuilders
 *  org.opensearch.client.opensearch._types.aggregations.AggregationVariant
 *  org.opensearch.client.opensearch._types.aggregations.Buckets
 *  org.opensearch.client.opensearch._types.aggregations.DateRangeAggregation$Builder
 *  org.opensearch.client.opensearch._types.aggregations.RangeBucket
 *  org.opensearch.client.opensearch._types.query_dsl.Query
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.QueryBuilders
 *  org.opensearch.client.opensearch._types.query_dsl.QueryVariant
 *  org.opensearch.client.opensearch._types.query_dsl.RangeQuery$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateParserBase;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.AggregateRangeDateDefinition;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.AggregateDateRange;
import org.nuxeo.ecm.platform.query.core.BucketRangeDate;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.AggregationVariant;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.DateRangeAggregation;
import org.opensearch.client.opensearch._types.aggregations.RangeBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;

public final class AggregateDateRangeParser {
    public static Aggregation getAggregate(Aggregate<? extends Bucket> agg) {
        DateRangeAggregation.Builder builder = AggregationBuilders.dateRange();
        builder.field(AggregateParserBase.getFieldName(agg.getField()));
        for (AggregateRangeDateDefinition range : agg.getDateRanges()) {
            if (range.getFromAsString() != null) {
                if (range.getToAsString() != null) {
                    builder.ranges(v -> v.key(range.getKey()).from(f -> f.expr(range.getFromAsString())).to(t -> t.expr(range.getToAsString())));
                    continue;
                }
                builder.ranges(v -> v.key(range.getKey()).from(f -> f.expr(range.getFromAsString())));
                continue;
            }
            if (range.getToAsString() == null) continue;
            builder.ranges(v -> v.key(range.getKey()).to(f -> f.expr(range.getToAsString())));
        }
        Map props = agg.getProperties();
        if (props.containsKey("format")) {
            builder.format((String)props.get("format"));
        }
        return new Aggregation((AggregationVariant)builder.build());
    }

    public static Query.Builder getFilter(AggregateDateRange agg) {
        if (agg.getSelection().isEmpty()) {
            return null;
        }
        ArrayList<Query> shouldFilter = new ArrayList<Query>();
        for (AggregateRangeDateDefinition range : agg.getDateRanges()) {
            if (!agg.getSelection().contains(range.getKey())) continue;
            RangeQuery.Builder rangeFilter = QueryBuilders.range();
            rangeFilter.field(AggregateParserBase.getFieldName(agg.getField()));
            if (range.getFromAsString() != null) {
                rangeFilter.gte(JsonData.of((Object)range.getFromAsString()));
            }
            if (range.getToAsString() != null) {
                rangeFilter.lt(JsonData.of((Object)range.getToAsString()));
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

    public static List<BucketRangeDate> parseBuckets(Buckets<RangeBucket> buckets) {
        if (buckets == null || !buckets.isArray() || buckets.array().isEmpty()) {
            return List.of();
        }
        ArrayList<BucketRangeDate> ret = new ArrayList<BucketRangeDate>(buckets.array().size());
        for (RangeBucket bucket : buckets.array()) {
            ZonedDateTime fromZDT = null;
            ZonedDateTime toZDT = null;
            ret.add(new BucketRangeDate(bucket.key(), fromZDT, toZDT, bucket.docCount()));
        }
        return ret;
    }
}

