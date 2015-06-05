/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.storageos.model.host.vcenter.VcenterRestRep;

import controllers.compute.VCenters;

public class VCenterDataTable extends DataTable {

    public VCenterDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("hostname");
        addColumn("version").hidden();
        VCenterInfo.addDiscoveryColumns(this);
        sortAll();
    }

    public static class VCenterInfo extends DiscoveredSystemInfo {
        public String id;
        public String rowLink;
        public String name;
        public String hostname;
        public String version;

        public VCenterInfo() {
        }

        public VCenterInfo(VcenterRestRep vcenter) {
            super(vcenter);
            this.id = vcenter.getId().toString();
            this.rowLink = createLink(VCenters.class, "edit", "id", id);
            this.name = vcenter.getName();
            this.hostname = vcenter.getIpAddress();
            this.version = vcenter.getOsVersion();
        }
    }
}
