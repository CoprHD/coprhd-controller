/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.executors;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;
import java.util.Collection;

/**
 * Created by bibbyi1 on 4/9/2015.
 */
public class SuspendWithConsExemptStrategy implements ExecutorStrategy {

    private SmisCommandHelper helper;

    public SuspendWithConsExemptStrategy(SmisCommandHelper helper) {
        this.helper = helper;
    }

    @Override
    public void execute(Collection<CIMObjectPath> objectPaths, StorageSystem provider) throws WBEMException {
        CIMInstance consExemptRepSetting = helper.getReplicationSettingDataInstance(provider);
        CIMArgument[] args = helper.getSRDFPauseGroupInputArguments(objectPaths.iterator().next(),
                consExemptRepSetting, false);
        helper.callModifyReplica(provider, args);
    }
}
