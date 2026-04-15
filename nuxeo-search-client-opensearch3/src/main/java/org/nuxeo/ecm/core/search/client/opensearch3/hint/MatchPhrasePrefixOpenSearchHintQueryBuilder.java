/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.nuxeo.ecm.core.query.sql.model.EsHint
 *  org.nuxeo.runtime.api.Framework
 *  org.nuxeo.runtime.services.config.ConfigurationService
 *  org.opensearch.client.opensearch._types.query_dsl.Query$Builder
 */
package org.nuxeo.ecm.core.search.client.opensearch3.hint;

import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.config.ConfigurationService;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class MatchPhrasePrefixOpenSearchHintQueryBuilder
implements OpenSearchHintQueryBuilder {
    @Override
    public Query.Builder make(EsHint hint, String fieldName, Object value) {
        String valueString = (String)value;
        if (valueString.endsWith("*") && valueString.length() > 2) {
            value = valueString.substring(0, valueString.length() - 1);
        }
        String query = (String)value;
        Query.Builder ret = new Query.Builder();
        ret.matchPhrasePrefix(m -> m.field(fieldName).analyzer(hint.analyzer).query(query).maxExpansions(Integer.valueOf(this.getMaxExpansions())));
        return ret;
    }

    protected int getMaxExpansions() {
        int defaultMax = 50;
        ConfigurationService cs = (ConfigurationService)Framework.getService(ConfigurationService.class);
        if (cs != null) {
            return cs.getInteger("opensearch2.max_expansions", defaultMax);
        }
        return defaultMax;
    }
}

