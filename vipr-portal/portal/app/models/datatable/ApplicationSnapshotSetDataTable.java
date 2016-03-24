/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.google.common.collect.Lists;

import util.BourneUtil;
import util.datatable.DataTable;

public class ApplicationSnapshotSetDataTable extends DataTable {
	public ApplicationSnapshotSetDataTable() {
		addColumn("snapshotGroups").setRenderFunction("renderSnapshots");
		addColumn("createdTime").setRenderFunction("render.localDate");
		addColumn("subGroup");
        sortAll();
	}
	
	//Suppressing sonar violation for need of accessor methods. Accessor methods are not needed and we use public variables
	@SuppressWarnings("ClassVariableVisibilityCheck")
	public static class ApplicationSnapshotSets {
		public String snapshotGroups;
		public long createdTime;
		public String groups;
		public List<String> subGroup = Lists.newArrayList();
		
		public ApplicationSnapshotSets(String sets, List<NamedRelatedResourceRep> snapshotDetails) {
			snapshotGroups = sets;
			for (NamedRelatedResourceRep snap : snapshotDetails) {
				BlockSnapshotRestRep snapshots = BourneUtil.getViprClient()
						.blockSnapshots().get((snap.getId()));
				createdTime = snapshots.getCreationTime().getTime().getTime();
				groups = snapshots.getReplicationGroupInstance();
				if (!subGroup.contains(groups)) {
					subGroup.add(groups);
				}
			}
		}
	}
}