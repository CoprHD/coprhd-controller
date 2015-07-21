/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;


public class BlockInsertion extends CassandraInsertion {
    @Override
    public void injectColumnsDetails(Stat statObj, DbClient client) throws Exception {
        Volume volObj = client.queryObject(Volume.class, statObj.getResourceId());
        if (null != volObj) {
            statObj.setProject(volObj.getProject().getURI());
            statObj.setTenant(volObj.getTenant().getURI());
            statObj.setVirtualPool(volObj.getVirtualPool());
        }
    }

    @Override
    public void throwException(Exception e) throws BaseCollectionException {
        _logger.error("Cassandra Database Injection Error", e);
        throw new SMIPluginException("Cassandra Database Injection Error", -1);
    }
}
