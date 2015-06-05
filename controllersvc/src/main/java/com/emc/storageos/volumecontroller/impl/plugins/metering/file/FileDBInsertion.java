/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.file;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;


/**
 * 
 * FileDBInsertion to populate the FileShare specific attributes in the Stat
 * object before inserting into db.
 * 
 */
public class FileDBInsertion extends CassandraInsertion {
    /**
     * Logger to log messages.
     */
    private Logger _logger = LoggerFactory.getLogger(FileDBInsertion.class);

    @Override
    public void injectColumnsDetails(final Stat statObj, final DbClient client)
            throws Exception {
        // resourceID query will be done before this, and its within each
        // plugin's processor code.
        final FileShare fileObj = client.queryObject(FileShare.class,
                statObj.getResourceId());
        statObj.setProject(fileObj.getProject().getURI());
        _logger.debug("Project :" + statObj.getProject());
        statObj.setTenant(fileObj.getTenant().getURI());
        _logger.debug("Tenant :" + statObj.getTenant());
        statObj.setVirtualPool(fileObj.getVirtualPool());
        statObj.setServiceType(VNXFileConstants.FILE);
        _logger.debug("VPool :" + statObj.getVirtualPool());
    }

    @Override
    public void throwException(final Exception ex) throws BaseCollectionException {
        throw new VNXFilePluginException(
                "Exception occurred while inserting into cassandra.", ex.getCause());
    }

}
