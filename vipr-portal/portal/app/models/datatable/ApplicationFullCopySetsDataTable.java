/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
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
	
	public static class ApplicationFullCopySets {
		public String cloneGroups;
		public long createdTime;
		public List<String> subGroup = Lists.newArrayList();
		
		public ApplicationFullCopySets(String sets, List<NamedRelatedResourceRep> volumeDetailClone) {
			cloneGroups = sets;
			for(NamedRelatedResourceRep clone : volumeDetailClone) {
    			VolumeRestRep blockVolume = BourneUtil.getViprClient().blockVolumes().get((clone.getId()));
    			createdTime = blockVolume.getCreationTime().getTime().getTime();;
    			subGroup.add(blockVolume.getReplicationGroupInstance());
			}
		}
	}
}