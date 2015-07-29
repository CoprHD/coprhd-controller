package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupSnapshotCreate;
import com.emc.vipr.client.Tasks;

public class CreateConsistencyGroupSnapshot extends
		WaitForTasks<BlockConsistencyGroupRestRep> {

	private URI consistencyGroupId;
	private String name;

	public CreateConsistencyGroupSnapshot(URI consistencyGroupId, String name) {
		this.consistencyGroupId = consistencyGroupId;
		this.name = name;
	}

	@Override
	protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {

		BlockConsistencyGroupSnapshotCreate param = new BlockConsistencyGroupSnapshotCreate();
		param.setName(name);

		return getClient().blockConsistencyGroups().createSnapshot(
				consistencyGroupId, param);
	}
}
