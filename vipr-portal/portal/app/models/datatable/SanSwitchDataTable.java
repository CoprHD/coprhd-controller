/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import models.NetworkSystemTypes;

import org.apache.commons.lang.StringUtils;

import util.MessagesUtils;
import util.datatable.DataTable;

import com.emc.storageos.model.network.NetworkSystemRestRep;

public class SanSwitchDataTable extends DataTable {

    public SanSwitchDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        addColumn("host");
        addColumn("deviceType");
        addColumn("version").hidden();
        addColumn("userName").hidden();
        SanSwitchInfo.addDiscoveryColumns(this);

        sortAllExcept("id", "ipAddress", "portNumber");
        setDefaultSort("name", "asc");
    }

    public static class SanSwitchInfo extends DiscoveredSystemInfo {
        public String id;
        public String name;
        public String deviceType;
        public String host;
        public String version;
        public String userName;
        public String registrationStatus;

        public SanSwitchInfo() {
        }

        public SanSwitchInfo(NetworkSystemRestRep sanSwitch) {
            super(sanSwitch);
            this.id = sanSwitch.getId().toString();
            this.name = sanSwitch.getName();
            this.deviceType = NetworkSystemTypes.getDisplayValue(sanSwitch.getSystemType());
            this.version = StringUtils.defaultIfEmpty(sanSwitch.getVersion(), MessagesUtils.get("SanSwitchDataTable.notApplicable"));

            if (NetworkSystemTypes.isSmisManaged(sanSwitch.getSystemType())) {
                this.host = sanSwitch.getSmisProviderIP();
                this.userName = sanSwitch.getSmisUserName();
            }
            else {
                this.host = sanSwitch.getIpAddress();
                this.userName = sanSwitch.getUsername();
            }

            this.registrationStatus = sanSwitch.getRegistrationStatus();
        }
    }
}
