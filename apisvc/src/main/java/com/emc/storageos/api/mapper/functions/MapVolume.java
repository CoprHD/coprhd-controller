/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.VolumeRestRep;
import com.google.common.base.Function;

public class MapVolume implements Function<Volume,VolumeRestRep> {
    public static final MapVolume instance = new MapVolume();
    
    public static MapVolume getInstance() {
        return instance;
    }

    private MapVolume() {
    }

    @Override
    public VolumeRestRep apply(Volume volume) {
    	// Via this mechanism, the volume rest rep will not contain target varrays or other "deep dive" objects within the volume
        return BlockMapper.map(volume);
    }
}
