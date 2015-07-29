/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.google.common.base.Function;

public class MapVcenter implements Function<Vcenter, VcenterRestRep> {
    public static final MapVcenter instance = new MapVcenter();

    public static MapVcenter getInstance() {
        return instance;
    }

    private MapVcenter() {
    }

    @Override
    public VcenterRestRep apply(Vcenter resource) {
        return HostMapper.map(resource);
    }
}
