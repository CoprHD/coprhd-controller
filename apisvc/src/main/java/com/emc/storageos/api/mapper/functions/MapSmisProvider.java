/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.SystemsMapper;
import com.emc.storageos.model.smis.SMISProviderRestRep;
import com.emc.storageos.db.client.model.StorageProvider;
import com.google.common.base.Function;

@Deprecated
public class MapSmisProvider implements Function<StorageProvider, SMISProviderRestRep> {
    public static final MapSmisProvider instance = new MapSmisProvider();

    public static MapSmisProvider getInstance() {
        return instance;
    }

    private MapSmisProvider() {
    }

    @Override
    public SMISProviderRestRep apply(StorageProvider resource) {
        return SystemsMapper.mapStorageProviderToSMISRep(resource);
    }
}
