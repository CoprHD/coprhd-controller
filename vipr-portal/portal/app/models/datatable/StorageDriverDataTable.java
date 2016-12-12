/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.model.storagedriver.StorageDriverRestRep;

import util.datatable.DataTable;

public class StorageDriverDataTable extends DataTable {

    public StorageDriverDataTable() {
        addColumn("driverName");
        addColumn("driverVersion");
        addColumn("supportedStorageSystems");
        addColumn("type");
        addColumn("defaultNonSslPort");
        addColumn("defaultSslPort");
        addColumn("status").setRenderFunction("storageDriverStatus");
        addColumn("actions").setRenderFunction("renderButtonBar");
        sortAllExcept("actions");
    }

    public static class StorageDriverInfo {
        public String driverName;
        public String driverVersion;
        public List<String> supportedStorageSystems = new ArrayList<String>();
        public String type;
        public String defaultNonSslPort;
        public String defaultSslPort;
        public String status;
        public String id;

        public StorageDriverInfo() {
        }

        public StorageDriverInfo(StorageDriverRestRep driver) {
            this.driverName = driver.getDriverName();
            this.driverVersion = driver.getDriverVersion();
            this.supportedStorageSystems = driver.getSupportedTypes();
            this.type = driver.getMetaType();
            this.defaultNonSslPort = driver.getNonSslPort();
            this.defaultSslPort = driver.getSslPort();
            this.status = driver.getDriverStatus();
            this.id = driver.getDriverName();
        }
    }
}