/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.google.common.base.Function;

public class MapVcenterDataCenter implements Function<VcenterDataCenter, VcenterDataCenterRestRep> {
    public static final MapVcenterDataCenter instance = new MapVcenterDataCenter();

    public static MapVcenterDataCenter getInstance() {
        return instance;
    }

    private MapVcenterDataCenter() {
    }

    @Override
    public VcenterDataCenterRestRep apply(VcenterDataCenter resource) {
        return HostMapper.map(resource);
    }

}
