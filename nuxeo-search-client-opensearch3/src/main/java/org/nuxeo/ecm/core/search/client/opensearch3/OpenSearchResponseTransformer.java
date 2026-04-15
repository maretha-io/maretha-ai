/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.nuxeo.common.utils.DateUtils
 *  org.nuxeo.ecm.core.schema.types.Type
 *  org.nuxeo.ecm.core.search.AbstractSearchResponseTransformer
 *  org.nuxeo.ecm.core.search.SearchClient$Capability
 *  org.nuxeo.ecm.core.search.SearchHit
 *  org.nuxeo.ecm.core.search.SearchQuery
 *  org.nuxeo.ecm.core.search.SearchResponse
 *  org.nuxeo.ecm.core.search.SearchResponseImpl$Builder
 *  org.nuxeo.ecm.core.search.SearchScrollContext
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.AggregateAvg
 *  org.nuxeo.ecm.platform.query.core.AggregateCardinality
 *  org.nuxeo.ecm.platform.query.core.AggregateCount
 *  org.nuxeo.ecm.platform.query.core.AggregateDateHistogram
 *  org.nuxeo.ecm.platform.query.core.AggregateDateRange
 *  org.nuxeo.ecm.platform.query.core.AggregateHistogram
 *  org.nuxeo.ecm.platform.query.core.AggregateMax
 *  org.nuxeo.ecm.platform.query.core.AggregateMin
 *  org.nuxeo.ecm.platform.query.core.AggregateMissing
 *  org.nuxeo.ecm.platform.query.core.AggregateRange
 *  org.nuxeo.ecm.platform.query.core.AggregateSignificantTerm
 *  org.nuxeo.ecm.platform.query.core.AggregateSum
 *  org.nuxeo.ecm.platform.query.core.AggregateTerm
 *  org.nuxeo.runtime.opensearch2.client.JsonTDoc
 *  org.opensearch.client.json.JsonData
 *  org.opensearch.client.opensearch._types.aggregations.Aggregate
 *  org.opensearch.client.opensearch._types.aggregations.AvgAggregate
 *  org.opensearch.client.opensearch._types.aggregations.Buckets
 *  org.opensearch.client.opensearch._types.aggregations.CardinalityAggregate
 *  org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregate
 *  org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket
 *  org.opensearch.client.opensearch._types.aggregations.DateRangeAggregate
 *  org.opensearch.client.opensearch._types.aggregations.HistogramAggregate
 *  org.opensearch.client.opensearch._types.aggregations.HistogramBucket
 *  org.opensearch.client.opensearch._types.aggregations.LongTermsBucket
 *  org.opensearch.client.opensearch._types.aggregations.MaxAggregate
 *  org.opensearch.client.opensearch._types.aggregations.MinAggregate
 *  org.opensearch.client.opensearch._types.aggregations.MissingAggregate
 *  org.opensearch.client.opensearch._types.aggregations.RangeAggregate
 *  org.opensearch.client.opensearch._types.aggregations.RangeBucket
 *  org.opensearch.client.opensearch._types.aggregations.SignificantStringTermsBucket
 *  org.opensearch.client.opensearch._types.aggregations.StringTermsBucket
 *  org.opensearch.client.opensearch._types.aggregations.SumAggregate
 *  org.opensearch.client.opensearch._types.aggregations.ValueCountAggregate
 *  org.opensearch.client.opensearch.core.SearchResponse
 *  org.opensearch.client.opensearch.core.search.Hit
 *  org.opensearch.client.opensearch.core.search.HitsMetadata
 *  org.opensearch.client.opensearch.core.search.TotalHits
 *  org.opensearch.client.opensearch.core.search.TotalHitsRelation
 */
package org.nuxeo.ecm.core.search.client.opensearch3;

import java.io.Serializable;
import java.lang.runtime.SwitchBootstraps;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.DateUtils;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.search.AbstractSearchResponseTransformer;
import org.nuxeo.ecm.core.search.SearchClient;
import org.nuxeo.ecm.core.search.SearchHit;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchResponseImpl;
import org.nuxeo.ecm.core.search.SearchScrollContext;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateDateHistogramParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateDateRangeParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateHistogramParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateParserBase;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateRangeParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateSignificantTermParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateSingleValueMetricParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateTermParser;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.AggregateAvg;
import org.nuxeo.ecm.platform.query.core.AggregateCardinality;
import org.nuxeo.ecm.platform.query.core.AggregateCount;
import org.nuxeo.ecm.platform.query.core.AggregateDateHistogram;
import org.nuxeo.ecm.platform.query.core.AggregateDateRange;
import org.nuxeo.ecm.platform.query.core.AggregateHistogram;
import org.nuxeo.ecm.platform.query.core.AggregateMax;
import org.nuxeo.ecm.platform.query.core.AggregateMin;
import org.nuxeo.ecm.platform.query.core.AggregateMissing;
import org.nuxeo.ecm.platform.query.core.AggregateRange;
import org.nuxeo.ecm.platform.query.core.AggregateSignificantTerm;
import org.nuxeo.ecm.platform.query.core.AggregateSum;
import org.nuxeo.ecm.platform.query.core.AggregateTerm;
import org.nuxeo.runtime.opensearch2.client.JsonTDoc;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.aggregations.AvgAggregate;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.CardinalityAggregate;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregate;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.DateRangeAggregate;
import org.opensearch.client.opensearch._types.aggregations.HistogramAggregate;
import org.opensearch.client.opensearch._types.aggregations.HistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.MaxAggregate;
import org.opensearch.client.opensearch._types.aggregations.MinAggregate;
import org.opensearch.client.opensearch._types.aggregations.MissingAggregate;
import org.opensearch.client.opensearch._types.aggregations.RangeAggregate;
import org.opensearch.client.opensearch._types.aggregations.RangeBucket;
import org.opensearch.client.opensearch._types.aggregations.SignificantStringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.SumAggregate;
import org.opensearch.client.opensearch._types.aggregations.ValueCountAggregate;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

