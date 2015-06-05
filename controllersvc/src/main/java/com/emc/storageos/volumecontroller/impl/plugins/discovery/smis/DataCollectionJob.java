/**
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
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.Serializable;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * Jobs common for Scanner and Discovery and even Metering.
 */
public abstract class DataCollectionJob implements Serializable {


    private static final long serialVersionUID = 3080188098065596509L;

    public static enum JobOrigin {
        USER_API,
        SCHEDULER;
    }

    private JobOrigin _origin;

    public DataCollectionJob(JobOrigin origin) {
        _origin = origin;
    }

    public abstract DataCollectionTaskCompleter getCompleter() throws ControllerException;

    public abstract void ready(DbClient dbClient) throws DeviceControllerException;

    public abstract void error(DbClient dbClient, ServiceCoded serviceCoded) throws DeviceControllerException;

    public abstract void schedule(DbClient dbClient);

    public abstract void setTaskError(DbClient dbClient,ServiceCoded code);

    public abstract void setTaskReady(DbClient dbClient,String message);

    public abstract void updateTask(DbClient dbClient, String message);

    public abstract boolean isActiveJob(DbClient dbClient);

   public boolean isSchedulerJob() {
        return (_origin == JobOrigin.SCHEDULER);
    }

    public abstract String getType();

    public abstract String systemString();

    public String getNamespace() {
        return Discovery_Namespaces.ALL.toString();
    }
}
