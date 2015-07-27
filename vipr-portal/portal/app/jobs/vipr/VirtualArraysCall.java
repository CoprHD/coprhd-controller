/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import java.util.List;

import util.BourneUtil;

import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.CachedResources;

public class VirtualArraysCall extends ViPRListCall<VirtualArrayRestRep> {
    public VirtualArraysCall() {
        this(BourneUtil.getViprClient());
    }

    public VirtualArraysCall(ViPRCoreClient client) {
        this(client, new CachedResources<VirtualArrayRestRep>(client.varrays()));
    }

    public VirtualArraysCall(ViPRCoreClient client, CachedResources<VirtualArrayRestRep> cache) {
        super(client, cache);
    }

    @Override
    public List<VirtualArrayRestRep> call() {
        return getByRefs(client.varrays().list());
    }
}
