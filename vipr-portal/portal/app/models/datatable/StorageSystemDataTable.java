/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import models.StorageSystemTypes;

import org.apache.commons.lang.StringUtils;

import util.MessagesUtils;
import util.datatable.DataTable;

import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;

public class StorageSystemDataTable extends DataTable {

    protected static final String NAME_NOT_AVAILABLE = "StorageSystems.nameNotAvailable";

    public StorageSystemDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        addColumn("host");
        addColumn("type");
        addColumn("version").hidden();
        addColumn("userName").hidden();
        StorageSystemInfo.addDiscoveryColumns(this);
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class StorageSystemInfo extends DiscoveredSystemInfo {
        public String id;
        public String name;
        public String host;
        public String userName;
        public String type;
        public String version;
        public String registrationStatus;

        public StorageSystemInfo() {
        }

        public StorageSystemInfo(StorageSystemRestRep storageSystem) {
            super(storageSystem);
            this.id = storageSystem.getId().toString();
            this.name = StringUtils.defaultIfEmpty(
                    StringUtils.defaultIfEmpty(storageSystem.getName(), storageSystem.getSerialNumber()),
                    MessagesUtils.get(NAME_NOT_AVAILABLE));
            if (ResourceUtils.id(storageSystem.getActiveProvider()) != null) {
                this.host = storageSystem.getSmisProviderIP();
                this.userName = storageSystem.getSmisUserName();
            }
            else {
                this.host = storageSystem.getIpAddress();
                this.userName = storageSystem.getUsername();
            }
            this.type = StorageSystemTypes.getDisplayValue(storageSystem.getSystemType());
            this.version = storageSystem.getFirmwareVersion();
            this.registrationStatus = storageSystem.getRegistrationStatus();
        }
    }
}
