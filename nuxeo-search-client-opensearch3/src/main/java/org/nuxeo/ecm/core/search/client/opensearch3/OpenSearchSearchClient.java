/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.commons.lang3.StringUtils
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.nuxeo.ecm.core.query.QueryParseException
 *  org.nuxeo.ecm.core.search.AbstractSearchClient
 *  org.nuxeo.ecm.core.search.BulkIndexingRequest
 *  org.nuxeo.ecm.core.search.BulkIndexingResponse
 *  org.nuxeo.ecm.core.search.BulkIndexingResponse$Builder
 *  org.nuxeo.ecm.core.search.IndexingRequest
 *  org.nuxeo.ecm.core.search.SearchClient$Capability
 *  org.nuxeo.ecm.core.search.SearchClientException
 *  org.nuxeo.ecm.core.search.SearchIndex
 *  org.nuxeo.ecm.core.search.SearchQuery
 *  org.nuxeo.ecm.core.search.SearchResponse
 *  org.nuxeo.ecm.core.search.SearchScrollContext
 *  org.nuxeo.runtime.RetryableException
 *  org.nuxeo.runtime.RuntimeServiceException
 *  org.nuxeo.runtime.api.Framework
 *  org.nuxeo.runtime.model.Descriptor
 *  org.nuxeo.runtime.opensearch2.OpenSearchClientService
 *  org.nuxeo.runtime.opensearch2.OpenSearchComponent
 *  org.nuxeo.runtime.opensearch2.client.JsonTDoc
 *  org.nuxeo.runtime.opensearch2.client.OpenSearchClient
 *  org.opensearch.client.opensearch._types.ErrorCause
 *  org.opensearch.client.opensearch._types.OpenSearchException
 *  org.opensearch.client.opensearch._types.Refresh
 *  org.opensearch.client.opensearch._types.Time
 *  org.opensearch.client.opensearch._types.Time$Builder
 *  org.opensearch.client.opensearch._types.VersionType
 *  org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery
 *  org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery$Builder
 *  org.opensearch.client.opensearch.core.BulkRequest$Builder
 *  org.opensearch.client.opensearch.core.BulkResponse
 *  org.opensearch.client.opensearch.core.ClearScrollRequest
 *  org.opensearch.client.opensearch.core.ClearScrollRequest$Builder
 *  org.opensearch.client.opensearch.core.ClearScrollResponse
 *  org.opensearch.client.opensearch.core.GetRequest$Builder
 *  org.opensearch.client.opensearch.core.GetResponse
 *  org.opensearch.client.opensearch.core.ScrollRequest
 *  org.opensearch.client.opensearch.core.ScrollRequest$Builder
 *  org.opensearch.client.opensearch.core.SearchRequest
 *  org.opensearch.client.opensearch.core.SearchRequest$Builder
 *  org.opensearch.client.opensearch.core.SearchResponse
 *  org.opensearch.client.opensearch.core.bulk.DeleteOperation$Builder
 *  org.opensearch.client.opensearch.core.bulk.IndexOperation$Builder
 *  org.opensearch.client.opensearch.core.search.Hit
 *  org.opensearch.client.util.ObjectBuilder
 */
package org.nuxeo.ecm.core.search.client.opensearch3;

import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.search.AbstractSearchClient;
import org.nuxeo.ecm.core.search.BulkIndexingRequest;
import org.nuxeo.ecm.core.search.BulkIndexingResponse;
import org.nuxeo.ecm.core.search.IndexingRequest;
import org.nuxeo.ecm.core.search.SearchClient;
import org.nuxeo.ecm.core.search.SearchClientException;
import org.nuxeo.ecm.core.search.SearchIndex;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchScrollContext;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchQueryTransformer;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchResponseTransformer;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchSearchClientDescriptor;
import org.nuxeo.runtime.RetryableException;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.Descriptor;
import org.nuxeo.runtime.opensearch2.OpenSearchClientService;
import org.nuxeo.runtime.opensearch2.OpenSearchComponent;
import org.nuxeo.runtime.opensearch2.client.JsonTDoc;
import org.nuxeo.runtime.opensearch2.client.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.ClearScrollResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.util.ObjectBuilder;

