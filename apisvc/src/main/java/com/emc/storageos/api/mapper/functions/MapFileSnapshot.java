/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.FileMapper;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.google.common.base.Function;

public class MapFileSnapshot implements Function<Snapshot,FileSnapshotRestRep> {
    public static final MapFileSnapshot instance = new MapFileSnapshot();

    public static MapFileSnapshot getInstance() {
        return instance;
    }

    private MapFileSnapshot() {
    }

    @Override
    public FileSnapshotRestRep apply(Snapshot resource) {
        return FileMapper.map(resource);
    }
}
