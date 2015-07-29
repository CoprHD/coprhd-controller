package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Task;

public class ActivateConsistencyGroupSnapshot extends
		WaitForTask<BlockConsistencyGroupRestRep> {

	private URI consistencyGroup;
	private URI fullCopy;

	public ActivateConsistencyGroupSnapshot(URI consistencyGroup, URI fullCopy) {
		this.consistencyGroup = consistencyGroup;
		this.fullCopy = fullCopy;
	}

	@Override
	protected Task<BlockConsistencyGroupRestRep> doExecute() throws Exception {
		return getClient().blockConsistencyGroups().activateSnapshot(consistencyGroup, fullCopy);
	}
}
