/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package controllers.arrays;

import static controllers.Common.backToReferrer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.api.mapper.SystemsMapper;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.datatable.StorageDriverDataTable;
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
public class StorageDrivers extends ViprResourceController {

    protected static final String UNKNOWN = "disasterRecovery.unknown";
    protected static final String DELETED_SUCCESS = "storageDrivers.delete.init.success";
    protected static final String SAVED = "SMISProviders.saved";
    
    protected static final String INTALL_INIT_SUCCESS = "storageDrivers.install.init.success";
    protected static final String UPGRADE_INIT_SUCCESS = "storageDrivers.upgrade.init.success";

    // show create page
    public static void create() {
        render("@upload");
    }

    // placeholder method, show upgrade page
    public static void upgrade(String driverName) {
    	render("@upload", driverName);
    }

    // placeholder method
    public static void remove(String driverName) {
        // TODO send request to back-end API
        flash.success(MessagesUtils.get(DELETED_SUCCESS));
        list();
    }

    public static void list() {
//        StorageSystemTypeDataTable dataTable = new StorageSystemTypeDataTable();
        StorageDriverDataTable dataTable = new StorageDriverDataTable();
        render(dataTable);
    }

//    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
//    public static void edit(String id) {
//        StorageSystemTypeRestRep typeRest = StorageSystemTypeUtils.getStorageSystemType(id);
//        if (typeRest != null) {
//            StorageSystemTypeForm storageSystemType = new StorageSystemTypeForm(typeRest);
//            edit(storageSystemType);
//        } else {
//            flash.error(MessagesUtils.get(UNKNOWN, id));
//            list();
//        }
//    }

//    private static void edit(StorageSystemTypeForm storageSystemTypes) {
//        render("@edit", storageSystemTypes);
//    }
//
//    private static void edit(StorageSystemTypeForm storageSystemTypes, String driverFilePath) {
//        render("@edit", storageSystemTypes, driverFilePath);
//    }

