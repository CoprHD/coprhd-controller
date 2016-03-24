package controllers.arrays;

import static controllers.Common.backToReferrer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.dr.SiteIdListParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.datatable.StorageSystemTypeDataTable;
import models.datatable.StorageSystemTypeDataTable.StorageSystemTypeInfo;
import play.Logger;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.DisasterRecoveryUtils;
import util.LicenseUtils;
import util.MessagesUtils;
import util.StorageSystemTypeUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class StorageSystemTypes extends ViprResourceController {

	protected static final String UNKNOWN = "disasterRecovery.unknown";
	protected static final String DELETED_SUCCESS = "disasterRecovery.delete.success";
	protected static final String SAVED = "SMISProviders.saved";

	public static void create() {
		StorageSystemTypeForm storageSystemType = new StorageSystemTypeForm();
		render("@edit", storageSystemType);
	}

	public static void list() {
		StorageSystemTypeDataTable dataTable = new StorageSystemTypeDataTable();
		render(dataTable);
	}

	@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
	public static void edit(String id) {
		StorageSystemTypeRestRep typeRest = StorageSystemTypeUtils.getStorageSystemType(id);
		if (typeRest != null) {
			StorageSystemTypeForm storageSystemType = new StorageSystemTypeForm(typeRest);
			edit(storageSystemType);
		} else {
			flash.error(MessagesUtils.get(UNKNOWN, id));
			list();
		}
	}

	private static void edit(StorageSystemTypeForm storageSystemType) {
		render("@edit", storageSystemType);
	}

	public static void listJson() {
		List<StorageSystemTypeDataTable.StorageSystemTypeInfo> storageSystemTypes = Lists.newArrayList();
		StorageSystemTypeUtils.getAllStorageSystemTypes("all");

		for (StorageSystemTypeRestRep storageSysType : StorageSystemTypeUtils.getAllStorageSystemTypes("all")
				.getStorageSystemTypes()) {
			storageSystemTypes.add(new StorageSystemTypeInfo(storageSysType));
		}
		renderJSON(DataTablesSupport.createJSON(storageSystemTypes, params));
	}

	@FlashException("list")
	@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
	public static void delete(@As(",") String[] ids) {
		List<String> uuids = Arrays.asList(ids);
		for (String uuid : uuids) {
			if (!DisasterRecoveryUtils.hasStandbySite(uuid)) {
				flash.error(MessagesUtils.get(UNKNOWN, uuid));
				list();
			}

		}

		SiteIdListParam param = new SiteIdListParam();
		param.getIds().addAll(uuids);
		DisasterRecoveryUtils.deleteStandby(param);
		flash.success(MessagesUtils.get(DELETED_SUCCESS));
		list();
	}

	@FlashException(keep = true, referrer = { "create", "edit" })
	public static void save(StorageSystemTypeForm storageSystemTypes) {
		storageSystemTypes.validate("storageSystemType");
		if (Validation.hasErrors()) {
			Common.handleError();
		}

		storageSystemTypes.save();
		flash.success(MessagesUtils.get(SAVED, storageSystemTypes.storageSystemTypeName));
		backToReferrer();
		list();
	}

    public static void uploadDriver(File newDeviceDriver) {

        File storaeDirectory = new File ("/data/");
        
        if (newDeviceDriver != null) {
            try {
            	FileUtils.copyFileToDirectory(newDeviceDriver, storaeDirectory);
            } catch (IOException e) {
                Validation.addError("newLicenseFile", MessagesUtils.get("license.invalidLicenseFile"));
                Logger.error(e, "Failed to read license text file");
            }
        }
    }
	
	
	@SuppressWarnings("squid:S2068")
	public static class StorageSystemTypeForm {

		public String storageTypeId;

		@MaxSize(128)
		@MinSize(2)
		@Required
		public String storageSystemTypeName;

		@MaxSize(128)
		public String storageSystemTypeDisplayName;

		@Required
		public String storageSystemTypeType;

		@Required
		public String portNumber;

		@Required
		public String sslPortNumber;

		public String driverClassName;

		public Boolean useSSL = false;

		public Boolean isOnlyMDM = false;

		public Boolean isElementMgr = false;

		public Boolean useMDM = false;

		public Boolean isProvider = false;

		public StorageSystemTypeForm() {
		}

		public StorageSystemTypeForm(StorageSystemTypeRestRep storageSysType) {
			readFrom(storageSysType);
		}

		public void readFrom(StorageSystemTypeRestRep storageSysType) {
			this.storageTypeId = storageSysType.getStorageTypeId();
			this.storageSystemTypeName = storageSysType.getStorageTypeName();
			this.storageSystemTypeDisplayName = storageSysType.getStorageTypeDispName();
			this.storageSystemTypeType = storageSysType.getStorageTypeType();
			if (null != storageSysType.getNonSslPort()) {
				this.portNumber = storageSysType.getNonSslPort();
			}
			if (null != storageSysType.getSslPort()) {
				this.sslPortNumber = storageSysType.getSslPort();
			}
			if (storageSysType.getDriverClassName() != null) {
				this.driverClassName = storageSysType.getDriverClassName();
			}

			this.useSSL = storageSysType.getIsDefaultSsl();
			this.isOnlyMDM = storageSysType.getIsOnlyMDM();
			this.isElementMgr = storageSysType.getIsElementMgr();
			this.useMDM = storageSysType.getIsDefaultMDM();
			this.isProvider = storageSysType.getIsSmiProvider();
		}

		public StorageSystemTypeRestRep save() {
			if (isNew()) {
				return create();
			}
			// else {
			// return update().getId();
			// }
			return null;
		}

		public boolean isNew() {
			return StringUtils.isBlank(storageTypeId);
		}

		public StorageSystemTypeRestRep create() {
			StorageSystemTypeAddParam addParams = new StorageSystemTypeAddParam();
			addParams.setStorageTypeName(storageSystemTypeName);
			addParams.setStorageTypeDispName(storageSystemTypeDisplayName);
			addParams.setStorageType(storageSystemTypeType);
			addParams.setDriverClassName(driverClassName);

			if (isProvider != null) {
				addParams.setIsSmiProvider(isProvider);
			} else {
				addParams.setIsSmiProvider(false);
			}
			if (useSSL != null) {
				addParams.setIsDefaultSsl(useSSL);
			} else {
				addParams.setIsDefaultSsl(false);
			}
			if (sslPortNumber != null) {
				addParams.setSslPort(sslPortNumber);
			}
			if (portNumber != null) {
				addParams.setNonSslPort(portNumber);
			}
			if (useMDM != null) {
				addParams.setIsDefaultMDM(useMDM);
			} else {
				addParams.setIsDefaultMDM(false);
			}
			if (isOnlyMDM != null) {
				addParams.setIsOnlyMDM(isOnlyMDM);
			} else {
				addParams.setIsOnlyMDM(false);
			}
			if (isElementMgr != null) {
				addParams.setIsElementMgr(isElementMgr);
			} else {
				addParams.setIsElementMgr(false);
			}

			StorageSystemTypeRestRep task = StorageSystemTypeUtils.addStorageSystemType(addParams);
			return task;
		}

		public void validate(String fieldName) {
			Validation.valid(fieldName, this);
		}

	}

}
