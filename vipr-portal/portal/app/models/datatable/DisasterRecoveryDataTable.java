/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.storageos.model.dr.SiteRestRep;

public class DisasterRecoveryDataTable extends DataTable {

    public DisasterRecoveryDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("description");
        addColumn("VirtualIP");
        addColumn("status").setRenderFunction("standbyStatusIcon");
        addColumn("networkHealth").setRenderFunction("networkHealthIcon");
        addColumn("actions").setRenderFunction("renderButtonBar");
        sortAllExcept("actions");
    }

    public static class StandByInfo extends DiscoveredSystemInfo {
        public String name;
        public String description;
        public String VirtualIP;
        public String status;
        public String siteId;
        public String id;
        public String networkHealth;

        public StandByInfo() {
        }

        public StandByInfo(SiteRestRep standByInfo) {
            this.name = standByInfo.getName();
            this.VirtualIP = standByInfo.getVip();
            this.status = standByInfo.getState();
            this.id = standByInfo.getUuid();
            this.description = standByInfo.getDescription();
            this.networkHealth = standByInfo.getNetworkHealth();
        }
    }
}
