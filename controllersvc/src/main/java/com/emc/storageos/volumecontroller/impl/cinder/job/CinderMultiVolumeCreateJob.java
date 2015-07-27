/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.cinder.job;

import java.net.URI;
import java.util.Map;

import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class CinderMultiVolumeCreateJob extends AbstractCinderVolumeCreateJob 
{

	private static final long serialVersionUID = -7640258258972261294L;

	/**
	 * @param jobId
	 * @param jobName
	 * @param storageSystem
	 * @param componentType
	 * @param ep
	 * @param taskCompleter
	 * @param storagePoolUri
	 */
	public CinderMultiVolumeCreateJob(String jobId, String jobName,
													  URI storageSystem, String componentType, 
													  CinderEndPointInfo ep, TaskCompleter taskCompleter,
													  URI storagePoolUri, Map<String, URI> volumeIds) 
	{
		super(jobId, String.format("Create %d Volumes", volumeIds.size()), 
				 storageSystem, componentType, ep, taskCompleter, storagePoolUri, volumeIds);
	}

}
