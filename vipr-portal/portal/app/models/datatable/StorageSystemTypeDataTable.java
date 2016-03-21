package models.datatable;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import util.datatable.DataTable;

public class StorageSystemTypeDataTable extends DataTable {

	public StorageSystemTypeDataTable() {
		addColumn("storageSystemTypeName").setRenderFunction("renderLink");
		addColumn("storageSystemTypeDisplayName");
		addColumn("storageSystemTypeType");
		addColumn("isProvider");
		addColumn("storageTypeId").hidden();
		sortAll();
	}

	public static class StorageSystemTypeInfo {
		public String storageTypeId;
		public String storageSystemTypeName;
		public String storageSystemTypeDisplayName;
		public String storageSystemTypeType;
		public String portNumber;
		public String sslPortNumber;
		public Boolean useSSL;
		public Boolean isOnlyMDM;
		public Boolean isElementMgr;
		public Boolean useMDM;
		public Boolean isProvider;

		public StorageSystemTypeInfo() {
		}

		public StorageSystemTypeInfo(StorageSystemTypeRestRep storageSysType) {
			this.storageTypeId = storageSysType.getStorageTypeId();
			this.storageSystemTypeName = storageSysType.getStorageTypeName();
			this.storageSystemTypeDisplayName = storageSysType.getStorageTypeDispName();
			this.storageSystemTypeType = storageSysType.getStorageTypeType();
			this.portNumber = storageSysType.getNonSslPort();
			this.sslPortNumber = storageSysType.getSslPort();
			this.useSSL = storageSysType.getIsDefaultSsl();
			this.isOnlyMDM = storageSysType.getIsOnlyMDM();
			this.isElementMgr = storageSysType.getIsElementMgr();
			this.useMDM = storageSysType.getIsDefaultMDM();
			this.isProvider = storageSysType.getIsSmiProvider();
		}
	}
}
