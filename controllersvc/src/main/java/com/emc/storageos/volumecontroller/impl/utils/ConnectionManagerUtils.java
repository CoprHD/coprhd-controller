/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;

public class ConnectionManagerUtils {
    private Logger log = LoggerFactory.getLogger(ConnectionManagerUtils.class);

    public void disallowReaping(Object profile, Object client) throws BaseCollectionException {
        AccessProfile accessProfile = (AccessProfile) profile;
        DbClient dbClient = (DbClient) client;
        try {
            final CIMConnectionFactory connectionFactory = (CIMConnectionFactory) accessProfile.getCimConnectionFactory();
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
            connectionFactory.setKeepAliveForConnection(storageSystem);
        } catch (final IllegalStateException ex) {
            log.error("Not able to get CIMOM Client instance for ip {} due to ", accessProfile.getIpAddress(), ex);
            throw new SMIPluginException(SMIPluginException.ERRORCODE_NO_WBEMCLIENT, ex.fillInStackTrace(), ex.getMessage());
        }
    }

    public void allowReaping(Object profile, Object client) throws BaseCollectionException {
        AccessProfile accessProfile = (AccessProfile) profile;
        DbClient dbClient = (DbClient) client;
        try {
            final CIMConnectionFactory connectionFactory = (CIMConnectionFactory) accessProfile.getCimConnectionFactory();
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
            connectionFactory.unsetKeepAliveForConnection(storageSystem);
        } catch (final IllegalStateException ex) {
            log.error("Not able to get CIMOM Client instance for ip {} due to ", accessProfile.getIpAddress(), ex);
            throw new SMIPluginException(SMIPluginException.ERRORCODE_NO_WBEMCLIENT, ex.fillInStackTrace(), ex.getMessage());
        }
    }

}
