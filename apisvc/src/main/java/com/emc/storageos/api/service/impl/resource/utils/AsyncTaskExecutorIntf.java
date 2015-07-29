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
