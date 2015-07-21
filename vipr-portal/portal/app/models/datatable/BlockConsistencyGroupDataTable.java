/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;

/**
 */
public class BlockConsistencyGroupDataTable extends DataTable {

    public BlockConsistencyGroupDataTable() {
        addColumn("name");
        this.setDefaultSort("name", "asc");
    }

    public static class BlockConsistencyGroup {
        public String id;
        public String name;

        public BlockConsistencyGroup() {
            //NA
        }

        public BlockConsistencyGroup(BlockConsistencyGroupRestRep blockConsistencyGroup) {
            this.id = blockConsistencyGroup.getId().toString();
            this.name = blockConsistencyGroup.getName();
        }
    }

}
