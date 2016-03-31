/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;


import java.net.URI;
import java.util.Map;

import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;

import util.BourneUtil;
import util.datatable.DataTable;

public class ApplicationSnapshotDataTable extends DataTable {
	public ApplicationSnapshotDataTable() {
		addColumn("name").setRenderFunction("renderLink");
		addColumn("capacity").setRenderFunction("render.sizeInGb");
        addColumn("varray");
        addColumn("vpool");
        addColumn("subGroup");
        sortAll();
	}
	
	//Suppressing sonar violation for need of accessor methods. Accessor methods are not needed and we use public variables
	@SuppressWarnings("ClassVariableVisibilityCheck")
	public static class ApplicationSnapshots {
		public URI id;
		public String name;
		public String capacity;
		public String varray;
		public String vpool;
		public String subGroup;
		public VolumeRestRep sourceVolume;
		public Map<URI, String> virtualArrays = ResourceUtils.mapNames(BourneUtil.getViprClient().varrays().list());
        public Map<URI, String> virtualPools = ResourceUtils.mapNames(BourneUtil.getViprClient().blockVpools().list());
		
		public ApplicationSnapshots(BlockSnapshotRestRep blockSnapshot) {
			id = blockSnapshot.getId();
			name = blockSnapshot.getName();
			capacity = blockSnapshot.getProvisionedCapacity();
			if (blockSnapshot.getVirtualArray() != null) {
                varray = virtualArrays.get(blockSnapshot.getVirtualArray().getId());
            }

			if(blockSnapshot.getParent()!=null) {
				sourceVolume = BourneUtil.getViprClient().blockVolumes().get(blockSnapshot.getParent().getId());
			}
			if(sourceVolume!=null) {
				subGroup = sourceVolume.getReplicationGroupInstance();
				if(sourceVolume.getVirtualPool()!=null) {
					vpool = virtualPools.get(sourceVolume.getVirtualPool().getId());
				}
			}  else {
				subGroup = blockSnapshot.getReplicationGroupInstance();
			}
		}
		
	}
}
