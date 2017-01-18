/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.FilePolicyMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.google.common.base.Function;

public class MapFilePolicy implements Function<FilePolicy, FilePolicyRestRep> {

    public static final MapFilePolicy instance = new MapFilePolicy();

    // The DB client is required to query the FCEndpoint
    private DbClient dbClient;

    public static MapFilePolicy getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    private MapFilePolicy() {
    }

    @Override
    public FilePolicyRestRep apply(FilePolicy resource) {
        return FilePolicyMapper.map(resource, dbClient);
    }

    /**
     * Translate <code>FilePolicy</code> object to <code>FilePolicyRestRep</code>
     * 
     * @param vNas
     * @return
     */
    public FilePolicyRestRep toFilePolicyRestRep(FilePolicy filePolicy) {
        return apply(filePolicy);
    }

}
