/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.CustomConfig;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.db.client.model.PropertyListDataObject;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Token;
import com.emc.storageos.db.client.model.VdcVersion;
import com.emc.storageos.db.client.model.VirtualDataCenter;

public class DbUtil {
    
    private static final Logger log = LoggerFactory.getLogger(DbUtil.class);
    
    private final List<Class<? extends DataObject>> excludeClasses = Arrays.asList(
            Token.class, StorageOSUserDAO.class, VirtualDataCenter.class,
            PropertyListDataObject.class, PasswordHistory.class, CustomConfig.class, VdcVersion.class);
    
    private DbClient dbClient;

    private DbUtil() {
        
    }
    
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    
    /**
     * Check if there are useful data in the DB
     * we don't check the data whose type is in excludeClasses
     * 
     * @return true if there are data no matter inactive or not
     */
    public boolean hasDataInDb() {
        Collection<DataObjectType> doTypes = TypeMap.getAllDoTypes();

        for (DataObjectType doType : doTypes) {
            Class clazz = doType.getDataObjectClass();

            if (hasDataInCF(clazz)) {
                return true;
            }
        }

        return false;
    }

    private <T extends DataObject> boolean hasDataInCF(Class<T> clazz) {
        if (excludeClasses.contains(clazz)) {
            return false; // ignore the data in those CFs
        }

        // true: query only active object ids, for below reason:
        // add VDC should succeed just when remove the data in vdc2.
        List<URI> ids = dbClient.queryByType(clazz, true, null, 2);

        if (clazz.equals(TenantOrg.class)) {
            if (ids.size() > 1) {
                // at least one non-root tenant exist
                return true;
            }

            return false;
        }

        if (!ids.isEmpty()) {
            log.info("The class {} has data e.g. id={}", clazz.getSimpleName(), ids.get(0));
            return true;
        }

        return false;
    }
}
