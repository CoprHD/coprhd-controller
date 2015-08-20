/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import util.BourneUtil;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.core.util.RelatedResourceComparator;
import com.google.common.collect.Sets;

/**
 * Fetches connected file virtual pools for the given virtual arrays.
 */
public class ConnectedFileVirtualPoolsCall extends ViPRListCall<FileVirtualPoolRestRep> {
    private Collection<URI> varrayIds;

    public ConnectedFileVirtualPoolsCall(Collection<URI> varrayIds) {
        this(BourneUtil.getViprClient(), varrayIds);
    }

    public ConnectedFileVirtualPoolsCall(ViPRCoreClient client, Collection<URI> varrayIds) {
        this(client, new CachedResources<FileVirtualPoolRestRep>(client.fileVpools()), varrayIds);
    }

    public ConnectedFileVirtualPoolsCall(ViPRCoreClient client, CachedResources<FileVirtualPoolRestRep> cache,
            Collection<URI> varrayIds) {
        super(client, cache);
        this.varrayIds = varrayIds;
    }

    @Override
    public List<FileVirtualPoolRestRep> call() {
        Set<NamedRelatedResourceRep> refs = Sets.newTreeSet(new RelatedResourceComparator());
        for (URI varrayId : varrayIds) {
            refs.addAll(client.fileVpools().listByVirtualArray(varrayId));
        }
        return getByRefs(refs);
    }
}
