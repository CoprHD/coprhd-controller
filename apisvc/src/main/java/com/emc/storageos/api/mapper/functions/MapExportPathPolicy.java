/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.model.block.export.ExportPathPolicyRestRep;
import com.google.common.base.Function;

public class MapExportPathPolicy implements Function<ExportPathParams, ExportPathPolicyRestRep> {

    public static final MapExportPathPolicy instance = new MapExportPathPolicy();
    // The DB client is required to query the FCEndpoint
    private DbClient dbClient;

    public static MapExportPathPolicy getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    private MapExportPathPolicy() {
    }

    private void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public ExportPathPolicyRestRep apply(ExportPathParams resource) {
        return DbObjectMapper.map(resource);
    }

}
