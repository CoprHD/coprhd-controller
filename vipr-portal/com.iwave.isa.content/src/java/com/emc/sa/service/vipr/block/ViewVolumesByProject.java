package com.emc.sa.service.vipr.block;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.VolumeRestRep;

/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */

public class ViewVolumesByProject extends ViPRExecutionTask<VolumeRestRep>{
	private URI volumeId;
	private URI projectId;
	
	public ViewVolumesByProject(URI volumeId, URI projectId) {
		this.volumeId = volumeId;
		this.projectId = projectId;
		provideDetailArgs(volumeId,projectId);
	}
}