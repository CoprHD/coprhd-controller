/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * This computer code is copyright 2014 EMC Corporation. All rights reserved.
 */
package com.emc.storageos.geo.vdccontroller.impl;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.impl.GlobalLockImpl;
import com.emc.storageos.db.client.model.GlobalLock;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.security.geo.GeoServiceClient;

/**
 * @author cgarber
 *
 */
public class VdcOperationLockHelper {
    
    private final static Logger log = LoggerFactory.getLogger(VdcOperationLockHelper.class);
    
    private final static String GLOBAL_LOCK = GeoServiceClient.VDCOP_LOCK_NAME;
    private final static long GLOBAL_LOCK_TIMEOUT = GeoServiceClient.VDCOP_LOCK_TIMEOUT;
    
    private InternalDbClient dbClient;

    private Service serviceInfo;
    
    public VdcOperationLockHelper() {
    }

    public void setDbClient(InternalDbClient _dbClient) {
        this.dbClient = _dbClient;
    }
    
    public void setService(Service serviceInfo) {
        this.serviceInfo = serviceInfo;
    }
    
    public void acquire(String vdcShortId) {
        boolean lockAcquired=false;
        String lockErrMsg = null;
        GlobalLockImpl glock = null;
        try {
            log.info("try to acquire lock for vdc operation {}", vdcShortId);
            
            glock = new GlobalLockImpl(dbClient, GLOBAL_LOCK,
                    GlobalLock.GL_Mode.GL_NodeSvcShared_MODE, GLOBAL_LOCK_TIMEOUT,
                    VdcUtil.getLocalShortVdcId());

            if (glock.acquire(serviceInfo.getId())) {
                lockAcquired = true;
            } else {
                lockErrMsg = glock.getErrorMessage();
                if(StringUtils.isEmpty(lockErrMsg)) {
                    lockErrMsg = "Could not acquire global lock for vdc operation";
                }
            }
            
        } catch (Exception e) {
            if(glock == null || StringUtils.isEmpty(glock.getErrorMessage())) {
                lockErrMsg = "Could not acquire global lock for vdc operation";
            }
            else {
                lockErrMsg = glock.getErrorMessage();
            }
            log.error(e.getMessage(), e);
        }
        if (!lockAcquired) {
            throw GeoException.fatals.acquireLockFail(vdcShortId, lockErrMsg);
        }
    }
    
    public void release(String vdcShortId) {
        try {
            log.info("try to release lock for vdc operation {}", vdcShortId);
            
            GlobalLockImpl glock = new GlobalLockImpl(dbClient, GLOBAL_LOCK,
                    GlobalLock.GL_Mode.GL_NodeSvcShared_MODE, GLOBAL_LOCK_TIMEOUT,
                    VdcUtil.getLocalShortVdcId());
            glock.release(serviceInfo.getId(), true);
        } catch (Exception e) {
            // don't fail if we can't release the lock; it'll eventually time out
            log.error(e.getMessage(), e);
        }
        
    }

}
