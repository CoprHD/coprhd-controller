package com.emc.sa.service.vipr.block;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */

public class ViewVolumesByProject extends WaitForTasks<VolumeRestRep>{
	private URI volumeId;
	private URI projectId;
	
	public ViewVolumesByProject(String projectId, String volumeId) {
		this(uri(projectId),uri(volumeId));
	}
	
	public ViewVolumesByProject(URI volumeId, URI projectId) {
		this.volumeId = volumeId;
		this.projectId = projectId;
		provideDetailArgs(volumeId,projectId);
	}
	
	@Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
		return null;
	}
}