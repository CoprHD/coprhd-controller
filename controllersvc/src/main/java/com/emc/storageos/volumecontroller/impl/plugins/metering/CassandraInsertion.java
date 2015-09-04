/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.BaseCollectionException;

/**
 * Plugins can extend this Class, and use it for Cassandra Injection. But,
 * getting Project, COS details, is very specific to plugin, (for VNXFiles, its
 * via getFileShareNativeIdConstraint and for Block its different) hence plugins
 * have to implement this Method "injectColumnsDetails". TimeInMillis field
 * have to be taken care by each Plugin.
 */
public abstract class CassandraInsertion {

    protected Logger _logger = LoggerFactory.getLogger(CassandraInsertion.class);

    /**
     * Retrieving Project ,COS ,Details are very specific to each Plugin. For
     * Block,we need to get the Volume Instance to retrieve Project Details. But
     * for File ,we need FileShare Instance. hence, each Plugin needs to
     * implement logic to attach Project Details.
     * 
     * @return
     * @throws Exception
     */
    protected abstract void injectColumnsDetails(Stat statObj, DbClient client) throws Exception;

    /**
     * Each plugin responsible for throwing customized Exception
     * 
     * @param e
     * @throws BaseCollectionException
     */
    protected abstract void throwException(Exception e) throws BaseCollectionException;

    /**
     * Inject Stat columns.
     * 
     * @param statObj
     * @param client
     * @throws BaseCollectionException
     */
    public <T> void injectColumns(Stat statObj, DbClient client) {
        try {
            injectColumnsDetails(statObj, client);
        } catch (Exception e) {
            // Even if one volume fails, no need to throw exception instead
            // continue processing other volumes
            _logger.error("Cassandra Database Error while querying Resource ID, VirtualPool & Project URIs", e);
        }
    }
}
