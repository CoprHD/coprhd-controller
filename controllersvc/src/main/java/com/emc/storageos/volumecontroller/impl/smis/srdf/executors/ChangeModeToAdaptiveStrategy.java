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

public class ChangeModeToAdaptiveStrategy implements ExecutorStrategy {
	private SmisCommandHelper helper;
	
	public ChangeModeToAdaptiveStrategy(SmisCommandHelper helper) {
		this.helper = helper;
	}

	@Override
	public void execute(Collection<CIMObjectPath> objectPaths,
			StorageSystem provider) throws Exception {
	    // Provider internally disables consistency when changing mode to Adaptive Copy
	    CIMArgument[] args = helper.getResetToAdaptiveCopyModeInputArguments(objectPaths.iterator().next());
	    helper.callModifyReplica(provider, args);
	}

}
