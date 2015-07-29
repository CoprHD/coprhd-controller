package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Tasks;

public class DeactivateConsistencyGroupFullCopy extends
		WaitForTasks<BlockConsistencyGroupRestRep> {

	private URI consistencyGroup;
	private URI fullCopy;

	public DeactivateConsistencyGroupFullCopy(URI consistencyGroup, URI fullCopy) {
		this.consistencyGroup = consistencyGroup;
		this.fullCopy = fullCopy;
	}

	@Override
	protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {
		return getClient().blockConsistencyGroups().deactivateFullCopy(consistencyGroup, fullCopy);
	}
}
