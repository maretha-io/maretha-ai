/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.BucketRange
 *  org.opensearch.client.json.JsonData
 *  org.opensearch.client.opensearch._types.SortOrder
 *  org.opensearch.client.opensearch._types.aggregations.Aggregation
 *  org.opensearch.client.opensearch._types.aggregations.AggregationBuilders
 *  org.opensearch.client.opensearch._types.aggregations.AggregationVariant
 *  org.opensearch.client.opensearch._types.aggregations.Buckets
 *  org.opensearch.client.opensearch._types.aggregations.ExtendedBounds
 *  org.opensearch.client.opensearch._types.aggregations.HistogramAggregation$Builder
 *  org.opensearch.client.opensearch._types.aggregations.HistogramBucket
 *  org.opensearch.client.opensearch._types.query_dsl.Query
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.QueryBuilders
 *  org.opensearch.client.opensearch._types.query_dsl.QueryVariant
 *  org.opensearch.client.opensearch._types.query_dsl.RangeQuery$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateParserBase;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.BucketRange;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.AggregationVariant;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.ExtendedBounds;
import org.opensearch.client.opensearch._types.aggregations.HistogramAggregation;
import org.opensearch.client.opensearch._types.aggregations.HistogramBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;

public final class AggregateHistogramParser {
    private AggregateHistogramParser() {
    }

    public static Aggregation getAggregate(Aggregate<? extends Bucket> agg) {
        HistogramAggregation.Builder builder = AggregationBuilders.histogram();
        builder.field(AggregateParserBase.getFieldName(agg.getField()));
        Map props = agg.getProperties();
        builder.interval(AggregateHistogramParser.getInterval(agg));
        if (props.containsKey("minDocCount")) {
            builder.minDocCount(Integer.valueOf(Integer.parseInt((String)props.get("minDocCount"))));
        }
        if (props.containsKey("order")) {
            switch (((String)props.get("order")).toLowerCase()) {
                case "count desc": {
                    builder.order(h -> h.count(SortOrder.Desc));
                    break;
                }
                case "count asc": {
                    builder.order(h -> h.count(SortOrder.Asc));
                    break;
                }
                case "key desc": {
                    builder.order(h -> h.key(SortOrder.Desc));
                    break;
                }
                case "key asc": {
                    builder.order(h -> h.key(SortOrder.Asc));
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Invalid order: " + (String)props.get("order"));
                }
            }
        }
        if (props.containsKey("extendedBoundsMax") && props.containsKey("extendedBoundsMin")) {
            builder.extendedBounds(ExtendedBounds.of(openSearchExtendedBounds -> openSearchExtendedBounds.max(Double.parseDouble((String)props.get("extendedBoundsMax"))).min(Double.parseDouble((String)props.get("extendedBoundsMin")))));
        }
        return new Aggregation((AggregationVariant)builder.build());
    }

    public static Query.Builder getFilter(Aggregate<? extends Bucket> agg) {
        if (agg.getSelection().isEmpty()) {
            return null;
        }
        ArrayList<Query> shouldFilter = new ArrayList<Query>();
        for (String sel : agg.getSelection()) {
            RangeQuery.Builder rangeFilter = QueryBuilders.range();
            rangeFilter.field(AggregateParserBase.getFieldName(agg.getField()));
            double from = Double.parseDouble(sel);
            double to = from + AggregateHistogramParser.getInterval(agg);
            rangeFilter.gte(JsonData.of((Object)from)).lt(JsonData.of((Object)to));
            shouldFilter.add(new Query((QueryVariant)rangeFilter.build()));
        }
        Query.Builder ret = new Query.Builder();
        ret.bool(b -> b.should(shouldFilter));
        return ret;
    }

    public static List<BucketRange> parseBuckets(Buckets<HistogramBucket> buckets, Aggregate<? extends Bucket> agg) {
        if (buckets == null || !buckets.isArray() || buckets.array().isEmpty()) {
            return List.of();
        }
        ArrayList<BucketRange> nxBuckets = new ArrayList<BucketRange>(buckets.array().size());
        for (HistogramBucket bucket : buckets.array()) {
            Double from = bucket.key();
            String key = Objects.requireNonNullElse(bucket.keyAsString(), from.toString());
            double to = from + AggregateHistogramParser.getInterval(agg);
            nxBuckets.add(new BucketRange(key, (Number)from, (Number)to, bucket.docCount()));
        }
        return nxBuckets;
    }

    public static Double getInterval(Aggregate<? extends Bucket> agg) {
        Map props = agg.getProperties();
        if (props.containsKey("interval")) {
            return Double.parseDouble((String)props.get("interval"));
        }
        throw new IllegalArgumentException("interval property must be defined for " + String.valueOf(agg));
    }

    private static int parseInt(String key) {
        if ("-Infinity".equals(key)) {
            return Integer.MIN_VALUE;
        }
        if ("+Infinity".equals(key)) {
            return Integer.MAX_VALUE;
        }
        return Math.round(Float.parseFloat(key));
    }
}

