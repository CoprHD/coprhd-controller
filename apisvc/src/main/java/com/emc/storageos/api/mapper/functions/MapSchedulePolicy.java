/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyRestRep;
import com.google.common.base.Function;

/**
 * MapSchedulePolicy maps schedule value to schedule policy rest representation.
 * 
 * @author prasaa9
 * 
 */
public class MapSchedulePolicy implements Function<SchedulePolicy, SchedulePolicyRestRep> {
    public static final MapSchedulePolicy instance = new MapSchedulePolicy();
    // The DB client is required to query the FCEndpoint
    private DbClient dbClient;

    public static MapSchedulePolicy getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    private MapSchedulePolicy() {
    }

    private void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public SchedulePolicyRestRep apply(SchedulePolicy resource) {
        return DbObjectMapper.map(resource);
    }

}
