/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package models.datatable;

import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.google.common.collect.Lists;

import util.BourneUtil;
import util.datatable.DataTable;

public class ApplicationSnapSetDataTable extends DataTable {
	public ApplicationSnapSetDataTable() {
		addColumn("snapsetGroups").setRenderFunction("renderSnapsets");
		addColumn("createdTime").setRenderFunction("render.localDate");
		addColumn("subGroup");
        sortAll();
	}
	public static class ApplicationSnapSets {
		public String snapsetGroups;
		public long createdTime;
		public String groups;
		public List<String> subGroup = Lists.newArrayList();
		
		public ApplicationSnapSets(String sets, List<NamedRelatedResourceRep> snapshotDetails) {
			snapsetGroups = sets;
			for (NamedRelatedResourceRep snap : snapshotDetails) {
				BlockSnapshotSessionRestRep snapshots = BourneUtil
						.getViprClient().blockSnapshotSessions()
						.get((snap.getId()));
				createdTime = snapshots.getCreationTime().getTime().getTime();
				groups = snapshots.getReplicationGroupInstance();
				if (!subGroup.contains(groups)) {
					subGroup.add(groups);
				}
			}
		}
	}
	
}