/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
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

}
