/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.recoverpoint;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Executor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.recoverpoint.RecoverPointCollectionException;

public class RPExecutor extends Executor {

    @Override
    protected void customizeException(Exception e, Operation operation)
            throws BaseCollectionException {
        throw new RecoverPointCollectionException(e.getMessage());
    }
}