public class OpenSearchResponseTransformer
extends AbstractSearchResponseTransformer<org.opensearch.client.opensearch.core.SearchResponse> {
    private static final Logger log = LogManager.getLogger(OpenSearchResponseTransformer.class);

    protected OpenSearchResponseTransformer(Set<SearchClient.Capability> supportedCapabilities) {
        super(supportedCapabilities);
    }

    public SearchResponse apply(SearchQuery searchQuery, org.opensearch.client.opensearch.core.SearchResponse osSearchResponse) {
        HitsMetadata osHits = osSearchResponse.hits();
        List<SearchHit> searchHits = this.makeSearchHits(searchQuery, osHits.hits());
        SearchResponseImpl.Builder responseBuilder = SearchResponse.builder(searchHits);
        TotalHits osTotalHits = osHits.total();
        if (osTotalHits != null) {
            responseBuilder.total(osTotalHits.value()).totalAccurate(osTotalHits.relation().equals((Object)TotalHitsRelation.Eq));
        }
        if (searchQuery.isScrollSearch()) {
            responseBuilder.scroll(new SearchScrollContext(searchQuery, osSearchResponse.scrollId()));
        }
        responseBuilder.aggregates(this.makeAggregates(searchQuery, osSearchResponse.aggregations()));
        responseBuilder.missingCapabilities(this.getMissingCapabilities(searchQuery));
        return responseBuilder.build();
    }

    protected List<SearchHit> makeSearchHits(SearchQuery searchQuery, List<Hit<JsonTDoc>> osHits) {
        ArrayList<SearchHit> hits = new ArrayList<SearchHit>(osHits.size());
        for (Hit<JsonTDoc> osHit : osHits) {
            Map osFields = osHit.fields();
            if (osFields == null) {
                log.warn("Got hit without sources: {}", osHit);
                hits.add(SearchHit.builder((String)osHit.index(), (String)osHit.id()).build());
                continue;
            }
            Map<String, Serializable> fields = this.makeFields(searchQuery, osFields, osHit.score());
            String repository = fields.containsKey("ecm:repository") ? fields.get("ecm:repository").toString() : null;
            String docId = fields.containsKey("ecm:uuid") ? fields.get("ecm:uuid").toString() : null;
            hits.add(SearchHit.builder((String)osHit.index(), (String)osHit.id()).repository(repository).docId(docId).fields(fields).highlights(osHit.highlight()).build());
        }
        return hits;
    }

    protected Map<String, Serializable> makeFields(SearchQuery searchQuery, Map<String, JsonData> source, Double score) {
        HashMap<String, Serializable> fields = new HashMap<String, Serializable>(searchQuery.getSelectFields().size());
        block16: for (Map.Entry selectedFieldEntry : searchQuery.getSelectFields().entrySet()) {
            String typeName;
            String name = (String)selectedFieldEntry.getKey();
            Type type = (Type)selectedFieldEntry.getValue();
            JsonData value = source.get(name);
            if ("ecm:fulltextScore".equals(name)) {
                fields.put(name, score);
                continue;
            }
            if (value == null) {
                fields.put(name, null);
                continue;
            }
            ArrayList obj = (ArrayList)value.to(ArrayList.class);
            switch (typeName = type == null ? "string" : type.getName()) {
                case "string": {
                    fields.put(name, (Serializable)((Object)((String)obj.getFirst())));
                    continue block16;
                }
                case "boolean": {
                    fields.put(name, (Boolean)obj.getFirst());
                    continue block16;
                }
                case "integer": {
                    fields.put(name, (Integer)obj.getFirst());
                    continue block16;
                }
                case "long": {
                    fields.put(name, (Long)obj.getFirst());
                    continue block16;
                }
                case "double": {
                    fields.put(name, (Double)obj.getFirst());
                    continue block16;
                }
                case "date": {
                    fields.put(name, DateUtils.toCalendar((Instant)Instant.from(DateTimeFormatter.ISO_INSTANT.parse((String)obj.getFirst()))));
                    continue block16;
                }
            }
            fields.put(name, (Serializable)((Object)((String)obj.getFirst())));
        }
        return fields;
    }

    protected List<Aggregate<? extends Bucket>> makeAggregates(SearchQuery searchQuery, Map<String, org.opensearch.client.opensearch._types.aggregations.Aggregate> osAggregations) {
        block16: for (Aggregate agg : searchQuery.getAggregates()) {
            String aggName = AggregateParserBase.getFilterId(agg);
            org.opensearch.client.opensearch._types.aggregations.Aggregate aggregate = osAggregations.get(aggName);
            if (aggregate != null && aggregate.isFilter()) {
                aggregate = (org.opensearch.client.opensearch._types.aggregations.Aggregate)aggregate.filter().aggregations().get(agg.getId());
            }
            if (aggregate == null) continue;
            org.opensearch.client.opensearch._types.aggregations.Aggregate finalAggregate = aggregate;
            Objects.requireNonNull(agg);
            if (agg instanceof AggregateTerm && finalAggregate.isSterms()) {
                ((AggregateTerm) agg).setBuckets(AggregateTermParser.parseBuckets((Buckets<StringTermsBucket>)finalAggregate.sterms().buckets()));
            } else if (agg instanceof AggregateTerm && finalAggregate.isLterms()) {
                ((AggregateTerm) agg).setBuckets(AggregateTermParser.parseLongBuckets((Buckets<LongTermsBucket>)finalAggregate.lterms().buckets()));
            } else if (agg instanceof AggregateSignificantTerm && finalAggregate.isSigsterms()) {
                ((AggregateSignificantTerm) agg).setBuckets(AggregateSignificantTermParser.parseBuckets((Buckets<SignificantStringTermsBucket>)finalAggregate.sigsterms().buckets()));
            } else if (agg instanceof AggregateRange && finalAggregate.isRange()) {
                ((AggregateRange) agg).setBuckets(AggregateRangeParser.parseBuckets((Buckets<RangeBucket>)finalAggregate.range().buckets()));
            } else if (agg instanceof AggregateDateRange && finalAggregate.isDateRange()) {
                ((AggregateDateRange) agg).setBuckets(AggregateDateRangeParser.parseBuckets((Buckets<RangeBucket>)finalAggregate.dateRange().buckets()));
            } else if (agg instanceof AggregateHistogram && finalAggregate.isHistogram()) {
                ((AggregateHistogram) agg).setBuckets(AggregateHistogramParser.parseBuckets((Buckets<HistogramBucket>)finalAggregate.histogram().buckets(), (Aggregate<? extends Bucket>)agg));
            } else if (agg instanceof AggregateDateHistogram && finalAggregate.isDateHistogram()) {
                ((AggregateDateHistogram) agg).setBuckets(AggregateDateHistogramParser.parseBuckets((Buckets<DateHistogramBucket>)finalAggregate.dateHistogram().buckets()));
            } else if (agg instanceof AggregateSum && finalAggregate.isSum()) {
                ((AggregateSum) agg).setBuckets(AggregateSingleValueMetricParser.parseBuckets(finalAggregate.sum()._aggregateKind().name().toLowerCase(), finalAggregate.sum().value()));
            } else if (agg instanceof AggregateMax && finalAggregate.isMax()) {
                ((AggregateMax) agg).setBuckets(AggregateSingleValueMetricParser.parseBuckets(finalAggregate.max()._aggregateKind().name().toLowerCase(), finalAggregate.max().value()));
            } else if (agg instanceof AggregateMin && finalAggregate.isMin()) {
                ((AggregateMin) agg).setBuckets(AggregateSingleValueMetricParser.parseBuckets(finalAggregate.min()._aggregateKind().name().toLowerCase(), finalAggregate.min().value()));
            } else if (agg instanceof AggregateAvg && finalAggregate.isAvg()) {
                ((AggregateAvg) agg).setBuckets(AggregateSingleValueMetricParser.parseBuckets(finalAggregate.avg()._aggregateKind().name().toLowerCase(), finalAggregate.avg().value()));
            } else if (agg instanceof AggregateCount && finalAggregate.isValueCount()) {
                ((AggregateCount) agg).setBuckets(AggregateSingleValueMetricParser.parseBuckets(finalAggregate.valueCount()._aggregateKind().name().toLowerCase(), finalAggregate.valueCount().value()));
            } else if (agg instanceof AggregateCardinality && finalAggregate.isCardinality()) {
                ((AggregateCardinality) agg).setBuckets(AggregateSingleValueMetricParser.parseBuckets(finalAggregate.cardinality()._aggregateKind().name().toLowerCase(), finalAggregate.cardinality().value()));
            } else if (agg instanceof AggregateMissing && finalAggregate.isMissing()) {
                ((AggregateMissing) agg).setBuckets(AggregateSingleValueMetricParser.parseBuckets(finalAggregate.missing()._aggregateKind().name().toLowerCase(), (double)finalAggregate.missing().docCount()));
            } else {
                throw new IllegalStateException("Invalid aggregate kind: " + finalAggregate._kind() + " for " + agg);
            }
        }
        return searchQuery.getAggregates();
    }
}

