/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.FileMapper;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.db.client.model.FileShare;
import com.google.common.base.Function;

public class MapFileShare implements Function<FileShare, FileShareRestRep> {
    public static final MapFileShare instance = new MapFileShare();

    public static MapFileShare getInstance() {
        return instance;
    }

    private MapFileShare() {
    }

    @Override
    public FileShareRestRep apply(FileShare share) {
        return FileMapper.map(share);
    }
}
