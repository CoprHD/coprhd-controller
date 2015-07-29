/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.FileMapper;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;
import com.google.common.base.Function;

public class MapUnmanagedFileSystem implements Function<UnManagedFileSystem, UnManagedFileSystemRestRep> {
    public static final MapUnmanagedFileSystem instance = new MapUnmanagedFileSystem();

    public static MapUnmanagedFileSystem getInstance() {
        return instance;
    }

    private MapUnmanagedFileSystem() {
    }

    @Override
    public UnManagedFileSystemRestRep apply(UnManagedFileSystem filesystem) {
        return FileMapper.map(filesystem);
    }
}
