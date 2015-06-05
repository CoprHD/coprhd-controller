/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import util.datatable.DataTable;

import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.resources.BlockConsistencyGroups;

public class ConsistencyGroupsDataTable extends DataTable {

    public ConsistencyGroupsDataTable() {
        addColumn("name");
        this.setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<ConsistencyGroup> fetch(URI projectId) {
        if (projectId == null) {
            return Collections.EMPTY_LIST;
        }

        ViPRCoreClient client = getViprClient();
        List<BlockConsistencyGroupRestRep> blockConsistencyGroups = client.blockConsistencyGroups().findByProject(projectId);

        List<ConsistencyGroup> results = Lists.newArrayList();
        for (BlockConsistencyGroupRestRep blockConsistencyGroup : blockConsistencyGroups) {
            results.add(new ConsistencyGroup(blockConsistencyGroup));
        }
        return results;
    }

    public static class ConsistencyGroup {
        public URI id;
        public String name;
        public String rowLink;

        public ConsistencyGroup(BlockConsistencyGroupRestRep blockConsistencyGroup) {
            id = blockConsistencyGroup.getId();
            name = blockConsistencyGroup.getName();
            this.rowLink = createLink(BlockConsistencyGroups.class, "consistencyGroupDetails", "consistencyGroupId", id);
        }
    }
}
