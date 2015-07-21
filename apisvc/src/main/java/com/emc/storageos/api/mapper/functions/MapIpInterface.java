/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.google.common.base.Function;

public class MapIpInterface implements Function<IpInterface, IpInterfaceRestRep>{

    public static final MapIpInterface instance = new MapIpInterface();

    public static MapIpInterface getInstance() {
        return instance;
    }

    private MapIpInterface() {
    }
    
    @Override
    public IpInterfaceRestRep apply(IpInterface resource) {
        return HostMapper.map(resource);
    }

}
