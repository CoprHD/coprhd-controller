/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;
import java.net.URI;

public abstract class SmisBlockMirrorJob extends SmisReplicaCreationJobs {

    private static final Logger _log = LoggerFactory.getLogger(SmisBlockMirrorJob.class);

    public SmisBlockMirrorJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter, String jobName) {
        super(cimJob, storageSystem, taskCompleter, jobName);
    }

    protected void updateSynchronizationAspects(WBEMClient client, BlockMirror mirror) throws WBEMException {
        CIMObjectPath path = CimObjectPathCreator.createInstance(mirror.getSynchronizedInstance());
        _log.info("Updating synchronization aspects of " + path.toString());
        CIMInstance syncInstance = getStorageSyncInstance(client, path);

        if (syncInstance == null) {
            return;
        }

        mirror.setSynchronizedInstance(syncInstance.getObjectPath().toString());
        mirror.setSyncState(CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_SYNC_STATE));
        mirror.setSyncType(CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_SYNC_TYPE));
    }

    protected CIMInstance getStorageSyncInstance(WBEMClient client, CIMObjectPath syncPath) throws WBEMException {
        return client.getInstance(syncPath, false, false, null);
    }

    protected CIMInstance getStorageSyncInstanceFromVolume(WBEMClient client, CIMObjectPath volumePath) throws WBEMException {
        CloseableIterator<CIMInstance> references = client.referenceInstances(volumePath,
                SmisConstants.CIM_STORAGE_SYNCHRONIZED, null, false, null);
        if (!references.hasNext()) {
            _log.error(String.format("No storage synchronized instance was found for %s", volumePath.toString()));
            return null;
        }
        return references.next();
    }
   
}
