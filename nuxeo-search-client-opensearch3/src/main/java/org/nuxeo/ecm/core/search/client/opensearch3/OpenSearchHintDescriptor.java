/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.commons.lang3.ObjectUtils
 *  org.nuxeo.common.xmap.annotation.XNode
 *  org.nuxeo.common.xmap.annotation.XObject
 *  org.nuxeo.ecm.core.api.NuxeoException
 *  org.nuxeo.runtime.model.Descriptor
 */
package org.nuxeo.ecm.core.search.client.opensearch3;

import org.apache.commons.lang3.ObjectUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.search.client.opensearch3.OpenSearchHintQueryBuilder;
import org.nuxeo.runtime.model.Descriptor;

@XObject(value="hint")
public class OpenSearchHintDescriptor
implements Descriptor {
    @XNode(value="@name")
    protected String name;
    @XNode(value="@class")
    protected Class<? extends OpenSearchHintQueryBuilder> klass;
    @XNode(value="@remove")
    public boolean remove;

    public String getId() {
        return this.name;
    }

    public boolean doesRemove() {
        return this.remove;
    }

    public String getName() {
        return this.name;
    }

    public Class<? extends OpenSearchHintQueryBuilder> getKlass() {
        return this.klass;
    }

    public OpenSearchHintQueryBuilder newInstance() {
        try {
            return this.getKlass().getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
        }
        catch (ReflectiveOperationException e) {
            throw new NuxeoException((Throwable)e);
        }
    }

    public OpenSearchHintDescriptor merge(Descriptor o) {
        OpenSearchHintDescriptor other = (OpenSearchHintDescriptor)o;
        OpenSearchHintDescriptor merged = new OpenSearchHintDescriptor();
        merged.name = this.name;
        merged.klass = (Class)ObjectUtils.defaultIfNull(other.klass, this.klass);
        merged.remove = other.remove;
        return merged;
    }
}

