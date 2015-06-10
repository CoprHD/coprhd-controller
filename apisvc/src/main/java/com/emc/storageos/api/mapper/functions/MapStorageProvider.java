/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.SystemsMapper;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.db.client.model.StorageProvider;
import com.google.common.base.Function;

public class MapStorageProvider implements Function<StorageProvider,StorageProviderRestRep> {
    public static final MapStorageProvider instance = new MapStorageProvider();

    public static MapStorageProvider getInstance() {
        return instance;
    }

    private MapStorageProvider() {
    }

    @Override
    public StorageProviderRestRep apply(StorageProvider resource) {
        return SystemsMapper.map(resource);
    }
}
