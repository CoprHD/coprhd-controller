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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.volumecontroller.TaskCompleter;

public class HDSBlockCreateMirrorJob extends HDSCreateVolumeJob {
	
	private static final Logger log = LoggerFactory.getLogger(HDSBlockCreateMirrorJob.class);

	public HDSBlockCreateMirrorJob(String hdsJob, URI storageSystem,
			URI storagePool, TaskCompleter taskCompleter) {
		super(hdsJob, storageSystem, storagePool, taskCompleter, "CreateBlockMirror");
	}
	
}
