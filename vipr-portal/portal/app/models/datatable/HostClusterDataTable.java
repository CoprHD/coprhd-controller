/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;
import util.datatable.DataTable;

import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;

public class HostClusterDataTable extends DataTable {

    public HostClusterDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("discovered").setRenderFunction("render.boolean");
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class HostClusterInfo {
        public String id;
        public String name;
        public boolean discovered;

        public HostClusterInfo() {
        }

        public HostClusterInfo(ClusterRestRep cluster) {
            this.id = cluster.getId().toString();
            this.name = getClusterName(cluster);
            this.discovered = cluster.getVcenterDataCenter() != null;
        }

        private String getClusterName(ClusterRestRep cluster) {
            if (cluster.getVcenterDataCenter() != null) {
                VcenterDataCenterRestRep datacenter = getViprClient().vcenterDataCenters().get(cluster.getVcenterDataCenter());
                return String.format(HostDataTable.HostInfo.ESX_CLUSTER_LABEL_FORMAT, datacenter.getName(), cluster.getName());
            }
            return cluster.getName();
        }
    }

}