    public static void listJson() {
//        List<StorageSystemTypeDataTable.StorageSystemTypeInfo> storageSystemTypes = Lists.newArrayList();
//
//        for (StorageSystemTypeRestRep storageSysType : StorageSystemTypeUtils.getAllStorageSystemTypes("all")
//                .getStorageSystemTypes()) {
//            storageSystemTypes.add(new StorageSystemTypeInfo(storageSysType));
//        }
        List<StorageDriverDataTable.StorageDriverInfo> drivers = Lists.newArrayList();
        StorageDriverDataTable.StorageDriverInfo driver = new StorageDriverDataTable.StorageDriverInfo();
        driver.driverName = "foo_driver";
        driver.driverVersion = "1.0.0.1";
        driver.supportedStorageSystems.add("Fool System");
        driver.supportedStorageSystems.add("Storage Provider for Fool System");
        driver.type = "Block";
        driver.defaultNonSslPort = "8080";
        driver.defaultSslPort = "443";
        driver.status = "Ready";
        drivers.add(driver);
        driver = new StorageDriverDataTable.StorageDriverInfo();
        driver.driverName = "bar_driver";
        driver.driverVersion = "2.0.0.0";
        driver.supportedStorageSystems.add("Bar System");
        driver.type = "File";
        driver.defaultNonSslPort = "9000";
        driver.defaultSslPort = "4443";
        driver.status = "In Use";
        drivers.add(driver);
//        driver = new StorageDriverDataTable.StorageDriverInfo();
//        driver.driverName = "baz_driver";
//        driver.driverVersion = "1.0.0.0";
//        driver.supportedStorageSystems.add("Baz System");
//        driver.type = "Object";
//        driver.defaultNonSslPort = "9001";
//        driver.defaultSslPort = "4442";
//        driver.status = "Uninstalling";
//        drivers.add(driver);
        renderJSON(DataTablesSupport.createJSON(drivers, params));
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

//    @FlashException(keep = true, referrer = { "create", "edit" })
//    public static void save(StorageSystemTypeForm storageSystemTypes) {
//        storageSystemTypes.validate("storageSystemType");
//        if (Validation.hasErrors()) {
//            Common.handleError();
//        }
//        String driverFilePath = params.get("storageSystemTypes.driverFilePath");
//        storageSystemTypes.save(driverFilePath);
//        flash.success(MessagesUtils.get(SAVED, storageSystemTypes.name));
//        backToReferrer();
//        list();
//    }

    public static void uploadDriver(File deviceDriverFile, String driverName) throws IOException {
        if (deviceDriverFile == null) {
            flash.error("Error: please specify a driver jar file");
            if (driverName == null || driverName.isEmpty()) {
                create();
            } else {
                upgrade(driverName);
            }
        }

        // TODO upload driver
//        String filename = deviceDriverFile.getName();
//        StorageSystemTypeAddParam addParam = StorageSystemTypeUtils.uploadStorageDriver(deviceDriverFile, filename);
//        StorageSystemTypeForm form = new StorageSystemTypeForm(addParam);
//        edit(form, addParam.getDriverFilePath());
        if (driverName == null || driverName.isEmpty()) {
            flash.success(MessagesUtils.get(INTALL_INIT_SUCCESS));
        } else {
            flash.success(MessagesUtils.get(UPGRADE_INIT_SUCCESS));
        }
        list();
    }

//    public static void itemsJson(@As(",") String[] ids) {
//        List<String> uuids = Arrays.asList(ids);
//        itemsJson(uuids);
//    }
//
//    private static void itemsJson(List<String> uuids) {
//        List<StorageSystemTypeRestRep> standbySites = new ArrayList<StorageSystemTypeRestRep>();
//        for (String uuid : uuids) {
//            StorageSystemTypeRestRep standbySite = StorageSystemTypeUtils.getStorageSystemType(uuid);
//            if (standbySite != null) {
//                standbySites.add(standbySite);
//            }
//        }
//        performItemsJson(standbySites, new JsonItemOperation());
//    }
//
//    protected static class JsonItemOperation
//            implements ResourceValueOperation<StorageSystemTypeInfo, StorageSystemTypeRestRep> {
//        @Override
//        public StorageSystemTypeInfo performOperation(StorageSystemTypeRestRep provider) throws Exception {
//            return new StorageSystemTypeInfo(provider);
//        }
//    }

//    @SuppressWarnings("squid:S2068")
//    public static class StorageSystemTypeForm {
//
//        public String id;
//
//        @MaxSize(128)
//        @MinSize(2)
//        @Required
//        public String name;
//
//        @MaxSize(128)
//        public String storageSystemTypeDisplayName;
//
//        @Required
//        public String metaType;
//
//        @Required
//        public String portNumber;
//
//        @Required
//        public String sslPortNumber;
//
//        public String driverClassName;
//
//        public Boolean useSSL = false;
//
//        public Boolean isOnlyMDM = false;
//
//        public Boolean isElementMgr = false;
//
//        public Boolean useMDM = false;
//
//        public Boolean isProvider = false;
//        
//        public Boolean isSecretKey = false;
//        public String providerName;
//        public String providerDispName;
//
//        public StorageSystemTypeForm() {
//        }
//
//        public StorageSystemTypeForm(StorageSystemTypeAddParam params) {
//            this.name = params.getStorageTypeName();
//            this.storageSystemTypeDisplayName = params.getStorageTypeDispName();
//            this.metaType = params.getMetaType();
//            this.driverClassName = params.getDriverClassName();
//            this.isProvider = params.getIsSmiProvider();
//            this.useSSL = params.getIsDefaultSsl();
//            this.sslPortNumber = params.getSslPort();
//            this.portNumber = params.getNonSslPort();
//            this.useMDM = params.getIsDefaultMDM();
//            this.isOnlyMDM = params.getIsOnlyMDM();
//            this.isElementMgr = params.getIsElementMgr();
//            this.isSecretKey = params.getIsSecretKey();
//            this.providerName = params.getProviderName();
//            this.providerDispName = params.getProviderDispName();
//        }
//
//        public StorageSystemTypeForm(StorageSystemTypeRestRep storageSysType) {
//            readFrom(storageSysType);
//        }
//
//        public StorageSystemTypeForm(StorageSystemType type) {
//            readFrom(SystemsMapper.map(type));
//        }
//
//        public void readFrom(StorageSystemTypeRestRep storageSysType) {
//            this.id = storageSysType.getStorageTypeId();
//            this.name = storageSysType.getStorageTypeName();
//            this.storageSystemTypeDisplayName = storageSysType.getStorageTypeDispName();
//            this.metaType = storageSysType.getMetaType();
//            if (null != storageSysType.getNonSslPort()) {
//                this.portNumber = storageSysType.getNonSslPort();
//            }
//            if (null != storageSysType.getSslPort()) {
//                this.sslPortNumber = storageSysType.getSslPort();
//            }
//            if (storageSysType.getDriverClassName() != null) {
//                this.driverClassName = storageSysType.getDriverClassName();
//            }
//
//            this.useSSL = storageSysType.getIsDefaultSsl();
//            this.isOnlyMDM = storageSysType.getIsOnlyMDM();
//            this.isElementMgr = storageSysType.getIsElementMgr();
//            this.useMDM = storageSysType.getIsDefaultMDM();
//            this.isProvider = storageSysType.getIsSmiProvider();
//            this.isSecretKey = storageSysType.getIsSecretKey();
//        }

//        public void save(String driverFilePath) {
//            if (isNew()) {
//                create(driverFilePath);
//            }
//        }

//        public boolean isNew() {
//            return StringUtils.isBlank(id);
//        }
//
//        public void create(String driverFilePath) {
//            StorageSystemTypeAddParam addParams = new StorageSystemTypeAddParam();
//            addParams.setStorageTypeName(name);
//            addParams.setStorageTypeDispName(storageSystemTypeDisplayName);
//            addParams.setMetaType(metaType);
//            addParams.setDriverClassName(driverClassName);
//
//            if (isProvider != null && isProvider == true) {
//                addParams.setIsSmiProvider(true);
//                addParams.setProviderName(providerName);
//                addParams.setProviderDispName(providerDispName);
//            } else {
//                addParams.setIsSmiProvider(false);
//            }
//            if (useSSL != null) {
//                addParams.setIsDefaultSsl(useSSL);
//            } else {
//                addParams.setIsDefaultSsl(false);
//            }
//            if (sslPortNumber != null) {
//                addParams.setSslPort(sslPortNumber);
//            }
//            if (portNumber != null) {
//                addParams.setNonSslPort(portNumber);
//            }
//            if (useMDM != null) {
//                addParams.setIsDefaultMDM(useMDM);
//            } else {
//                addParams.setIsDefaultMDM(false);
//            }
//            if (isOnlyMDM != null) {
//                addParams.setIsOnlyMDM(isOnlyMDM);
//            } else {
//                addParams.setIsOnlyMDM(false);
//            }
//            if (isElementMgr != null) {
//                addParams.setIsElementMgr(isElementMgr);
//            } else {
//                addParams.setIsElementMgr(false);
//            }
//            if (isSecretKey != null) {
//                addParams.setIsSecretKey(isSecretKey);
//            } else {
//                addParams.setIsSecretKey(false);
//            }
//
//            addParams.setDriverFilePath(driverFilePath);
//
//            StorageSystemTypeUtils.installStorageDriver(addParams);
//        }
//
//        public void validate(String fieldName) {
//            Validation.valid(fieldName, this);
//        }
//    }
}
