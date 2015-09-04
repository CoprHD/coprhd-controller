/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.executors;

import java.util.Collection;

import javax.cim.CIMArgument;
import javax.cim.CIMObjectPath;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;

public class ChangeModeToSyncStrategy implements ExecutorStrategy {

    private SmisCommandHelper helper;

    public ChangeModeToSyncStrategy(SmisCommandHelper helper) {
        this.helper = helper;
    }

    @Override
    public void execute(Collection<CIMObjectPath> objectPaths,
            StorageSystem provider) throws Exception {
        CIMArgument[] args = helper.getResetToSyncCopyModeInputArguments(objectPaths.iterator().next());
        helper.callModifyReplica(provider, args);

        // activate consistency when we change mode to Sync
        CIMArgument[] args1 = helper.getActivateConsistencyInputArguments(objectPaths.iterator().next());
        helper.callModifyReplica(provider, args1);
    }

}
