/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;


import java.net.URI;
import java.util.Map;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;

import util.BourneUtil;
import util.datatable.DataTable;

public class ApplicationSnapSessionDataTable extends DataTable {
	public ApplicationSnapSessionDataTable() {
		addColumn("name").setRenderFunction("renderLink");
        addColumn("varray");
        addColumn("vpool");
        addColumn("subGroup");
        sortAll();
	}
	
	@SuppressWarnings("ClassVariableVisibilityCheck ")
	public static class ApplicationSnapshotSession {
		public URI id;
		public String name;
		public String capacity;
		public String varray;
		public String vpool;
		public String subGroup;
		public VolumeRestRep sourceVolume;
		public Map<URI, String> virtualArrays = ResourceUtils.mapNames(BourneUtil.getViprClient().varrays().list());
        public Map<URI, String> virtualPools = ResourceUtils.mapNames(BourneUtil.getViprClient().blockVpools().list());
		
		public ApplicationSnapshotSession(BlockSnapshotSessionRestRep blockSnapSession) {
			id = blockSnapSession.getId();
			name = blockSnapSession.getName();
			if (blockSnapSession.getVirtualArray() != null) {
                varray = virtualArrays.get(blockSnapSession.getVirtualArray().getId());
            }
			subGroup = blockSnapSession.getReplicationGroupInstance();
			if(blockSnapSession.getParent()!=null) {
				sourceVolume = BourneUtil.getViprClient().blockVolumes().get(blockSnapSession.getParent().getId());
			}
			if(sourceVolume!=null) {
				if(sourceVolume.getVirtualPool()!=null) {
					vpool = virtualPools.get(sourceVolume.getVirtualPool().getId());
				}
			}
		}
	}
}