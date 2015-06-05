/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.FileMapper;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.model.file.QuotaDirectoryRestRep;
import com.google.common.base.Function;

public class MapQuotaDirectory implements Function<QuotaDirectory,QuotaDirectoryRestRep> {
    public static final MapQuotaDirectory instance = new MapQuotaDirectory();

    public static MapQuotaDirectory getInstance() {
        return instance;
    }

    private MapQuotaDirectory() {
    }

    @Override
    public QuotaDirectoryRestRep apply(QuotaDirectory qd) {
        return FileMapper.map(qd);
    }
}
