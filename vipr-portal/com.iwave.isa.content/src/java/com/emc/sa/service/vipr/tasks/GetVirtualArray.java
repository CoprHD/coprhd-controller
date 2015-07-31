/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import com.emc.storageos.model.varray.VirtualArrayRestRep;

public class GetVirtualArray extends ViPRExecutionTask<VirtualArrayRestRep> {
    private final URI virtualArrayId;

    public GetVirtualArray(URI virtualArrayId) {
        this.virtualArrayId = virtualArrayId;
        provideDetailArgs(virtualArrayId);
    }

    @Override
    public VirtualArrayRestRep executeTask() throws Exception {
        return getClient().varrays().get(virtualArrayId);
    }
}