public class OpenSearchSearchClient
extends AbstractSearchClient {
    private static final Logger log = LogManager.getLogger(OpenSearchSearchClient.class);
    public static final String ECM_ANCESTOR_FIELDS = "ecm:ancestorId";
    protected final OpenSearchResponseTransformer responseTransformer;
    protected final OpenSearchClient client;
    protected final Map<String, String> indexes;
    protected final OpenSearchQueryTransformer queryTransformer;

    public OpenSearchSearchClient(OpenSearchSearchClientDescriptor descriptor, Map<String, OpenSearchHintQueryBuilder> hints) {
        super((Descriptor)descriptor);
        this.client = ((OpenSearchClientService)Framework.getService(OpenSearchClientService.class)).getClient(descriptor.getClientId());
        this.indexes = descriptor.getSearchIndexes().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> ((OpenSearchSearchClientDescriptor.SearchIndex)e.getValue()).getTechnicalName()));
        this.queryTransformer = new OpenSearchQueryTransformer(this.indexes, hints);
        this.responseTransformer = new OpenSearchResponseTransformer(this.getCapabilities());
    }

    public boolean isReady() {
        return this.client.isReady((String[])this.indexes.values().toArray(String[]::new));
    }

    public boolean hasCapability(SearchClient.Capability capability) {
        return switch (capability) {
            default -> throw new MatchException(null, null);
            case SearchClient.Capability.INDEXING -> true;
            case SearchClient.Capability.HIGHLIGHT -> true;
            case SearchClient.Capability.AGGREGATE -> true;
            case SearchClient.Capability.MULTI_REPOSITORIES -> true;
        };
    }

    public void dropIndex(String name) {
        this.client.dropIndex(name);
    }

    public void dropAndInitIndex(String indexName) {
        ((OpenSearchComponent)Framework.getService(OpenSearchClientService.class)).dropAndInitIndex(this.indexes.get(indexName));
    }

    public BulkIndexingResponse indexDocuments(BulkIndexingRequest request) {
        log.debug("indexDocuments: {}", (Object)request);
        String indexName = this.indexes.get(request.getSearchIndex().index());
        BulkRequest.Builder builder = new BulkRequest.Builder().index(indexName);
        int count = 0;
        for (IndexingRequest item2 : request.getRequests()) {
            if (item2.isDelete()) {
                builder.operations(o -> o.delete(d -> (ObjectBuilder)((DeleteOperation.Builder)((DeleteOperation.Builder)d.id(item2.getDocumentId())).versionType(VersionType.External)).version(Long.valueOf(request.getVersion()))));
                if (item2.isDeleteRecursive()) {
                    ConstantScoreQuery.Builder qBuilder = new ConstantScoreQuery.Builder();
                    qBuilder.filter(t -> t.term(tt -> tt.field(ECM_ANCESTOR_FIELDS).value(v -> v.stringValue(item2.getDocumentId()))));
                    this.doRecurseDelete(indexName, qBuilder.build());
                }
                ++count;
                continue;
            }
            String source = item2.getSource();
            if (StringUtils.isEmpty((CharSequence)source)) {
                log.debug("No json source, skipping indexing command: {}", (Object)item2);
                continue;
            }
            builder.operations(o -> o.index(i -> ((IndexOperation.Builder)((IndexOperation.Builder)((IndexOperation.Builder)i.id(item2.getDocumentId())).versionType(VersionType.External)).version(Long.valueOf(request.getVersion()))).document((Object)JsonTDoc.of((String)source))));
            ++count;
        }
        if (request.isRefresh()) {
            builder.refresh(Refresh.True);
        }
        BulkIndexingResponse.Builder responseBuilder = BulkIndexingResponse.buildResponse((SearchIndex)request.getSearchIndex());
        if (count > 0) {
            try {
                BulkResponse bulkResponse = this.client.bulk(builder.build());
                bulkResponse.items().forEach(item -> {
                    if (item.error() != null) {
                        responseBuilder.addFailure(item.id(), item.error().reason());
                    }
                });
            }
            catch (RuntimeServiceException e) {
                throw new SearchClientException((Throwable)e);
            }
        }
        return responseBuilder.build();
    }

    public void refresh(String indexName) {
        try {
            this.client.refresh(this.indexes.get(indexName));
        }
        catch (RuntimeServiceException e) {
            throw new SearchClientException((Throwable)e);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected void doRecurseDelete(String indexName, ConstantScoreQuery query) {
        log.debug("delete recurse: {}", (Object)query);
        try {
            Time keepAlive = (Time)new Time.Builder().time("1m").build();
            SearchRequest request = new SearchRequest.Builder().index(indexName, new String[0]).scroll(keepAlive).query(q -> q.constantScore(query)).source(s -> s.fetch(Boolean.valueOf(false))).size(Integer.valueOf(100)).build();
            org.opensearch.client.opensearch.core.SearchResponse<?> response = null;
            try {
                response = this.client.search(request);
                while (!response.hits().hits().isEmpty()) {
                    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder().index(indexName);
                    int operations = 0;
                    for (Hit<?> hit : response.hits().hits()) {
                        bulkBuilder.operations(o -> o.delete(d -> d.id(hit.id())));
                        ++operations;
                    }
                    log.debug("Bulk delete request on {} elements", (Object)operations);
                    this.client.bulk(bulkBuilder.build());
                    response = this.client.scroll(new ScrollRequest.Builder().scrollId(response.scrollId()).scroll(keepAlive).build());
                }
            }
            finally {
                this.clearScrollContext((org.opensearch.client.opensearch.core.SearchResponse<JsonTDoc>)response);
            }
        }
        catch (RuntimeServiceException e) {
            throw new SearchClientException((Throwable)e);
        }
    }

    protected void clearScrollContext(org.opensearch.client.opensearch.core.SearchResponse<JsonTDoc> response) {
        if (response != null && response.scrollId() != null) {
            ClearScrollRequest closeScrollRequest = new ClearScrollRequest.Builder().scrollId(response.scrollId(), new String[0]).build();
            this.client.clearScroll(closeScrollRequest);
        }
    }

    public String getDocument(String indexName, String documentId) {
        try {
            GetResponse response = this.client.get(new GetRequest.Builder().index(this.indexes.get(indexName)).id(documentId).build());
            if (response.found()) {
                JsonTDoc docGet = (JsonTDoc)response.source();
                return docGet == null ? null : docGet.toJsonString();
            }
            return null;
        }
        catch (RuntimeServiceException e) {
            throw new SearchClientException((Throwable)e);
        }
    }

    public Long getDocumentVersion(String indexName, String documentId) {
        try {
            GetResponse response = this.client.get(new GetRequest.Builder().index(this.indexes.get(indexName)).id(documentId).build());
            if (response.found()) {
                return response.version();
            }
            return null;
        }
        catch (RuntimeServiceException e) {
            throw new SearchClientException((Throwable)e);
        }
    }

    public SearchResponse search(SearchQuery query) {
        try {
            SearchRequest osSearchRequest = this.queryTransformer.apply(query);
            org.opensearch.client.opensearch.core.SearchResponse osSearchResponse = this.client.search(osSearchRequest);
            if (osSearchResponse.timedOut()) {
                log.debug("Search operation timed out: {}", (Object)osSearchResponse);
                throw new RetryableException("Search operation timed out");
            }
            return this.responseTransformer.apply(query, osSearchResponse);
        }
        catch (IllegalArgumentException e) {
            throw new QueryParseException((Throwable)e);
        }
        catch (RetryableException e) {
            throw e;
        }
        catch (OpenSearchException e) {
            try {
                throw new SearchClientException(((ErrorCause)e.response().error().rootCause().getFirst()).reason(), (Exception)((Object)e));
            }
            catch (NullPointerException ee) {
                throw new SearchClientException(e.getMessage(), (Exception)((Object)e));
            }
        }
        catch (RuntimeServiceException e) {
            throw new SearchClientException((Throwable)e);
        }
    }

    public SearchResponse searchScroll(SearchScrollContext scrollContext) {
        ScrollRequest osRequest = new ScrollRequest.Builder().scrollId(scrollContext.scrollId()).scroll(OpenSearchQueryTransformer.getKeepAlive(scrollContext.searchQuery())).build();
        try {
            org.opensearch.client.opensearch.core.SearchResponse osSearchResponse = this.client.scroll(osRequest);
            if (osSearchResponse.timedOut()) {
                log.debug("SearchScroll operation timed out: {}", (Object)osSearchResponse);
                throw new RetryableException("SearchScroll operation timed out: " + String.valueOf(scrollContext));
            }
            return this.responseTransformer.apply(scrollContext.searchQuery(), osSearchResponse);
        }
        catch (RetryableException e) {
            throw e;
        }
        catch (RuntimeServiceException e) {
            throw new SearchClientException((Throwable)e);
        }
    }

    public boolean clearScroll(SearchScrollContext scrollContext) {
        ClearScrollRequest request = new ClearScrollRequest.Builder().scrollId(scrollContext.scrollId(), new String[0]).build();
        try {
            ClearScrollResponse response = this.client.clearScroll(request);
            return response.succeeded() && response.numFreed() > 0;
        }
        catch (RuntimeServiceException e) {
            throw new SearchClientException((Throwable)e);
        }
    }

    public void close() {
    }

    public Map<String, String> getTechnicalIndexes() {
        return this.indexes;
    }

    public String toString() {
        return "<OpenSearchSearchClient name=\"" + this.name + "\" clientId=\"" + this.client.getId() + "\" />";
    }
}

