/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.google.common.collect.Lists;

import util.BourneUtil;
import util.datatable.DataTable;

public class ApplicationFullCopySetsDataTable extends DataTable {
	public ApplicationFullCopySetsDataTable() {
		addColumn("cloneGroups").setRenderFunction("renderClones");
		addColumn("createdTime").setRenderFunction("render.localDate");
		addColumn("subGroup");
        sortAll();
	}
	
	//Suppressing sonar violation for need of accessor methods. Accessor methods are not needed and we use public variables
	@SuppressWarnings("ClassVariableVisibilityCheck")
	public static class ApplicationFullCopySets {
		public String cloneGroups;
		public long createdTime;
		public List<String> subGroup = Lists.newArrayList();
		public RelatedResourceRep associatedSourceVolume;
		public String group;
		
		public ApplicationFullCopySets(String sets,
				List<NamedRelatedResourceRep> volumeDetailClone) {
			cloneGroups = sets;
			for (NamedRelatedResourceRep clone : volumeDetailClone) {
				VolumeRestRep blockVolume = BourneUtil.getViprClient()
						.blockVolumes().get((clone.getId()));
				if (blockVolume.getProtection() != null
						&& blockVolume.getProtection().getFullCopyRep() != null) {
					associatedSourceVolume = blockVolume.getProtection()
							.getFullCopyRep().getAssociatedSourceVolume();
				}
				if (associatedSourceVolume != null) {
					VolumeRestRep associatedVolume = BourneUtil.getViprClient()
							.blockVolumes().get(associatedSourceVolume.getId());
					group = associatedVolume.getReplicationGroupInstance();
					if (!subGroup.contains(group)) {
						subGroup.add(group);
					}
				}
				createdTime = blockVolume.getCreationTime().getTime().getTime();
				;
			}
		}
	}
}
