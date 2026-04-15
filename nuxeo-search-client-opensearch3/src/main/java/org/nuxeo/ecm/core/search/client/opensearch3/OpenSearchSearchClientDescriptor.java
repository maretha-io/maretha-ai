/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.commons.lang3.BooleanUtils
 *  org.apache.commons.lang3.ObjectUtils
 *  org.apache.commons.lang3.StringUtils
 *  org.nuxeo.common.xmap.annotation.XNode
 *  org.nuxeo.common.xmap.annotation.XNodeMap
 *  org.nuxeo.common.xmap.annotation.XObject
 *  org.nuxeo.runtime.model.Descriptor
 */
package org.nuxeo.ecm.core.search.client.opensearch3;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

@XObject(value="searchClient")
public class OpenSearchSearchClientDescriptor
implements Descriptor {
    @XNode(value="@name")
    protected String name;
    @XNode(value="@enabled")
    protected Boolean enabled;
    @XNode(value="@clientId")
    protected String clientId;
    @XNodeMap(value="searchIndex", key="@name", type=HashMap.class, componentType=SearchIndex.class)
    protected Map<String, SearchIndex> searchIndexes;

    public String getId() {
        return this.name;
    }

    public String getName() {
        return this.name;
    }

    public boolean isEnabled() {
        return BooleanUtils.toBooleanDefaultIfNull((Boolean)this.enabled, (boolean)true);
    }

    public Map<String, SearchIndex> getSearchIndexes() {
        return this.searchIndexes;
    }

    public String getClientId() {
        return (String)ObjectUtils.defaultIfNull((Object)this.clientId, (Object)("search/" + this.name));
    }

    public Descriptor merge(Descriptor o) {
        OpenSearchSearchClientDescriptor other = (OpenSearchSearchClientDescriptor)o;
        OpenSearchSearchClientDescriptor merged = new OpenSearchSearchClientDescriptor();
        merged.name = this.name;
        merged.enabled = (Boolean)ObjectUtils.defaultIfNull((Object)other.enabled, (Object)this.enabled);
        merged.clientId = (String)StringUtils.defaultIfBlank((CharSequence)other.clientId, (CharSequence)this.clientId);
        merged.searchIndexes = new HashMap<String, SearchIndex>(this.searchIndexes);
        other.searchIndexes.forEach((k, v) -> merged.searchIndexes.merge((String)k, (SearchIndex)v, SearchIndex::merge));
        return merged;
    }

    @XObject(value="searchIndex")
    public static final class SearchIndex
    implements Descriptor {
        @XNode(value="@name")
        protected String name;
        @XNode(value="@technicalName")
        protected String technicalName;

        public String getId() {
            return this.name;
        }

        public String getTechnicalName() {
            return this.technicalName;
        }

        public SearchIndex merge(Descriptor other) {
            return (SearchIndex)other;
        }
    }
}

