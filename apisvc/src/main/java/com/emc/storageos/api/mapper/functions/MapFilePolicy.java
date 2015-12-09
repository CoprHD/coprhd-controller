/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.FileMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.model.file.FilePolicyRestRep;
import com.google.common.base.Function;

/**
 * MapFilePolicy maps file policy to file policy rest representation.
 * 
 * @author prasaa9
 * 
 */
public class MapFilePolicy implements Function<FilePolicy, FilePolicyRestRep> {
    public static final MapFilePolicy instance = new MapFilePolicy();
    // The DB client is required to query the FCEndpoint
    private DbClient dbClient;

    public static MapFilePolicy getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    private MapFilePolicy() {
    }

    private void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public FilePolicyRestRep apply(FilePolicy resource) {
        return FileMapper.map(resource);
    }

}
