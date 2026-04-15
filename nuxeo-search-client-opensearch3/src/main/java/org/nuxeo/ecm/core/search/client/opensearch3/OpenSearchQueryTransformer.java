/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.commons.lang3.ArrayUtils
 *  org.apache.commons.lang3.StringUtils
 *  org.nuxeo.ecm.core.api.NuxeoPrincipal
 *  org.nuxeo.ecm.core.query.QueryParseException
 *  org.nuxeo.ecm.core.query.sql.NXQL
 *  org.nuxeo.ecm.core.query.sql.model.DefaultQueryVisitor
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.nuxeo.ecm.core.query.sql.model.EsHint$FieldHint
 *  org.nuxeo.ecm.core.query.sql.model.Expression
 *  org.nuxeo.ecm.core.query.sql.model.FromClause
 *  org.nuxeo.ecm.core.query.sql.model.FromList
 *  org.nuxeo.ecm.core.query.sql.model.Function
 *  org.nuxeo.ecm.core.query.sql.model.IVisitor
 *  org.nuxeo.ecm.core.query.sql.model.Literal
 *  org.nuxeo.ecm.core.query.sql.model.LiteralList
 *  org.nuxeo.ecm.core.query.sql.model.MultiExpression
 *  org.nuxeo.ecm.core.query.sql.model.Operand
 *  org.nuxeo.ecm.core.query.sql.model.Operator
 *  org.nuxeo.ecm.core.query.sql.model.OrderByExpr
 *  org.nuxeo.ecm.core.query.sql.model.Predicate
 *  org.nuxeo.ecm.core.query.sql.model.Reference
 *  org.nuxeo.ecm.core.query.sql.model.SelectClause
 *  org.nuxeo.ecm.core.query.sql.model.StringLiteral
 *  org.nuxeo.ecm.core.schema.SchemaManager
 *  org.nuxeo.ecm.core.schema.types.Field
 *  org.nuxeo.ecm.core.schema.utils.DateParser
 *  org.nuxeo.ecm.core.search.SearchIndex
 *  org.nuxeo.ecm.core.search.SearchQuery
 *  org.nuxeo.ecm.core.search.SearchQueryTransformer
 *  org.nuxeo.ecm.core.security.SecurityService
 *  org.nuxeo.ecm.platform.query.api.Aggregate
 *  org.nuxeo.ecm.platform.query.api.Bucket
 *  org.nuxeo.ecm.platform.query.core.AggregateDateRange
 *  org.nuxeo.runtime.api.Framework
 *  org.nuxeo.runtime.services.config.ConfigurationService
 *  org.opensearch.client.json.JsonData
 *  org.opensearch.client.opensearch._types.FieldValue
 *  org.opensearch.client.opensearch._types.SearchType
 *  org.opensearch.client.opensearch._types.SortOptions
 *  org.opensearch.client.opensearch._types.SortOptions$Builder
 *  org.opensearch.client.opensearch._types.SortOrder
 *  org.opensearch.client.opensearch._types.Time
 *  org.opensearch.client.opensearch._types.Time$Builder
 *  org.opensearch.client.opensearch._types.aggregations.Aggregation
 *  org.opensearch.client.opensearch._types.mapping.FieldType
 *  org.opensearch.client.opensearch._types.query_dsl.BoolQuery$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.FieldAndFormat
 *  org.opensearch.client.opensearch._types.query_dsl.Operator
 *  org.opensearch.client.opensearch._types.query_dsl.Query
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 *  org.opensearch.client.opensearch._types.query_dsl.QueryBuilders
 *  org.opensearch.client.opensearch.core.SearchRequest
 *  org.opensearch.client.opensearch.core.SearchRequest$Builder
 *  org.opensearch.client.opensearch.core.search.Highlight
 *  org.opensearch.client.opensearch.core.search.Highlight$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.query.sql.model.DefaultQueryVisitor;
