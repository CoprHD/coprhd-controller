/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.model.block.export.ExportPathParametersRestRep;
import com.google.common.base.Function;

public class MapExportPathParams implements Function<ExportPathParams, ExportPathParametersRestRep> {

    public static final MapExportPathParams instance = new MapExportPathParams();
    // The DB client is required to query the FCEndpoint
    private DbClient dbClient;

    public static MapExportPathParams getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    private MapExportPathParams() {
    }

    private void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public ExportPathParametersRestRep apply(ExportPathParams resource) {
        return DbObjectMapper.map(resource);
    }

}
