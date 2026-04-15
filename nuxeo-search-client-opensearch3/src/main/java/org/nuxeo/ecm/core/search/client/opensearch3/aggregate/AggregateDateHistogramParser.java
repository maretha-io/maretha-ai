/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.commons.lang3.StringUtils
 *  org.nuxeo.common.utils.DateUtils
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.BucketRangeDate
 *  org.opensearch.client.json.JsonData
 *  org.opensearch.client.opensearch._types.SortOrder
 *  org.opensearch.client.opensearch._types.aggregations.Aggregation
 *  org.opensearch.client.opensearch._types.aggregations.AggregationBuilders
 *  org.opensearch.client.opensearch._types.aggregations.AggregationVariant
 *  org.opensearch.client.opensearch._types.aggregations.Buckets
 *  org.opensearch.client.opensearch._types.aggregations.CalendarInterval
 *  org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregation$Builder
 *  org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket
 *  org.opensearch.client.opensearch._types.aggregations.FieldDateMath
 *  org.opensearch.client.opensearch._types.query_dsl.Query
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.QueryBuilders
 *  org.opensearch.client.opensearch._types.query_dsl.QueryVariant
 *  org.opensearch.client.opensearch._types.query_dsl.RangeQuery$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.utils.DateUtils;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateParserBase;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.DateHelper;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.BucketRangeDate;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.AggregationVariant;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.CalendarInterval;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregation;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.FieldDateMath;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;

public final class AggregateDateHistogramParser {
    private AggregateDateHistogramParser() {
    }

    public static Aggregation getAggregate(Aggregate<? extends Bucket> agg) {
        DateHistogramAggregation.Builder builder = AggregationBuilders.dateHistogram();
        builder.field(AggregateParserBase.getFieldName(agg.getField()));
        builder.timeZone(ZoneId.systemDefault().getId());
        Map props = agg.getProperties();
        if (props.containsKey("interval")) {
            builder.calendarInterval(CalendarInterval.valueOf((String)StringUtils.capitalize((String)((String)props.get("interval")))));
        }
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
            builder.extendedBounds(e -> e.max(FieldDateMath.of(f -> f.expr((String)props.get("extendedBoundsMax")))).min(FieldDateMath.of(f -> f.expr((String)props.get("extendedBoundsMin")))));
        }
        if (props.containsKey("timeZone")) {
            builder.timeZone((String)props.get("timeZone"));
        }
        if (props.containsKey("preZone")) {
            builder.timeZone((String)props.get("preZone"));
        }
        if (props.containsKey("format")) {
            builder.format((String)props.get("format"));
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
            ZonedDateTime from = AggregateDateHistogramParser.convertStringToDate(sel, agg);
            ZonedDateTime to = DateHelper.plusDuration(from, AggregateDateHistogramParser.getInterval(agg));
            rangeFilter.gte(JsonData.of((Object)from.toInstant().toEpochMilli())).lt(JsonData.of((Object)to.toInstant().toEpochMilli())).format("epoch_millis");
            shouldFilter.add(new Query((QueryVariant)rangeFilter.build()));
        }
        Query.Builder ret = new Query.Builder();
        ret.bool(b -> b.should(shouldFilter));
        return ret;
    }

    private static ZonedDateTime convertStringToDate(String date, Aggregate<? extends Bucket> agg) {
        String timezone = "UTC";
        Map props = agg.getProperties();
        if (props.containsKey("timeZone")) {
            timezone = (String)props.get("timeZone");
        }
        if (!props.containsKey("format")) {
            throw new IllegalArgumentException("format property must be defined for " + String.valueOf(agg));
        }
        DateTimeFormatter fmt = DateUtils.robustOfPattern((String)((String)props.get("format"))).withZone(ZoneId.of(timezone));
        TemporalAccessor ta = fmt.parseBest(date, ZonedDateTime::from, LocalDate::from);
        if (ta instanceof LocalDate) {
            return ((LocalDate)ta).atStartOfDay(ZoneId.of(timezone));
        }
        return (ZonedDateTime)ta;
    }

    public static List<BucketRangeDate> parseBuckets(Buckets<DateHistogramBucket> buckets) {
        if (buckets == null || !buckets.isArray() || buckets.array().isEmpty()) {
            return List.of();
        }
        ArrayList<BucketRangeDate> nxBuckets = new ArrayList<BucketRangeDate>(buckets.array().size());
        for (DateHistogramBucket bucket : buckets.array()) {
            ZonedDateTime fromZDT = null;
            ZonedDateTime toZDT = null;
            nxBuckets.add(new BucketRangeDate(bucket.keyAsString(), fromZDT, toZDT, bucket.docCount()));
        }
        return nxBuckets;
    }

    private static String getInterval(Aggregate<? extends Bucket> agg) {
        Map props = agg.getProperties();
        if (!props.containsKey("interval")) {
            throw new IllegalArgumentException("interval property must be defined for " + String.valueOf(agg));
        }
        String ret = (String)props.get("interval");
        return ret;
    }
}

