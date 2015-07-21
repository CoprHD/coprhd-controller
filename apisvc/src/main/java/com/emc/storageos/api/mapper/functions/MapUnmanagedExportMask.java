/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.model.block.UnManagedExportMaskRestRep;
import com.google.common.base.Function;

public class MapUnmanagedExportMask implements Function<UnManagedExportMask,UnManagedExportMaskRestRep> {
    public static final MapUnmanagedExportMask instance = new MapUnmanagedExportMask();

    public static MapUnmanagedExportMask getInstance() {
        return instance;
    }

    private MapUnmanagedExportMask() {
    }

    @Override
    public UnManagedExportMaskRestRep apply(UnManagedExportMask exportMask) {
        return BlockMapper.map(exportMask);
    }
}
