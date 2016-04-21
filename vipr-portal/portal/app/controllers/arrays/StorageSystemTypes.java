package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.backToReferrer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Providers;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.google.common.collect.Lists;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.MultiPart;
import com.sun.jersey.multipart.file.FileDataBodyPart;
import com.sun.jersey.multipart.file.StreamDataBodyPart;
import javax.ws.rs.core.MediaType;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.StorageProviderTypes;
import models.datatable.StorageSystemTypeDataTable;
import models.datatable.StorageSystemTypeDataTable.StorageSystemTypeInfo;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
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
		renderArgs.put("supportedTypeOptions", StorageProviderTypes.getSupportedStorageType());
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

		for (StorageSystemTypeRestRep storageSysType : StorageSystemTypeUtils.getAllStorageSystemTypes("all")
				.getStorageSystemTypes()) {
			storageSystemTypes.add(new StorageSystemTypeInfo(storageSysType));
		}
		renderJSON(DataTablesSupport.createJSON(storageSystemTypes, params));
	}

	@FlashException("list")
	@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
	public static void delete(@As(",") String[] ids) {
		for (String id : ids) {
			StorageSystemTypeUtils.deleteStorageSystemType(id);
		}
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
		flash.success(MessagesUtils.get(SAVED, storageSystemTypes.name));
		backToReferrer();
		list();
	}

	public static void uploadDriver(File deviceDriverFile) {

		if (deviceDriverFile != null) {
			ClientResponse restResponse;
			try {
				FileInputStream fs = new FileInputStream(deviceDriverFile);
				deviceDriverFile.getName();

				FileDataBodyPart fdp = new FileDataBodyPart("deviceDriver", deviceDriverFile, MediaType.APPLICATION_OCTET_STREAM_TYPE);

				@SuppressWarnings("resource")
				MultiPart mdf = new FormDataMultiPart().bodyPart(fdp);

				restResponse = StorageSystemTypeUtils.uploadDriver(mdf);

				flash.success("Device driver jar file uploaded");
				list();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void itemsJson(@As(",") String[] ids) {
		List<String> uuids = Arrays.asList(ids);
		itemsJson(uuids);
	}

    public static void upload(String ids) {
    	render(ids);
    }
    
	private static void itemsJson(List<String> uuids) {
		List<StorageSystemTypeRestRep> standbySites = new ArrayList<StorageSystemTypeRestRep>();
		for (String uuid : uuids) {
			StorageSystemTypeRestRep standbySite = StorageSystemTypeUtils.getStorageSystemType(uuid);
			if (standbySite != null) {
				standbySites.add(standbySite);
			}
		}
		performItemsJson(standbySites, new JsonItemOperation());
	}

	protected static class JsonItemOperation
			implements ResourceValueOperation<StorageSystemTypeInfo, StorageSystemTypeRestRep> {
		@Override
		public StorageSystemTypeInfo performOperation(StorageSystemTypeRestRep provider) throws Exception {
			return new StorageSystemTypeInfo(provider);
		}
	}

	@SuppressWarnings("squid:S2068")
	public static class StorageSystemTypeForm {

		public String id;

		@MaxSize(128)
		@MinSize(2)
		@Required
		public String name;

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
			this.id = storageSysType.getStorageTypeId();
			this.name = storageSysType.getStorageTypeName();
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
			return null;
		}

		public boolean isNew() {
			return StringUtils.isBlank(id);
		}

		public StorageSystemTypeRestRep create() {
			StorageSystemTypeAddParam addParams = new StorageSystemTypeAddParam();
			addParams.setStorageTypeName(name);
			addParams.setStorageTypeDispName(storageSystemTypeDisplayName);
			addParams.setStorageTypeType(storageSystemTypeType);
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
