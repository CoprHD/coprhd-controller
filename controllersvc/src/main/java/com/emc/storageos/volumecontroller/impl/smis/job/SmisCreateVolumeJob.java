/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;
import java.net.URI;

/**
 * A VMAX Volume Create job
 */
public class SmisCreateVolumeJob extends SmisAbstractCreateVolumeJob {

    public SmisCreateVolumeJob(CIMObjectPath cimJob,
                               URI storageSystem,
                               URI storagePool,
                               TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, storagePool, taskCompleter, "CreateSingleVolume");
    }

    public SmisCreateVolumeJob(CIMObjectPath cimJob,
                               URI storageSystem,
                               URI storagePool,
                               TaskCompleter taskCompleter,
                               String name) {
        super(cimJob, storageSystem, storagePool, taskCompleter, name);
    }

    /**
     * This simply updates the deviceLabel name for the single volume that was created.
     *
     * @param dbClient     [in] - Client for reading/writing from/to database.
     * @param client       [in] - WBEMClient for accessing SMI-S provider data
     * @param volume       [in] - Reference to Bourne's Volume object
     * @param volumePath   [in] - Name reference to the SMI-S side volume object
     */
    @Override
    void specificProcessing(DbClient dbClient, WBEMClient client, Volume volume, CIMInstance volumeInstance,
                            CIMObjectPath volumePath) {
        String elementName = CIMPropertyFactory.getPropertyValue(volumeInstance, SmisConstants.CP_ELEMENT_NAME);
        volume.setDeviceLabel(elementName);
    }

}



