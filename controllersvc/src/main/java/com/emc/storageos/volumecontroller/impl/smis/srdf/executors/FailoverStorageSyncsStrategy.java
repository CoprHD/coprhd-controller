/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.executors;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;

import javax.cim.CIMArgument;
import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;
import java.util.Collection;

/**
 * Created by bibbyi1 on 4/8/2015.
 */
public class FailoverStorageSyncsStrategy implements ExecutorStrategy {

    private SmisCommandHelper helper;

    public FailoverStorageSyncsStrategy(SmisCommandHelper helper) {
        this.helper = helper;
    }

    @Override
    public void execute(Collection<CIMObjectPath> objectPaths, StorageSystem provider) throws WBEMException {
        CIMArgument[] args = helper.getFailoverSyncPairInputArguments(objectPaths);
        helper.callModifyListReplica(provider, args);
    }
}
