/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;


/**
 * Interface to be use to execute Async tasks.
 */
public interface AsyncTaskExecutorIntf {

    public void executeTasks(AsyncTask[] tasks) throws ControllerException;

    public ResourceOperationTypeEnum getOperation();

}
