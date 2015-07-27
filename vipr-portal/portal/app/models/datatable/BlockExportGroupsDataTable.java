/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import util.datatable.DataTable;

import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.resources.BlockExportGroups;

public class BlockExportGroupsDataTable extends DataTable {

    public BlockExportGroupsDataTable() {
        addColumn("name");
        addColumn("type");
        addColumn("varray");
        sortAll();
        setDefaultSort("name", "asc");

        setRowCallback("createRowLink");
    }

    public static List<ExportGroup> fetch(URI projectId) {
        if (projectId == null) {
            return Collections.EMPTY_LIST;
        }

        ViPRCoreClient client = getViprClient();
        List<ExportGroupRestRep> exportGroups = client.blockExports().findByProject(projectId);
        Map<URI,String> virtualArrays = ResourceUtils.mapNames(client.varrays().list());

        List<ExportGroup> results = Lists.newArrayList();
        for (ExportGroupRestRep exportGroup : exportGroups) {
            results.add(new ExportGroup(exportGroup, virtualArrays));
        }
        return results;
    }

    public static class ExportGroup {
    	public String rowLink;
        public URI id;
        public String name;
        public String type;
        public String varray;

        public ExportGroup(ExportGroupRestRep exportGroup, Map<URI,String> varrayMap) {
            id = exportGroup.getId();
            name = exportGroup.getName();
            rowLink = createLink(BlockExportGroups.class, "exportGroup", "exportGroupId", id);
            type = exportGroup.getType();
            if (exportGroup.getVirtualArray() != null) {
                varray = varrayMap.get(exportGroup.getVirtualArray().getId());
            }            
        }
    }
}
