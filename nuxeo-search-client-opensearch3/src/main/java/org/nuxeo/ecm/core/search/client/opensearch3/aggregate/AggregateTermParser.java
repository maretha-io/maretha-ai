/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.BucketTerm
 *  org.opensearch.client.opensearch._types.SortOrder
 *  org.opensearch.client.opensearch._types.aggregations.Aggregation
 *  org.opensearch.client.opensearch._types.aggregations.AggregationBuilders
 *  org.opensearch.client.opensearch._types.aggregations.AggregationVariant
 *  org.opensearch.client.opensearch._types.aggregations.Buckets
 *  org.opensearch.client.opensearch._types.aggregations.LongTermsBucket
 *  org.opensearch.client.opensearch._types.aggregations.StringTermsBucket
 *  org.opensearch.client.opensearch._types.aggregations.TermsAggregation$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import java.util.List;
import java.util.Map;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateParserBase;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateTermParserBase;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.BucketTerm;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.AggregationVariant;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class AggregateTermParser {
    private AggregateTermParser() {
    }

    public static Aggregation getAggregate(Aggregate<? extends Bucket> agg) {
        TermsAggregation.Builder ret = AggregationBuilders.terms();
        ret.field(AggregateParserBase.getFieldName(agg.getField()));
        Map props = agg.getProperties();
        if (props.containsKey("size")) {
            ret.size(Integer.valueOf(AggregateTermParserBase.getAggSize((String)props.get("size"))));
        }
        if (props.containsKey("minDocCount")) {
            ret.minDocCount(Integer.valueOf(Integer.parseInt((String)props.get("minDocCount"))));
        }
        if (props.containsKey("exclude")) {
            String exclude = (String)props.get("exclude");
            ret.exclude(e -> e.regexp(exclude));
        }
        if (props.containsKey("include")) {
            String include = (String)props.get("include");
            ret.include(e -> e.regexp(include));
        }
        if (props.containsKey("order")) {
            switch (((String)props.get("order")).toLowerCase()) {
                case "count desc": {
                    ret.order(Map.of("_count", SortOrder.Desc), new Map[0]);
                    break;
                }
                case "count asc": {
                    ret.order(Map.of("_count", SortOrder.Asc), new Map[0]);
                    break;
                }
                case "term desc": {
                    ret.order(Map.of("_term", SortOrder.Desc), new Map[0]);
                    break;
                }
                case "term asc": {
                    ret.order(Map.of("_term", SortOrder.Asc), new Map[0]);
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Invalid order: " + (String)props.get("order"));
                }
            }
        }
        return new Aggregation((AggregationVariant)ret.build());
    }

    public static Query.Builder getFilter(Aggregate<? extends Bucket> agg) {
        return AggregateTermParserBase.getFilter(agg);
    }

    public static List<BucketTerm> parseBuckets(Buckets<StringTermsBucket> buckets) {
        return AggregateTermParserBase.parseBuckets(buckets);
    }

    public static List<BucketTerm> parseLongBuckets(Buckets<LongTermsBucket> buckets) {
        return AggregateTermParserBase.parseLongBuckets(buckets);
    }
}

