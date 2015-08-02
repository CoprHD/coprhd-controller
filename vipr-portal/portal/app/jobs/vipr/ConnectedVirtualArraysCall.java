/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import static com.emc.vipr.client.core.util.ResourceUtils.id;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import util.BourneUtil;

import com.emc.storageos.model.varray.VirtualArrayConnectivityRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.CachedResources;
import com.google.common.collect.Sets;

public class ConnectedVirtualArraysCall extends ViPRListCall<VirtualArrayRestRep> {
    private Collection<URI> varrayIds;
    private String connectionType;

    public ConnectedVirtualArraysCall(Collection<URI> varrayIds, String connectionType) {
        this(BourneUtil.getViprClient(), varrayIds, connectionType);
    }

    public ConnectedVirtualArraysCall(ViPRCoreClient client, Collection<URI> varrayIds, String connectionType) {
        this(client, new CachedResources<VirtualArrayRestRep>(client.varrays()), varrayIds, connectionType);
    }

    public ConnectedVirtualArraysCall(ViPRCoreClient client, CachedResources<VirtualArrayRestRep> cache,
            Collection<URI> varrayIds, String connectionType) {
        super(client, cache);
        this.varrayIds = varrayIds;
        this.connectionType = connectionType;
    }

    @Override
    public List<VirtualArrayRestRep> call() {
        Set<URI> connectedVirtualArrays = Sets.newHashSet();
        for (URI varrayId : varrayIds) {
            List<VirtualArrayConnectivityRestRep> connectivities = client.varrays().getConnectivity(varrayId);
            for (VirtualArrayConnectivityRestRep connectivity : connectivities) {
                if (connectivity.getConnectionType().contains(connectionType)) {
                    connectedVirtualArrays.add(id(connectivity.getVirtualArray()));
                }
            }
        }
        return getByIds(connectedVirtualArrays);
    }
}
