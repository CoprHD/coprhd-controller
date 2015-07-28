/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;

/**
 * Processor responsible for getting back Storage pools from Providers, and
 * discard pools which are not managed by Bourne.
 * 
 */
public class StoragePoolProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(StoragePoolProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            final Iterator<?> it = (Iterator<?>) resultObj;
            while (it.hasNext()) {
                final CIMObjectPath path = (CIMObjectPath) it.next();
                // Validate whether this storagePool is created by Bourne
                if (validateStoragePool(path, keyMap)) {
                    addPath(keyMap, operation.getResult(), path);
                }
            }
        } catch (Exception ex) {
            if (!(ex instanceof BaseCollectionException)) {
                _logger.error(" Allocated Capacity : ", ex);
            }
        }
        resultObj = null;
    }

    /**
     * Global Cache, will be updated with details of the list of Storage Pools
     * Native IDs,on which the storagePools retrieved from Providers would get
     * validated. List for Storage pools, can be retrieved ,as IndexConstraints
     * are available between StorageDevice-->Pool. But we don't have a way in DB
     * interface to get the list of Pool IDs directly.There is already a need to
     * get the list of Storage Volumes Ids to be retrieved from DB, in addition
     * to that, we can include the requirement to get back Storage Pools Ids as
     * well.
     * 
     * @param path
     * @return boolean
     */
    private boolean validateStoragePool(CIMObjectPath path, Map<String, Object> keyMap) {
        /**
         * To-DO: updating Cache with StoragePool Ids, and making sure the cache
         * is in sync with DB. As no. of StoragePools would not be a huge number
         * as like Volumes, we can try to get the List of Storage Pool IDs
         * managed by Bourne for a Storage Device , and then validate them
         * against the ones got from Provider.This extra Call to DB,eliminates
         * lot no. of SMI-S Calls.
         * 
         */
        // Discard Storage Pools, which are not part of the Array
        // To-Do: replaced by Associators , so that this check can be
        // eliminated.
        String serialID = (String) keyMap.get(Constants._serialID);
        if (path.getKey("InstanceID").getValue().toString().contains(serialID)) {
            return true;
        }
        return false;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
