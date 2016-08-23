/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package models.datatable;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import util.datatable.DataTable;

public class StorageSystemTypeDataTable extends DataTable {

    public StorageSystemTypeDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("storageSystemTypeDisplayName");
        addColumn("metaType");
        addColumn("isProvider");
        addColumn("portNumber");
        addColumn("sslPortNumber");
        addColumn("driverClassName");
        sortAll();
    }

    public static class StorageSystemTypeInfo {
        public String id;
        public String name;
        public String storageSystemTypeDisplayName;
        public String metaType;
        public String portNumber;
        public String sslPortNumber;
        public Boolean useSSL;
        public Boolean isOnlyMDM;
        public Boolean isElementMgr;
        public Boolean useMDM;
        public Boolean isProvider;
        public String driverClassName;

        public StorageSystemTypeInfo() {
        }

        public StorageSystemTypeInfo(StorageSystemTypeRestRep storageSysType) {
            this.id = storageSysType.getStorageTypeId();
            this.name = storageSysType.getStorageTypeName();
            this.storageSystemTypeDisplayName = storageSysType.getStorageTypeDispName();
            this.metaType = storageSysType.getMetaType();
            this.portNumber = storageSysType.getNonSslPort();
            this.sslPortNumber = storageSysType.getSslPort();
            this.useSSL = storageSysType.getIsDefaultSsl();
            this.isOnlyMDM = storageSysType.getIsOnlyMDM();
            this.isElementMgr = storageSysType.getIsElementMgr();
            this.useMDM = storageSysType.getIsDefaultMDM();
            this.isProvider = storageSysType.getIsSmiProvider();
            this.driverClassName = storageSysType.getDriverClassName();
        }
    }
}
