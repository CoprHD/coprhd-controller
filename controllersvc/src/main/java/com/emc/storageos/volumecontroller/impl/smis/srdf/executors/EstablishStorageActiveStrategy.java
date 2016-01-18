/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.executors;

import java.util.Collection;

import javax.cim.CIMArgument;
import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;

public class EstablishStorageActiveStrategy implements ExecutorStrategy {

    private SmisCommandHelper helper;

    public EstablishStorageActiveStrategy(SmisCommandHelper helper) {
        this.helper = helper;
    }

    @Override
    public void execute(Collection<CIMObjectPath> objectPaths, StorageSystem provider) throws WBEMException {
        CIMArgument[] args = helper.getResumeActiveListInputArguments(objectPaths);
        helper.callModifyListReplica(provider, args);
    }
}
