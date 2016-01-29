/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.List;

import util.AppSupportUtil;
import util.MobilityGroupSupportUtil;
import util.datatable.DataTable;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.google.common.collect.Lists;

public class MobilityGroupSupportDataTable extends DataTable {

    public MobilityGroupSupportDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("migrationGroupBy");
        addColumn("description");
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<MobilityGroupSupport> fetch() {
        List<MobilityGroupSupport> results = Lists.newArrayList();
        for (NamedRelatedResourceRep mobilityGroup : MobilityGroupSupportUtil.getMobilityGroups()) {
            results.add(new MobilityGroupSupport(mobilityGroup));
        }
        return results;
    }

    public static class MobilityGroupSupport {
        public String id;
        public String name;
        public String migrationGroupBy;
        public String description;

        public MobilityGroupSupport(NamedRelatedResourceRep mobilityGroup) {
            id = mobilityGroup.getId().toString();
            name = mobilityGroup.getName();

            VolumeGroupRestRep volumeGroup = AppSupportUtil.getApplication(id);
            migrationGroupBy = volumeGroup.getMigrationGroupBy();
            description = volumeGroup.getDescription();
        }
    }
}
