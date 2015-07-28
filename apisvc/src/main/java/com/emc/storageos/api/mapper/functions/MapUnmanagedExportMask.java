/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation All Rights Reserved This software contains the
 * intellectual property of EMC Corporation or is licensed to EMC Corporation from third parties.
 * Use of this software and the intellectual property contained therein is expressly limited to the
 * terms and conditions of the License Agreement under which it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.model.block.UnManagedExportMaskRestRep;
import com.google.common.base.Function;

public class MapUnmanagedExportMask implements Function<UnManagedExportMask, UnManagedExportMaskRestRep> {
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
