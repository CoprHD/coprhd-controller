/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;

import com.emc.storageos.volumecontroller.TaskCompleter;

public class HDSBlockMirrorDeleteJob extends HDSDeleteVolumeJob {

	public HDSBlockMirrorDeleteJob(String hdsJob, URI storageSystem,
			TaskCompleter taskCompleter) {
		super(hdsJob, storageSystem, taskCompleter, "DeleteMirror");
	}
	
}
