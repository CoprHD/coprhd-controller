/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package controllers.arrays;

import static controllers.Common.backToReferrer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.api.mapper.SystemsMapper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.google.common.collect.Lists;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.MultiPart;
import com.sun.jersey.multipart.file.FileDataBodyPart;

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
import util.BourneUtil;
import util.MessagesUtils;
import util.StorageSystemTypeUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class StorageSystemTypes extends ViprResourceController {

    protected static final String UNKNOWN = "disasterRecovery.unknown";
    protected static final String DELETED_SUCCESS = "disasterRecovery.delete.success";
    protected static final String SAVED = "SMISProviders.saved";
    protected static final String TMP_DRIVER_FORMAT = "/tmp/%s";
//    protected static final String TMP_DRIVER_FORMAT = "C:\\Users\\caos1\\Downloads\\tests\\%s";

    public static void create() {
        render("@upload");
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

    private static void edit(StorageSystemTypeForm storageSystemTypes) {
        render("@edit", storageSystemTypes);
    }

    private static void edit(StorageSystemTypeForm storageSystemTypes, String driverFilePath) {
        render("@edit", storageSystemTypes, driverFilePath);
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
        String driverFilePath = params.get("storageSystemTypes.driverFilePath");
        storageSystemTypes.save(driverFilePath);
        flash.success(MessagesUtils.get(SAVED, storageSystemTypes.name));
        backToReferrer();
        list();
    }

    public static void uploadDriver(File deviceDriverFile) throws IOException {
        if (deviceDriverFile == null) {
            flash.error("Error: please specify a driver jar file");
            create();
        }

        File dest = new File(String.format(TMP_DRIVER_FORMAT, deviceDriverFile.getName()));
        if (dest.exists()) {
            dest.delete();
        }

        Files.copy(deviceDriverFile.toPath(), dest.toPath());
        StorageSystemType type = parseDriver(dest.getAbsolutePath());
        StorageSystemTypeForm form = new StorageSystemTypeForm(type);
        flash.success("Storage driver has been uploaded, please confirm/edit meta data for it");
        edit(form, dest.getName());
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<String> uuids = Arrays.asList(ids);
        itemsJson(uuids);
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
        public String metaType;

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
        
        public Boolean isSecretKey = false;

        public StorageSystemTypeForm() {
        }

        public StorageSystemTypeForm(StorageSystemTypeRestRep storageSysType) {
            readFrom(storageSysType);
        }

        public StorageSystemTypeForm(StorageSystemType type) {
            readFrom(SystemsMapper.map(type));
        }

        public void readFrom(StorageSystemTypeRestRep storageSysType) {
            this.id = storageSysType.getStorageTypeId();
            this.name = storageSysType.getStorageTypeName();
            this.storageSystemTypeDisplayName = storageSysType.getStorageTypeDispName();
            this.metaType = storageSysType.getMetaType();
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
            this.isSecretKey = storageSysType.getIsSecretKey();
        }

        public void save(String driverFilePath) {
            if (isNew()) {
                create(driverFilePath);
            }
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void create(String driverFilePath) {
            StorageSystemTypeAddParam addParams = new StorageSystemTypeAddParam();
            addParams.setStorageTypeName(name);
            addParams.setStorageTypeDispName(storageSystemTypeDisplayName);
            addParams.setMetaType(metaType);
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
            if (isSecretKey != null) {
                addParams.setIsSecretKey(isSecretKey);
            } else {
                addParams.setIsSecretKey(false);
            }

            addParams.setDriverFilePath(driverFilePath);
            addParams.setNode(BourneUtil.getSysApiUrl());

            StorageSystemTypeUtils.installStorageDriver(addParams);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
        }

    }

    private static StorageSystemType parseDriver(String path) {
        StorageSystemType type = new StorageSystemType();

        type.setStorageTypeName("typename");
        type.setMetaType("block");
        type.setDriverClassName("driver class");
        type.setStorageTypeDispName("display_name");
        type.setNonSslPort("1234");
        type.setSslPort("4321");

        type.setIsSmiProvider(false);
        type.setIsDefaultSsl(true);
        type.setIsDefaultMDM(false);
        type.setIsOnlyMDM(false);
        type.setIsElementMgr(false);
        type.setIsSecretKey(false);
        type.setDriverFileName(path +"_dirver_file_name");
        return type;
    }
}
