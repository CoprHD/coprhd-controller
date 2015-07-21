/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.google.common.base.Function;

public class MapInitiator implements Function<Initiator, InitiatorRestRep> {
	
    public static final MapInitiator instance = new MapInitiator();

    public static MapInitiator getInstance() {
        return instance;
    }

    private MapInitiator() {
    }

    @Override
    public InitiatorRestRep apply(Initiator resource) {
        return HostMapper.map(resource);
    }

}
