/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import util.BourneUtil;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.core.util.RelatedResourceComparator;
import com.google.common.collect.Sets;

/**
 * Fetches connected block virtual pools for the given virtual arrays.
 */
public class ConnectedBlockVirtualPoolsCall extends ViPRListCall<BlockVirtualPoolRestRep> {
    private Collection<URI> varrayIds;

    public ConnectedBlockVirtualPoolsCall(Collection<URI> varrayIds) {
        this(BourneUtil.getViprClient(), varrayIds);
    }

    public ConnectedBlockVirtualPoolsCall(ViPRCoreClient client, Collection<URI> varrayIds) {
        this(client, new CachedResources<BlockVirtualPoolRestRep>(client.blockVpools()), varrayIds);
    }

    public ConnectedBlockVirtualPoolsCall(ViPRCoreClient client, CachedResources<BlockVirtualPoolRestRep> cache,
            Collection<URI> varrayIds) {
        super(client, cache);
        this.varrayIds = varrayIds;
    }

    @Override
    public List<BlockVirtualPoolRestRep> call() {
        Set<NamedRelatedResourceRep> refs = Sets.newTreeSet(new RelatedResourceComparator());
        for (URI varrayId : varrayIds) {
            refs.addAll(client.blockVpools().listByVirtualArray(varrayId));
        }
        return getByRefs(refs);
    }
}
