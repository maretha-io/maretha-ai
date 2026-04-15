/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.BucketTerm
 *  org.opensearch.client.opensearch._types.aggregations.Aggregation
 *  org.opensearch.client.opensearch._types.aggregations.AggregationBuilders
 *  org.opensearch.client.opensearch._types.aggregations.AggregationVariant
 *  org.opensearch.client.opensearch._types.aggregations.Buckets
 *  org.opensearch.client.opensearch._types.aggregations.SignificantStringTermsBucket
 *  org.opensearch.client.opensearch._types.aggregations.SignificantTermsAggregation$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateParserBase;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateTermParserBase;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.BucketTerm;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.AggregationVariant;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.SignificantStringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.SignificantTermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class AggregateSignificantTermParser {
    private AggregateSignificantTermParser() {
    }

    public static Aggregation getAggregate(Aggregate<? extends Bucket> agg) {
        SignificantTermsAggregation.Builder ret = AggregationBuilders.significantTerms();
        ret.field(AggregateParserBase.getFieldName(agg.getField()));
        Map props = agg.getProperties();
        if (props.containsKey("size")) {
            ret.size(Integer.valueOf(AggregateTermParserBase.getAggSize((String)props.get("size"))));
        }
        if (props.containsKey("minDocCount")) {
            ret.minDocCount(Long.valueOf(Long.parseLong((String)props.get("minDocCount"))));
        }
        if (props.containsKey("exclude")) {
            String exclude = (String)props.get("exclude");
            ret.exclude(e -> e.regexp(exclude));
        }
        if (props.containsKey("include")) {
            String include = (String)props.get("include");
            ret.include(i -> i.regexp(include));
        }
        return new Aggregation((AggregationVariant)ret.build());
    }

    public static Query.Builder getFilter(Aggregate<? extends Bucket> agg) {
        return AggregateTermParserBase.getFilter(agg);
    }

    public static List<BucketTerm> parseBuckets(Buckets<SignificantStringTermsBucket> buckets) {
        if (buckets == null || !buckets.isArray() || buckets.array().isEmpty()) {
            return List.of();
        }
        ArrayList<BucketTerm> nxBuckets = new ArrayList<BucketTerm>(buckets.array().size());
        for (SignificantStringTermsBucket bucket : buckets.array()) {
            nxBuckets.add(new BucketTerm(bucket.key(), bucket.docCount()));
        }
        return nxBuckets;
    }
}

