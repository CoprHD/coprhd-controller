/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import models.ProtectionSystemTypes;
import util.datatable.DataTable;

import com.emc.storageos.model.protection.ProtectionSystemRestRep;

public class DataProtectionSystemsDataTable extends DataTable {

    public DataProtectionSystemsDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("host");
        addColumn("systemType");
        addColumn("version").hidden();
        addColumn("userName").hidden().setSearchable(false);
        DataProtectionSystemInfo.addDiscoveryColumns(this);
        sortAll();
        this.setDefaultSort("name", "asc");
    }

    public static class DataProtectionSystemInfo extends DiscoveredSystemInfo {
        public String id;
        public String name;
        public String host;
        public String userName;
        public String systemType;
        public String version;

        public DataProtectionSystemInfo() {
        }

        public DataProtectionSystemInfo(ProtectionSystemRestRep protectionSystem) {
            super(protectionSystem);
            this.id = protectionSystem.getId().toString();
            this.name = protectionSystem.getName();

            this.version = protectionSystem.getMajorVersion();
            this.host = protectionSystem.getIpAddress();
            this.userName = protectionSystem.getUsername();
            this.systemType = ProtectionSystemTypes.getDisplayValue(protectionSystem.getSystemType());
        }
    }
}