import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.FromClause;
import org.nuxeo.ecm.core.query.sql.model.FromList;
import org.nuxeo.ecm.core.query.sql.model.Function;
import org.nuxeo.ecm.core.query.sql.model.IVisitor;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.utils.DateParser;
import org.nuxeo.ecm.core.search.SearchIndex;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchQueryTransformer;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateDateHistogramParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateDateRangeParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateHistogramParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateParserBase;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateRangeParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateSignificantTermParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateSingleValueMetricParser;
import org.nuxeo.ecm.core.search.client.opensearch3.aggregate.AggregateTermParser;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.AggregateDateRange;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.config.ConfigurationService;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SearchType;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.mapping.FieldType;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.FieldAndFormat;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Highlight;

public class OpenSearchQueryTransformer
implements SearchQueryTransformer<SearchRequest> {
    public static final String FULLTEXT_FIELD = "all_field";
    public static final int DEFAULT_MAX_EXPANSIONS = 50;
    protected static final String SIMPLE_QUERY_PREFIX = "es: ";
    protected static final String TYPE_DOCUMENT = "Document";
    protected static final String SCORE_FIELD = "_score";
    protected static final long MAX_KEEP_ALIVE_SECONDS = Duration.ofDays(1L).minusMinutes(10L).toSeconds();
    protected final Map<String, String> indexes;
    protected final Map<String, OpenSearchHintQueryBuilder> hintBuilders;

    public OpenSearchQueryTransformer(Map<String, String> indexes, Map<String, OpenSearchHintQueryBuilder> hintBuilders) {
        this.indexes = indexes;
        this.hintBuilders = hintBuilders;
    }

    public SearchRequest apply(SearchQuery searchQuery) {
        SearchRequest.Builder osSearchBuilder = new SearchRequest.Builder();
        osSearchBuilder.trackTotalHits(t -> t.enabled(Boolean.valueOf(true)));
        osSearchBuilder.source(s -> s.fetch(Boolean.valueOf(false)));
        List<FieldAndFormat> fields = searchQuery.getSelectFields().keySet().stream().map(f -> FieldAndFormat.of(ff -> ff.field(f))).toList();
        osSearchBuilder.fields(fields);
        if (searchQuery.isScrollSearch()) {
            osSearchBuilder.size(Integer.valueOf(searchQuery.getScrollSize()));
        } else {
            osSearchBuilder.from(Integer.valueOf(searchQuery.getOffset())).size(Integer.valueOf(searchQuery.getLimit()));
        }
        osSearchBuilder.sort(this.makeSortBuilders(searchQuery));
        osSearchBuilder.highlight(this.makeHighlighter(searchQuery));
        osSearchBuilder.aggregations(this.makeAggregationBuilders(searchQuery));
        osSearchBuilder.postFilter(this.makeAggregatePostFilter(searchQuery));
        Query.Builder osQueryBuilder = this.makeQueryBuilder(searchQuery);
        osSearchBuilder.query(osQueryBuilder.build());
        List<String> osIndexes = searchQuery.getSearchIndexes().stream().map(SearchIndex::index).map(this.indexes::get).toList();
        osSearchBuilder.index(osIndexes);
        osSearchBuilder.searchType(SearchType.DfsQueryThenFetch);
        if (searchQuery.isScrollSearch()) {
            osSearchBuilder.scroll(OpenSearchQueryTransformer.getKeepAlive(searchQuery));
        }
        return osSearchBuilder.build();
    }

    protected Query.Builder makeQueryBuilder(SearchQuery searchQuery) {
        final LinkedList<ExpressionBuilder> builders = new LinkedList<ExpressionBuilder>();
        ExpressionBuilder ret = new ExpressionBuilder();
        builders.add(ret);
        final ArrayList fromList = new ArrayList();
        searchQuery.getQuery().accept((IVisitor)new DefaultQueryVisitor(){

            public void visitFromClause(FromClause node) {
                FromList elements = node.elements;
                SchemaManager schemaManager = (SchemaManager)Framework.getService(SchemaManager.class);
                for (String type : elements.values()) {
                    if (OpenSearchQueryTransformer.TYPE_DOCUMENT.equalsIgnoreCase(type)) {
                        fromList.clear();
                        return;
                    }
                    Set types = schemaManager.getDocumentTypeNamesExtending(type);
                    if (types == null) {
                        throw new QueryParseException("Unknown type: " + type);
                    }
                    fromList.addAll(types);
                }
            }

            public void visitMultiExpression(MultiExpression node) {
                Iterator it = node.predicates.iterator();
                while (it.hasNext()) {
                    ((Predicate)it.next()).accept((IVisitor)this);
                    if (!it.hasNext()) continue;
                    node.operator.accept((IVisitor)this);
                }
            }

            public void visitSelectClause(SelectClause node) {
            }

            /*
             * WARNING - void declaration
             * Enabled force condition propagation
             * Lifted jumps to return sites
             */
            public void visitExpression(Expression node) {
                Operator op = node.operator;
                if (op == Operator.AND || op == Operator.OR || op == Operator.NOT) {
                    builders.add(new ExpressionBuilder(op.toString()));
                    super.visitExpression(node);
                    ExpressionBuilder expr = (ExpressionBuilder)builders.removeLast();
                    if (builders.isEmpty()) return;
                    ((ExpressionBuilder)builders.getLast()).merge(expr);
                    return;
                } else {
                    Reference ref = node.lvalue instanceof Reference ? (Reference)node.lvalue : null;
                    String name = ref != null ? ref.name : node.lvalue.toString();
                    String value = null;
                    if (node.rvalue instanceof Literal) {
                        value = ((Literal)node.rvalue).asString();
                    } else {
                        Operand operand = node.rvalue;
                        if (operand instanceof Function) {
                            Function function = (Function)operand;
                            String func = function.name;
                            if (!"NOW".equalsIgnoreCase(func)) throw new IllegalArgumentException("Unknown function: " + func);
                            String durationStr = null;
                            if (function.args != null && function.args.size() == 1) {
                                durationStr = ((StringLiteral)function.args.get(0)).value;
                            }
                            ZonedDateTime dateTime = NXQL.nowPlusPeriodAndDuration((String)durationStr);
                            GregorianCalendar calendar = GregorianCalendar.from(dateTime);
                            value = DateParser.formatW3CDateTime((Calendar)calendar);
                        } else if (node.rvalue != null) {
                            value = node.rvalue.toString();
                        }
                    }
                    Object[] values = null;
                    Operand operandList = node.rvalue;
                    if (operandList instanceof LiteralList) {
                        LiteralList items = (LiteralList)operandList;
                        values = new Object[items.size()];
                        int idx = 0;
                        for (Literal item : items) {
                            values[idx++] = item.asString();
                        }
                    }
                    EsHint hint = ref != null ? ref.esHint : null;
                    ((ExpressionBuilder)builders.getLast()).add(OpenSearchQueryTransformer.this.makeQueryFromSimpleExpression(op.toString(), name, value, values, hint));
                }
            }
        });
        Query.Builder queryBuilder = ret.get();
        NuxeoPrincipal principal = searchQuery.getPrincipal();
        if (principal != null && !principal.isAdministrator()) {
            Query.Builder aclFilter = this.getAclFilter(principal);
            Query subQuery = queryBuilder.build();
            queryBuilder = new Query.Builder();
            queryBuilder.bool(b -> b.must(subQuery, new Query[0]).filter(aclFilter.build(), new Query[0]));
        }
        if (!fromList.isEmpty()) {
            Query subQuery = queryBuilder.build();
            queryBuilder = new Query.Builder();
            queryBuilder.bool(b -> b.must(subQuery, new Query[0]).filter(this.makeQueryFromSimpleExpression((String)"IN", (String)"ecm:primaryType", null, (Object[])fromList.toArray(), null).filter.build(), new Query[0]));
        }
        return queryBuilder;
    }

    protected Query.Builder getAclFilter(NuxeoPrincipal principal) {
        Query.Builder aclFilter = new Query.Builder();
        aclFilter.bool(b -> b.must(t -> t.terms(a -> a.field("ecm:acl").terms(v -> v.value(this.getTermValues(SecurityService.getPrincipalsToCheck((NuxeoPrincipal)principal)))))).mustNot(t -> t.terms(a -> a.field("ecm:acl").terms(v -> v.value(List.of(FieldValue.of((String)"_UNSUPPORTED_ACL_")))))));
        return aclFilter;
    }

    private List<FieldValue> getTermValues(Object[] terms) {
        ArrayList<FieldValue> termValues = new ArrayList<FieldValue>(terms.length);
        Arrays.asList(terms).forEach(v -> termValues.add(FieldValue.of((String)((String)v))));
        return termValues;
    }

    protected QueryAndFilter makeQueryFromSimpleExpression(String op, String nxqlName, Object value, Object[] values, EsHint hint) {
        Query.Builder query = null;
        Query.Builder filter = null;
        String name = this.getFieldName(nxqlName, hint);
        if (hint != null && hint.operator != null) {
            if (ArrayUtils.isNotEmpty((Object[])values)) {
                filter = this.makeHintQuery(name, values, hint);
            } else {
                query = this.makeHintQuery(name, value, hint);
            }
        } else if (nxqlName.startsWith("ecm:fulltext") && ("=".equals(op) || "!=".equals(op) || "<>".equals(op) || "LIKE".equals(op) || "NOT LIKE".equals(op))) {
            query = this.makeFulltextQuery(nxqlName, (String)value, hint);
            if ("!=".equals(op) || "<>".equals(op) || "NOT LIKE".equals(op)) {
                filter = new Query.Builder();
                Query fQuery = query.build();
                filter.bool(b -> b.mustNot(fQuery, new Query[0]));
                query = null;
            }
        } else {
            switch (op) {
                case "=": {
                    filter = new Query.Builder();
                    filter.term(t -> t.field(name).value(v -> v.stringValue((String)this.checkBoolValue(nxqlName, value))));
                    break;
                }
                case "<>": 
                case "!=": {
                    filter = new Query.Builder();
                    filter.bool(b -> b.mustNot(a -> a.term(t -> t.field(name).value(v -> v.stringValue((String)this.checkBoolValue(nxqlName, value))))));
                    break;
                }
                case ">": {
                    filter = new Query.Builder();
                    filter.range(r -> r.field(name).gt(JsonData.of((Object)value)));
                    break;
                }
                case "<": {
                    filter = new Query.Builder();
                    filter.range(r -> r.field(name).lt(JsonData.of((Object)value)));
                    break;
                }
                case ">=": {
                    filter = new Query.Builder();
                    filter.range(r -> r.field(name).gte(JsonData.of((Object)value)));
                    break;
                }
                case "<=": {
                    filter = new Query.Builder();
                    filter.range(r -> r.field(name).lte(JsonData.of((Object)value)));
                    break;
                }
                case "BETWEEN": 
                case "NOT BETWEEN": {
                    filter = new Query.Builder();
                    filter.range(r -> r.field(name).from(JsonData.of((Object)values[0])).to(JsonData.of((Object)values[1])));
                    if (!op.startsWith("NOT")) break;
                    Query subfilter = filter.build();
                    filter = new Query.Builder();
                    filter.bool(b -> b.mustNot(subfilter, new Query[0]));
                    break;
                }
                case "IN": 
                case "NOT IN": {
                    filter = new Query.Builder();
                    filter.terms(t -> t.field(name).terms(v -> v.value(this.getTermValues(values))));
                    if (!op.startsWith("NOT")) break;
                    Query subfilter = filter.build();
                    filter = new Query.Builder();
                    filter.bool(b -> b.mustNot(subfilter, new Query[0]));
                    break;
                }
                case "IS NULL": {
                    filter = new Query.Builder();
                    filter.bool(b -> b.mustNot(e -> e.exists(t -> t.field(name))));
                    break;
                }
                case "IS NOT NULL": {
                    filter = new Query.Builder();
                    filter.exists(t -> t.field(name));
                    break;
                }
                case "LIKE": 
                case "ILIKE": 
                case "NOT LIKE": 
                case "NOT ILIKE": {
                    query = this.makeLikeQuery(op, name, (String)value, hint);
                    if (!op.startsWith("NOT")) break;
                    Query q = query.build();
                    filter = new Query.Builder();
                    filter.bool(b -> b.mustNot(q, new Query[0]));
                    query = null;
                    break;
                }
                case "STARTSWITH": {
                    filter = this.makeStartsWithQuery(name, value);
                    break;
                }
                default: {
                    throw new UnsupportedOperationException("Operator: '" + op + "' is unknown");
                }
            }
        }
        return new QueryAndFilter(query, filter);
    }

    protected Object checkBoolValue(String nxqlName, Object value) {
        if (!"0".equals(value) && !"1".equals(value)) {
            return value;
        }
        switch (nxqlName) {
            case "ecm:isProxy": 
            case "ecm:isCheckedIn": 
            case "ecm:isTrashed": 
            case "ecm:isVersion": 
            case "ecm:isCheckedInVersion": 
            case "ecm:isRecord": 
            case "ecm:isFlexibleRecord": 
            case "ecm:hasLegalHold": 
            case "ecm:isLatestMajorVersion": 
            case "ecm:isLatestVersion": {
                break;
            }
            default: {
                SchemaManager schemaManager = (SchemaManager)Framework.getService(SchemaManager.class);
                Field field = schemaManager.getField(nxqlName);
                if (field != null && "boolean".equals(field.getType().getName())) break;
                return value;
            }
        }
        return "0".equals(value) ? "false" : "true";
    }

    protected Query.Builder makeHintQuery(String name, Object value, EsHint hint) {
        OpenSearchHintQueryBuilder hintBuilder = this.hintBuilders.get(hint.operator);
        if (hintBuilder == null) {
            throw new QueryParseException(String.format("Operator: %s is unknown", hint.operator));
        }
        return hintBuilder.make(hint, name, value);
    }

    protected Query.Builder makeStartsWithQuery(String name, Object value) {
        Query.Builder filter = new Query.Builder();
        String indexName = name + ".children";
        if ("/".equals(value)) {
            if ("ecm:path".equals(name)) {
                filter.exists(e -> e.field("ecm:parentId"));
            } else {
                filter.exists(e -> e.field(indexName));
            }
        } else {
            String v = String.valueOf(value);
            if (v.endsWith("/")) {
                v = v.replaceAll("/$", "");
            }
            String fv = v;
            if ("ecm:path".equals(name)) {
                filter.bool(b -> b.must(m -> m.term(t -> t.field(indexName).value(s -> s.stringValue(fv)))).mustNot(n -> n.term(f -> f.field(name).value(s -> s.stringValue(fv)))));
            } else {
                filter.term(t -> t.field(indexName).value(s -> s.stringValue(fv)));
            }
        }
        return filter;
    }

    protected Query.Builder makeLikeQuery(String op, String name, String value, EsHint hint) {
        Object fName = name;
        if (op.contains("ILIKE")) {
            value = value.toLowerCase();
            fName = name + ".lowercase";
        }
        if (hint != null && hint.index != null) {
            fName = hint.index;
        }
        String fieldName = (String) fName;
        String wildcard = this.likeToWildcard(value);
        if (StringUtils.countMatches((CharSequence)wildcard, (CharSequence)"*") == 1 && wildcard.endsWith("*") && !wildcard.contains("?") && !wildcard.contains("\\")) {
            Query.Builder query = new Query.Builder();
            ConfigurationService cs = (ConfigurationService)Framework.getService(ConfigurationService.class);
            int maxExp = cs.getInteger("opensearch2.max_expansions", 50);
            String analyzer = hint != null ? hint.analyzer : null;
            query.matchPhrasePrefix(m -> m.field(fieldName).query(wildcard.replace("*", "")).maxExpansions(Integer.valueOf(maxExp)).analyzer(analyzer));
            return query;
        }
        Query.Builder qb = new Query.Builder();
        qb.wildcard(w -> w.field(fieldName).wildcard(wildcard));
        return qb;
    }

    protected String likeToWildcard(String like) {
        StringBuilder wildcard = new StringBuilder();
        char[] chars = like.toCharArray();
        boolean escape = false;
        for (char c : chars) {
            boolean escapeNext = false;
            switch (c) {
                case '?': {
                    wildcard.append("\\?");
                    break;
                }
                case '%': 
                case '*': {
                    if (escape) {
                        wildcard.append(c);
                        break;
                    }
                    wildcard.append("*");
                    break;
                }
                case '_': {
                    if (escape) {
                        wildcard.append(c);
                        break;
                    }
                    wildcard.append("?");
                    break;
                }
                case '\\': {
                    if (escape) {
                        wildcard.append("\\\\");
                        break;
                    }
                    escapeNext = true;
                    break;
                }
                default: {
                    wildcard.append(c);
                }
            }
            escape = escapeNext;
        }
        return wildcard.toString();
    }

    protected Query.Builder makeFulltextQuery(String nxqlName, String value, EsHint hint) {
        org.opensearch.client.opensearch._types.query_dsl.Operator defaultOperator;
        String queryString;
        Object name = nxqlName.replace("ecm:fulltext", "");
        name = ((String)name).startsWith(".") ? ((String)name).substring(1) + ".fulltext" : FULLTEXT_FIELD;
        if (value.startsWith(SIMPLE_QUERY_PREFIX)) {
            queryString = value.substring(SIMPLE_QUERY_PREFIX.length());
            defaultOperator = org.opensearch.client.opensearch._types.query_dsl.Operator.Or;
        } else {
            queryString = this.translateFulltextQuery(value);
            defaultOperator = org.opensearch.client.opensearch._types.query_dsl.Operator.And;
        }
        String analyzer = hint != null && hint.analyzer != null ? hint.analyzer : "fulltext";
        Query.Builder queryBuilder = new Query.Builder();
        ArrayList<String> fields = new ArrayList<String>();
        if (hint != null && hint.index != null) {
            for (EsHint.FieldHint fieldHint : hint.getIndex()) {
                fields.add(OpenSearchQueryTransformer.getBoostedField(fieldHint));
            }
        } else {
            fields.add((String)name);
        }
        queryBuilder.simpleQueryString(q -> q.fields(fields).query(queryString).defaultOperator(defaultOperator).analyzer(analyzer));
        return queryBuilder;
    }

    public static String getBoostedField(EsHint.FieldHint hint) {
        if (hint.getBoost() == 1.0f) {
            return hint.getField();
        }
        String ret = "%s^%.1f".formatted(hint.getField(), Float.valueOf(hint.getBoost()));
        return ret.endsWith(".0") ? ret.substring(0, ret.length() - 2) : ret;
    }

    protected String getFieldName(String name, EsHint hint) {
        if (hint != null && hint.index != null) {
            return hint.index;
        }
        if ("ecm:isCheckedInVersion".equals(name)) {
            name = "ecm:isVersion";
        }
        name = name.replace("/*", "");
        name = name.replace("/", ".");
        return name;
    }

    protected List<SortOptions> makeSortBuilders(SearchQuery searchQuery) {
        final ArrayList<SortOptions> sortBuilders = new ArrayList<SortOptions>();
        searchQuery.getQuery().accept((IVisitor)new DefaultQueryVisitor(){

            public void visitOrderByExpr(OrderByExpr node) {
                String name = OpenSearchQueryTransformer.this.getFieldName(node.reference.name, null);
                if ("ecm:fulltextScore".equals(name)) {
                    sortBuilders.add((SortOptions)new SortOptions.Builder().score(s -> s.order(SortOrder.Desc)).build());
                } else {
                    sortBuilders.add((SortOptions)new SortOptions.Builder().field(f -> f.field(name).order(node.isDescending ? SortOrder.Desc : SortOrder.Asc).unmappedType(OpenSearchQueryTransformer.this.guessFieldType(name))).build());
                }
            }
        });
        return sortBuilders;
    }

    protected FieldType guessFieldType(String field) {
        String schemaFieldType;
        if (SCORE_FIELD.equals(field)) {
            return null;
        }
        SchemaManager schemaManager = (SchemaManager)Framework.getService(SchemaManager.class);
        Field schemaField = schemaManager.getField(field);
        if (schemaField == null) {
            return FieldType.Keyword;
        }
        return switch (schemaFieldType = schemaField.getType().getName()) {
            case "integer" -> FieldType.Integer;
            case "long" -> FieldType.Long;
            case "boolean" -> FieldType.Boolean;
            case "date" -> FieldType.Date;
            default -> FieldType.Keyword;
        };
    }

    protected String translateFulltextQuery(String query) {
        return query.replace(" OR ", " | ").replace(" or ", " | ");
    }

    protected Highlight makeHighlighter(SearchQuery searchQuery) {
        if (!searchQuery.getHighlights().isEmpty()) {
            Highlight.Builder hb = new Highlight.Builder();
            for (String field : searchQuery.getHighlights()) {
                hb.fields(field, f -> f);
            }
            hb.requireFieldMatch(Boolean.valueOf(false));
            return hb.build();
        }
        return null;
    }

    protected Map<String, Aggregation> makeAggregationBuilders(SearchQuery searchQuery) {
        List<Aggregate<? extends Bucket>> aggregates = searchQuery.getAggregates();
        SearchRequest.Builder request = new SearchRequest.Builder();
        for (Aggregate<? extends Bucket> aggregate : aggregates) {
            String key = AggregateParserBase.getFilterId(aggregate);
            Query filter = this.getAggregateFilterExceptFor(searchQuery, aggregate.getId()).build();
            Aggregation aggregation = this.getSubAggregation((Aggregate<? extends Bucket>)aggregate);
            request.aggregations(key, f -> f.filter(filter).aggregations(aggregate.getId(), aggregation));
        }
        return request.build().aggregations();
    }

    protected Query.Builder getAggregateFilterExceptFor(SearchQuery searchQuery, String id) {
        Query.Builder ret = new Query.Builder();
        List<Query> mustFilter = searchQuery.getAggregates().stream().filter(a -> a.getId() != id).map(this::getAggregateFilter).filter(Objects::nonNull).map(Query.Builder::build).toList();
        if (mustFilter.isEmpty()) {
            ret.matchAll(m -> m);
        } else {
            ret.bool(b -> b.must(mustFilter));
        }
        return ret;
    }

    protected Query.Builder getAggregateFilter(Aggregate<? extends Bucket> aggregate) {
        if (aggregate.getSelection().isEmpty()) {
            return null;
        }
        return switch (aggregate.getType()) {
            case "terms", "count", "cardinality", "sum", "avg", "max", "min" -> AggregateTermParser.getFilter(aggregate);
            case "range" -> AggregateRangeParser.getFilter(aggregate);
            case "histogram" -> AggregateHistogramParser.getFilter(aggregate);
            case "date_range" -> AggregateDateRangeParser.getFilter((AggregateDateRange)aggregate);
            case "date_histogram" -> AggregateDateHistogramParser.getFilter(aggregate);
            default -> throw new IllegalStateException("Unexpected value: " + aggregate.getType());
        };
    }

    protected Aggregation getSubAggregation(Aggregate<? extends Bucket> aggregate) {
        return switch (aggregate.getType()) {
            case "terms" -> AggregateTermParser.getAggregate(aggregate);
            case "range" -> AggregateRangeParser.getAggregate(aggregate);
            case "date_range" -> AggregateDateRangeParser.getAggregate(aggregate);
            case "sum", "count", "cardinality", "avg", "max", "min", "missing" -> AggregateSingleValueMetricParser.getAggregate(aggregate);
            case "histogram" -> AggregateHistogramParser.getAggregate(aggregate);
            case "date_histogram" -> AggregateDateHistogramParser.getAggregate(aggregate);
            case "significant_terms" -> AggregateSignificantTermParser.getAggregate(aggregate);
            default -> throw new IllegalStateException("Unexpected value: " + aggregate.getType());
        };
    }

    protected Query makeAggregatePostFilter(SearchQuery searchQuery) {
        List<Query> mustFilter = searchQuery.getAggregates().stream().map(this::getAggregateFilter).filter(Objects::nonNull).map(Query.Builder::build).toList();
        if (mustFilter.isEmpty()) {
            return null;
        }
        return (Query)new Query.Builder().bool(b -> b.must(mustFilter)).build();
    }

    protected static Time getKeepAlive(SearchQuery query) {
        long keepAlive = query.getScrollKeepAlive().toSeconds();
        if (keepAlive <= 0L || keepAlive > MAX_KEEP_ALIVE_SECONDS) {
            keepAlive = MAX_KEEP_ALIVE_SECONDS;
        }
        return (Time)new Time.Builder().time(keepAlive + "s").build();
    }

    public static class ExpressionBuilder {
        public final String operator;
        public Query.Builder queryBuilder;
        public BoolQuery.Builder boolQueryBuilder;

        public ExpressionBuilder() {
            this(null);
        }

        public ExpressionBuilder(String op) {
            this.operator = op;
            this.queryBuilder = null;
        }

        public void add(QueryAndFilter qf) {
            if (qf != null) {
                this.add(qf.query, qf.filter);
            }
        }

        public void add(Query.Builder q) {
            this.add(q, null);
        }

        public void add(Query.Builder q, Query.Builder f) {
            Query.Builder inputQuery;
            if (q == null && f == null) {
                return;
            }
            Query.Builder builder = inputQuery = q == null ? new Query.Builder() : q;
            if (q == null) {
                inputQuery.constantScore(c -> c.filter(f.build()));
            }
            if (this.operator == null) {
                this.queryBuilder = inputQuery;
            } else {
                if (this.boolQueryBuilder == null) {
                    this.boolQueryBuilder = QueryBuilders.bool();
                }
                switch (this.operator) {
                    case "AND": {
                        this.boolQueryBuilder.must(inputQuery.build(), new Query[0]);
                        break;
                    }
                    case "OR": {
                        this.boolQueryBuilder.should(inputQuery.build(), new Query[0]);
                        break;
                    }
                    case "NOT": {
                        this.boolQueryBuilder.mustNot(inputQuery.build(), new Query[0]);
                    }
                }
            }
        }

        public void merge(ExpressionBuilder expr) {
            if (expr.operator != null && expr.operator.equals(this.operator) && this.boolQueryBuilder == null) {
                this.boolQueryBuilder = expr.boolQueryBuilder;
            } else {
                this.add(new QueryAndFilter(expr.get(), null));
            }
        }

        public Query.Builder get() {
            if (this.queryBuilder != null) {
                return this.queryBuilder;
            }
            Query.Builder ret = new Query.Builder();
            if (this.boolQueryBuilder != null) {
                ret.bool(this.boolQueryBuilder.build());
                return ret;
            }
            ret.matchAll(m -> m);
            return ret;
        }

        public String toString() {
            return "op=%s qb=%s bqb=%s".formatted(this.operator, this.queryBuilder, this.boolQueryBuilder);
        }
    }

    public record QueryAndFilter(Query.Builder query, Query.Builder filter) {
    }
}

