/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.CachedResources;

/**
 * Base class for ViPR calls that return lists of DataObjectRestRep.
 * 
 * @param <T>
 *            the item return type.
 */
public abstract class ViPRListCall<T extends DataObjectRestRep> extends ViPRCall<List<T>> {
    protected CachedResources<T> cache;

    public ViPRListCall(ViPRCoreClient client, CachedResources<T> cache) {
        super(client);
        this.cache = cache;
    }

    protected T get(RelatedResourceRep ref) {
        return cache.get(ref);
    }

    protected T get(URI id) {
        return cache.get(id);
    }

    protected List<T> getByRefs(Collection<? extends RelatedResourceRep> refs) {
        return cache.getByRefs(refs);
    }

    protected List<T> getByIds(Collection<URI> ids) {
        return cache.getByIds(ids);
    }
}
