/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.VirtualMachineMapper;
import com.emc.storageos.db.client.model.VirtualMachine;
import com.emc.storageos.model.host.VirtualMachineRestRep;
import com.google.common.base.Function;

public class MapVirtualMachine implements Function<VirtualMachine, VirtualMachineRestRep> {
    public static final MapVirtualMachine instance = new MapVirtualMachine();

    public static MapVirtualMachine getInstance() {
        return instance;
    }

    private MapVirtualMachine() {
    }

    @Override
    public VirtualMachineRestRep apply(VirtualMachine resource) {
        return VirtualMachineMapper.map(resource);
    }
}
