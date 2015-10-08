/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteRestRep;


public class DisasterRecoveryDataTable extends DataTable {
    
    public DisasterRecoveryDataTable() {
        addColumn("siteName");
        addColumn("virtualIp");
        addColumn("status");
        addColumn("siteId");
        //addColumn("actions");
        sortAll();
        setDefaultSort("siteName", "asc");
        setRowCallback("createRowLink");
    }

    public static class StandByInfo {
        public String siteName;
        public String virtualIp;
        public String status;
        public String siteId;

        public StandByInfo() {
        }

        public StandByInfo(SiteRestRep standByInfo) {
            this.siteName = standByInfo.getName();
            this.virtualIp = standByInfo.getVip();
            this.status = standByInfo.getState();
            this.siteId = standByInfo.getUuid();
        }
    }
}
