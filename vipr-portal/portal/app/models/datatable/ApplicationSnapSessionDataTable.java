/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Map;

import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;

import util.datatable.DataTable;

public class ApplicationSnapSessionDataTable extends DataTable {
	public ApplicationSnapSessionDataTable() {
		addColumn("name").setRenderFunction("renderLink");
		addColumn("capacity").setRenderFunction("render.sizeInGb");
        addColumn("varray");
        addColumn("vpool");
        addColumn("subGroup");
        sortAll();
	}
	
	public static class ApplicationSnapshots {
		public URI id;
		public String name;
		public String capacity;
		public String varray;
		public String vpool;
		public String subGroup;
		
		public ApplicationSnapshots(BlockSnapshotSessionRestRep blockSnapSession, Map<URI, String> varrayMap, Map<URI, String> vpoolMap) {
			id = blockSnapSession.getId();
			name = blockSnapSession.getName();
			capacity="2.0GB";
			if (blockSnapSession.getVirtualArray() != null) {
                varray = varrayMap.get(blockSnapSession.getVirtualArray().getId());
            }
			subGroup = blockSnapSession.getReplicationGroupInstance();
			vpool = "testPool";
		}
		
	}
}