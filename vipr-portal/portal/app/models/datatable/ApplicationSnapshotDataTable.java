/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.model.block.BlockSnapshotRestRep;

import util.datatable.DataTable;
import controllers.resources.BlockApplications;

public class ApplicationSnapshotDataTable extends DataTable {
	public ApplicationSnapshotDataTable() {
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
		
		public ApplicationSnapshots(BlockSnapshotRestRep blockSnapshot, Map<URI, String> varrayMap, Map<URI, String> vpoolMap) {
			id = blockSnapshot.getId();
			name = blockSnapshot.getName();
			if (blockSnapshot.getVirtualArray() != null) {
                varray = varrayMap.get(blockSnapshot.getVirtualArray().getId());
            }
		}
		
	}
}