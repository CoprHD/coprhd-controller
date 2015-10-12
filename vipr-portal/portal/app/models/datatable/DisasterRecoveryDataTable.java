/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.storageos.model.dr.SiteRestRep;


public class DisasterRecoveryDataTable extends DataTable {
    
    public DisasterRecoveryDataTable() {
        addColumn("name");
        addColumn("VirtualIP");
        addColumn("status");
        addColumn("id");
        sortAll();
        setDefaultSort("siteName", "asc");
        setRowCallback("createRowLink");
    }

    public static class StandByInfo {
        public String name;
        public String VirtualIP;
        public String status;
        public String siteId;
        public String id;

        public StandByInfo() {
        }

        public StandByInfo(SiteRestRep standByInfo) {
            this.name = standByInfo.getName();
            this.VirtualIP = standByInfo.getVip();
            this.status = standByInfo.getState();
            this.id = standByInfo.getUuid();
        }
    }
}
