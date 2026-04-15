/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.BucketDouble
 *  org.opensearch.client.opensearch._types.aggregations.Aggregation
 *  org.opensearch.client.opensearch._types.aggregations.AggregationBuilders
 *  org.opensearch.client.opensearch._types.aggregations.AggregationVariant
 *  org.opensearch.client.opensearch._types.aggregations.AverageAggregation$Builder
 *  org.opensearch.client.opensearch._types.aggregations.CardinalityAggregation$Builder
 *  org.opensearch.client.opensearch._types.aggregations.MaxAggregation$Builder
 *  org.opensearch.client.opensearch._types.aggregations.MinAggregation$Builder
 *  org.opensearch.client.opensearch._types.aggregations.SumAggregation$Builder
 *  org.opensearch.client.opensearch._types.aggregations.ValueCountAggregation$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import java.util.List;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateParserBase;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateTermParserBase;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.BucketDouble;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.AggregationVariant;
import org.opensearch.client.opensearch._types.aggregations.AverageAggregation;
import org.opensearch.client.opensearch._types.aggregations.CardinalityAggregation;
import org.opensearch.client.opensearch._types.aggregations.MaxAggregation;
import org.opensearch.client.opensearch._types.aggregations.MinAggregation;
import org.opensearch.client.opensearch._types.aggregations.SumAggregation;
import org.opensearch.client.opensearch._types.aggregations.ValueCountAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class AggregateSingleValueMetricParser {
    private AggregateSingleValueMetricParser() {
    }

    public static Aggregation getAggregate(Aggregate<? extends Bucket> agg) {
        String field = AggregateParserBase.getFieldName(agg.getField());
        return switch (agg.getType()) {
            case "cardinality" -> new Aggregation((AggregationVariant)((CardinalityAggregation.Builder)AggregationBuilders.cardinality().field(field)).build());
            case "count" -> new Aggregation((AggregationVariant)((ValueCountAggregation.Builder)AggregationBuilders.valueCount().field(field)).build());
            case "sum" -> new Aggregation((AggregationVariant)((SumAggregation.Builder)AggregationBuilders.sum().field(field)).build());
            case "avg" -> new Aggregation((AggregationVariant)((AverageAggregation.Builder)AggregationBuilders.avg().field(field)).build());
            case "max" -> new Aggregation((AggregationVariant)((MaxAggregation.Builder)AggregationBuilders.max().field(field)).build());
            case "min" -> new Aggregation((AggregationVariant)((MinAggregation.Builder)AggregationBuilders.min().field(field)).build());
            case "missing" -> new Aggregation((AggregationVariant)AggregationBuilders.missing().field(field).build());
            default -> throw new IllegalArgumentException("Unknown aggregate type: " + String.valueOf(agg));
        };
    }

    public static Query.Builder getFilter(Aggregate<? extends Bucket> agg) {
        return AggregateTermParserBase.getFilter(agg);
    }

    public static List<BucketDouble> parseBuckets(String type, double value) {
        return List.of(new BucketDouble(type, Double.valueOf(value)));
    }
}

