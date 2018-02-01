/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.model.StorageDriverMetaData;
import com.emc.storageos.coordinator.client.model.StorageDriversInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.model.storagedriver.StorageDriverList;
import com.emc.storageos.model.storagedriver.StorageDriverRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.storagedriver.StorageDriverManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.systemservices.mapper.StorageDriverMapper;
import com.emc.vipr.model.sys.ClusterInfo;
import com.google.common.io.Files;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

/**
 * APIs implementation to storage driver lifecycle management such as install,
 * uninstall and upgrade.
 */
@Path("/storagedriver")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })
public class StorageDriverService {

    private static final Logger log = LoggerFactory.getLogger(StorageDriverService.class);

    // meta data fields related constants
    private static final String META_DEF_FILE_NAME = "metadata.properties";
    private static final String DRIVER_NAME = "driver_name";
    private static final String DRIVER_VERSION = "driver_version";
    private static final Pattern DRIVER_VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
    private static final String STORAGE_NAME = "storage_name";
    private static final String STORAGE_DISPLAY_NAME = "storage_display_name";
    private static final String PROVIDER_NAME = "provider_name";
    private static final String PROVIDER_DISPLAY_NAME = "provider_display_name";
    private static final String STORAGE_META_TYPE = "meta_type";
    private static final String ENABLE_SSL = "enable_ssl";
    private static final String NON_SSL_PORT = "non_ssl_port";
    private static final String SSL_PORT = "ssl_port";
    private static final String DRIVER_CLASS_NAME = "driver_class_name";
    private static final String SUPPORT_AUTO_TIER_POLICY = "support_auto_tier_policy";
    private static final int DRIVER_VERSION_NUM_SIZE = 4;
    private static final Set<String> VALID_META_TYPES = new HashSet<String>(
            Arrays.asList(new String[] { "block", "file", "block_and_file", "object" }));
    private static final String READY = "READY";
    private static final String IN_USE = "IN_USE";
    private static final String EVENT_SERVICE_TYPE = "StorageDriver";

    // TODO we may need to make these 2 values configurable by overwrite it with
    // value from ZK
    private static final int MAX_DRIVER_NUMBER = 25;
    private static final int MAX_DRIVER_SIZE = 20 * 1024 * 1024; // 20MB
    private static final int MAX_DISPLAY_STRING_LENGTH = 50;
    private static final String STORAGE_DRIVER_OPERATION_lOCK = "storagedriveroperation";
    private static final int LOCK_WAIT_TIME_SEC = 5; // 5 seconds
    private static final OrderStatus[] BLOCKING_STATES = new OrderStatus[] {OrderStatus.EXECUTING, OrderStatus.PENDING};

    @Autowired
    private AuditLogManager auditMgr;

    private CoordinatorClient coordinator;
    private CoordinatorClientExt coordinatorExt;
    private DbClient dbClient;
    private LocalRepository localRepo = LocalRepository.getInstance();

    public void setLocalRepo(LocalRepository localRepo) {
        this.localRepo = localRepo;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCoordinatorExt(CoordinatorClientExt coordinatorExt) {
        this.coordinatorExt = coordinatorExt;
        this.coordinator = coordinatorExt.getCoordinatorClient();
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{driverName}")
    public StorageDriverRestRep getSingleStorageDriver(@PathParam("driverName") String name) {
        List<StorageDriverRestRep> drivers = queryStorageDriver(name);
        if (drivers.isEmpty()) {
            throw APIException.badRequests.driverNameNotFound(name);
        }
        return drivers.get(0);
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StorageDriverList getStorageDrivers() {
        StorageDriverList driverList = new StorageDriverList();
        driverList.setDrivers(queryStorageDriver(null));
        return driverList;
    }

    /**
     * 
     * @return driver specified by the name; return all drivers if name is null
     */
    private List<StorageDriverRestRep> queryStorageDriver(String name) {
        Set<String> usedProviderTypes = getUsedStorageProviderTypes();
        Set<String> usedSystemTypes = getUsedStorageSystemTypes();

        Map<String, StorageDriverRestRep> driverMap = new HashMap<String, StorageDriverRestRep>();
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> it = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (it.hasNext()) {
            StorageSystemType type = it.next();
            String driverName = type.getDriverName();
            if (name != null && !name.equals(driverName)) {
                continue;
            }
            if (type.getIsNative() == null || type.getIsNative()) {
                // bypass native storage types
                continue;
            }

            setTypeDriverStatus(type, usedProviderTypes, usedSystemTypes);
            setDriverIntoMap(driverName, type, driverMap);
        }
        List<StorageDriverRestRep> drivers = new ArrayList<StorageDriverRestRep>();
        drivers.addAll(driverMap.values());
        return drivers;
    }

    private void setDriverIntoMap(String driverName, StorageSystemType type, Map<String, StorageDriverRestRep> driverMap) {
        if (driverMap.containsKey(driverName)) {
            StorageDriverRestRep driverRestRep = driverMap.get(driverName);
            driverRestRep.getSupportedTypes().add(type.getStorageTypeDispName());
            if (!(IN_USE.equals(driverRestRep.getDriverStatus()) && READY.equals(type.getDriverStatus()))) {
                driverRestRep.setDriverStatus(type.getDriverStatus());
            }
        } else {
            driverMap.put(type.getDriverName(), StorageDriverMapper.map(type));
        }
    }

    private void setTypeDriverStatus(StorageSystemType type, Set<String> usedProviderTypes,
            Set<String> usedSystemTypes) {
        if (!StringUtils.equals(type.getDriverStatus(), StorageSystemType.STATUS.ACTIVE.toString())) {
            return;
        }
        type.setDriverStatus(READY);
        if (usedProviderTypes.contains(type.getStorageTypeName())
                || usedSystemTypes.contains(type.getStorageTypeName())) {
            type.setDriverStatus(IN_USE);
        }
    }

    protected Set<String> getUsedStorageProviderTypes() {
        List<URI> ids = dbClient.queryByType(StorageProvider.class, true);
        Set<String> types = new HashSet<String>();
        Iterator<StorageProvider> it = dbClient.queryIterativeObjects(StorageProvider.class, ids);
        while (it.hasNext()) {
            types.add(it.next().getInterfaceType());
        }
        log.info("These storage provider types are being refered: {}", Arrays.toString(types.toArray()));
        return types;
    }

    protected Set<String> getUsedStorageSystemTypes() {
        List<URI> ids = dbClient.queryByType(StorageSystem.class, true);
        Set<String> types = new HashSet<String>();
        Iterator<StorageSystem> it = dbClient.queryIterativeObjects(StorageSystem.class, ids);
        while (it.hasNext()) {
            types.add(it.next().getSystemType());
        }
        log.info("These storage system types are being refered: {}", Arrays.toString(types.toArray()));
        return types;
    }

    @GET
    @Path("/internal/download")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response getDriver(@QueryParam("name") String name) throws FileNotFoundException {
        log.info("download driver {} ...", name);
        InputStream in = new FileInputStream(StorageDriverManager.DRIVER_DIR + name);
        return Response.ok(in).type(MediaType.APPLICATION_OCTET_STREAM).build();
    }

    // Extract this method for the convenience of UT mocking
    protected void moveDriverToDataDir(File f) throws IOException {
        Files.move(f, new File(StorageDriverManager.DRIVER_DIR + f.getName()));
    }

    @POST
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response install(@FormDataParam("driver") InputStream uploadedInputStream,
            @FormDataParam("driver") FormDataContentDisposition details) {
        String fileName = details.getFileName();
        precheckForDriverFileName(fileName);
        long fileSize = details.getSize();
        log.info("Received driver jar file: {}, size: {}", fileName, fileSize);
        if (fileSize >= MAX_DRIVER_SIZE) {
            throw APIException.badRequests.fileSizeExceedsLimit(MAX_DRIVER_SIZE);
        }
        precheckForEnv();
        File driverFile = saveToTmpDir(fileName, uploadedInputStream);
        StorageDriverMetaData metaData = parseDriverMetaData(driverFile);
        precheckForMetaData(metaData);
        InterProcessLock lock = getStorageDriverOperationLock();
        try {
            // move file from /tmp to /data/drivers
            moveDriverToDataDir(driverFile);
            // insert meta data int db
            List<StorageSystemType> types = StorageDriverMapper.map(metaData);
            for (StorageSystemType type : types) {
                type.setDriverStatus(StorageSystemType.STATUS.INSTALLING.toString());
                type.setIsNative(false);
                dbClient.createObject(type);
                log.info("Added storage system type {}, set status to INSTALLING", type.getStorageTypeName());
            }
            // update local list in ZK
            Set<String> localDrivers = localRepo.getLocalDrivers();
            StorageDriversInfo info = new StorageDriversInfo();
            info.setInstalledDrivers(localDrivers);
            coordinatorExt.setNodeSessionScopeInfo(info);
            log.info("Updated local driver list to syssvc service beacon: {}", Arrays.toString(localDrivers.toArray()));
            // update target list in ZK
            info = coordinator.getTargetInfo(StorageDriversInfo.class);
            if (info == null) {
                info = new StorageDriversInfo();
            }
            info.getInstalledDrivers().add(metaData.getDriverFileName());
            coordinator.setTargetInfo(info);
            log.info("Successfully triggered install operation for driver", metaData.getDriverName());
            auditOperation(OperationTypeEnum.INSTALL_STORAGE_DRIVER, AuditLogManager.AUDITLOG_SUCCESS,
                    AuditLogManager.AUDITOP_BEGIN, metaData.getDriverName());
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Error happened when installing driver file", e);
            auditOperation(OperationTypeEnum.INSTALL_STORAGE_DRIVER, AuditLogManager.AUDITLOG_FAILURE, null,
                    metaData.getDriverName());
            throw APIException.internalServerErrors.installDriverFailed(e.getMessage());
        } finally {
            try {
                lock.release();
            } catch (Exception ignore) {
                log.error(String.format("Lock release failed when installing driver %s", metaData.getDriverName()));
            }
        }
    }

    @DELETE
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{driverName}")
    public Response uninstall(@PathParam("driverName") String driverName) {
        log.info("Start to uninstall driver {} ...", driverName);

        Set<String> driverNames = getAllDriverNames();
        if (!driverNames.contains(driverName)) {
            throw APIException.badRequests.driverNameNotFound(driverName);
        }

        precheckForEnv();
        List<StorageSystemType> toUninstallTypes = filterTypesByDriver(driverName);
        precheckForDriverStatus(toUninstallTypes, driverName);
        InterProcessLock lock = getStorageDriverOperationLock();
        try {
            StorageDriversInfo info = coordinator.getTargetInfo(StorageDriversInfo.class);
            if (info == null) {
                info = new StorageDriversInfo();
            }

            for (StorageSystemType type : toUninstallTypes) {
                type.setDriverStatus(StorageSystemType.STATUS.UNISNTALLING.toString());
                dbClient.updateObject(type);
                info.getInstalledDrivers().remove(type.getDriverFileName());
            }
            // update target list in ZK
            coordinator.setTargetInfo(info);
            log.info("Successfully triggered uninstall operation for driver {}", driverName);
            auditOperation(OperationTypeEnum.UNINSTALL_STORAGE_DRIVER, AuditLogManager.AUDITLOG_SUCCESS,
                    AuditLogManager.AUDITOP_BEGIN, driverName);
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Error happened when installing driver file", e);
            auditOperation(OperationTypeEnum.UNINSTALL_STORAGE_DRIVER, AuditLogManager.AUDITLOG_FAILURE, null,
                    driverName);
            throw APIException.internalServerErrors.uninstallDriverFailed(e.getMessage());
        } finally {
            try {
                lock.release();
            } catch (Exception ignore) {
                log.error(String.format("Lock release failed when uninstalling driver %s", driverName));
            }
        }
    }

    private List<StorageSystemType> filterTypesByDriver(String driverName) {
        List<StorageSystemType> types = listStorageSystemTypes();
        List<StorageSystemType> toUninstallTypes = new ArrayList<StorageSystemType>();
        for (StorageSystemType type : types) {
            if (!StringUtils.equals(driverName, type.getDriverName())) {
                continue;
            }
            toUninstallTypes.add(type);
        }
        return toUninstallTypes;
    }

    private void precheckForDriverStatus(List<StorageSystemType> toUninstallTypes, String driverName) {
        Set<String> usedProviderTypes = getUsedStorageProviderTypes();
        Set<String> usedSystemTypes = getUsedStorageSystemTypes();
        for (StorageSystemType type : toUninstallTypes) {
            if (usedProviderTypes.contains(type.getStorageTypeName())
                    || usedSystemTypes.contains(type.getStorageTypeName())) {
                throw APIException.badRequests.cantUninstallDriverInUse(driverName);
            }
        }
    }

    protected File saveToTmpDir(String fileName, InputStream uploadedInputStream) {
        File driverFile = new File(StorageDriverManager.TMP_DIR + fileName);
        OutputStream os = null;
        try {
            os = new FileOutputStream(driverFile);
            long copiedSize = IOUtils.copyLarge(uploadedInputStream, os, 0, MAX_DRIVER_SIZE);
            if (copiedSize >= MAX_DRIVER_SIZE) {
                throw APIException.badRequests.fileSizeExceedsLimit(MAX_DRIVER_SIZE);
            }
            uploadedInputStream.close();
        } catch (IOException e) {
            log.error("Error happened when uploading driver file", e);
            throw APIException.internalServerErrors.installDriverUploadFailed(e.getMessage());
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    log.error("Error happened when closing output stream of driver file {}", fileName, e);
                }
            }
        }

        log.info("Finished saving driver file to {}", driverFile.getAbsolutePath());
        return driverFile;
    }

    @POST
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/{driverName}")
    public Response upgrade(@PathParam("driverName") String driverName,
            @FormDataParam("driver") InputStream uploadedInputStream,
            @FormDataParam("driver") FormDataContentDisposition details,
            @FormDataParam("force") @DefaultValue("false") Boolean force) {
        log.info("Start to upgrade driver for {} ...", driverName);
        String fileName = details.getFileName();
        precheckForDriverFileName(fileName);
        long fileSize = details.getSize();
        log.info("Received driver jar file: {}, size: {}", fileName, fileSize);
        if (fileSize >= MAX_DRIVER_SIZE) {
            throw APIException.badRequests.fileSizeExceedsLimit(MAX_DRIVER_SIZE);
        }
        precheckForEnv();
        File driverFile = saveToTmpDir(fileName, uploadedInputStream);
        StorageDriverMetaData metaData = parseDriverMetaData(driverFile);
        if (!StringUtils.equals(driverName, metaData.getDriverName())) {
            throw APIException.internalServerErrors.upgradeDriverPrecheckFailed(
                    String.format("Driver name specified in jar file is not %s", driverName));
        }
        precheckForMetaData(metaData, true, force);
        InterProcessLock lock = getStorageDriverOperationLock();
        try {
            moveDriverToDataDir(driverFile);

            // save new meta data to ZK
            coordinator.persistServiceConfiguration(metaData.toConfiguration());

            // update status to UPGRADING in db
            StorageDriversInfo targetInfo = coordinator.getTargetInfo(StorageDriversInfo.class);
            if (targetInfo == null) {
                targetInfo = new StorageDriversInfo();
            }
            List<StorageSystemType> types = filterTypesByDriver(driverName);
            for (StorageSystemType type : types) {
                type.setDriverStatus(StorageSystemType.STATUS.UPGRADING.toString());
                dbClient.updateObject(type);
                // remove old driver file name from target list
                targetInfo.getInstalledDrivers().remove(type.getDriverFileName());
            }

            coordinator.setTargetInfo(targetInfo);
            log.info("Successfully triggered upgrade operation for driver", metaData.getDriverName());
            auditOperation(OperationTypeEnum.UPGRADE_STORAGE_DRIVER, AuditLogManager.AUDITLOG_SUCCESS,
                    AuditLogManager.AUDITOP_BEGIN, driverName);
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Error happened when upgrading driver file", e);
            auditOperation(OperationTypeEnum.UPGRADE_STORAGE_DRIVER, AuditLogManager.AUDITLOG_FAILURE, null,
                    driverName);
            throw APIException.internalServerErrors.upgradeDriverFailed(e.getMessage());
        } finally {
            try {
                lock.release();
            } catch (Exception ignore) {
                log.error(String.format("Lock release failed when upgrading driver %s", metaData.getDriverName()));
            }
        }
    }

    private Set<String> getAllDriverNames() {
        List<StorageSystemType> types = listStorageSystemTypes();
        Set<String> drivers = new HashSet<String>();
        for (StorageSystemType type : types) {
            drivers.add(type.getDriverName());
        }
        return drivers;
    }

    private void precheckForMetaData(StorageDriverMetaData metaData) {
        precheckForMetaData(metaData, false, false);
    }

    private void compareVersion(String oldVersionStr, String newVersionStr) {
        String[] oldVersionSegs = oldVersionStr.split("\\.");
        String[] newVersionSegs = newVersionStr.split("\\.");
        if (oldVersionSegs.length != DRIVER_VERSION_NUM_SIZE || newVersionSegs.length != DRIVER_VERSION_NUM_SIZE) {
            throw APIException.internalServerErrors.upgradeDriverPrecheckFailed(
                    String.format("Invalid driver version format (four numbers separated by dot), old: %s, new: %s",
                            oldVersionStr, newVersionStr));
        }
        for (int i = 0; i < DRIVER_VERSION_NUM_SIZE; i ++) {
            int oldVersion = Integer.valueOf(oldVersionSegs[i]);
            int newVersion = Integer.valueOf(newVersionSegs[i]);
            if (newVersion > oldVersion) {
                return;
            } else if (newVersion < oldVersion) {
                throw APIException.internalServerErrors.upgradeDriverPrecheckFailed(String.format(
                        "new version (%s) should be later than the old one (%s)", newVersionStr, oldVersionStr));
            }
        }
        throw APIException.internalServerErrors.upgradeDriverPrecheckFailed(String.format(
                "new version (%s) should be later than the old one (%s)", newVersionStr, oldVersionStr));
    }

    private void precheckForDupField(String existingValue, String newValue, String fieldName) {
        if (StringUtils.equals(existingValue, newValue)) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    String.format("duplicate %s: %s", fieldName, newValue));
        }
    }

    private void precheckForMetaData(StorageDriverMetaData metaData, boolean upgrade, boolean force) {
        List<StorageSystemType> types = listStorageSystemTypes();
        Set<String> drivers = new HashSet<String>();
        boolean driverNameExists = false;
        for (StorageSystemType type : types) {
            if (upgrade && StringUtils.equals(type.getDriverName(), metaData.getDriverName())) {
                driverNameExists = true;
                if (!force) {
                    String oldVersion = type.getDriverVersion();
                    String newVersion = metaData.getDriverVersion();
                    compareVersion(oldVersion, newVersion);
                }
            } else {
                precheckForDupField(type.getDriverName(), metaData.getDriverName(), "driver name");
                precheckForDupField(type.getStorageTypeName(), metaData.getStorageName(), "storage name");
                precheckForDupField(type.getStorageTypeDispName(), metaData.getStorageDisplayName(), "display name");
                precheckForDupField(type.getStorageTypeName(), metaData.getProviderName(), "provider name");
                precheckForDupField(type.getStorageTypeDispName(), metaData.getProviderDisplayName(), "provider display name");
                precheckForDupField(type.getDriverClassName(), metaData.getDriverClassName(), "driver class name");
            }
            precheckForDupField(type.getDriverFileName(), metaData.getDriverFileName(), "driver file name");
            drivers.add(type.getDriverName());
        }
        if (upgrade && !driverNameExists) {
            throw APIException.internalServerErrors.upgradeDriverPrecheckFailed(
                    String.format("Can't find specified driver name: %s", metaData.getDriverName()));
        }
        if (!upgrade && drivers.size() >= MAX_DRIVER_NUMBER) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(String
                    .format("Can't install more drivers as max driver number %s has been reached", MAX_DRIVER_NUMBER));
        }
    }

    private void precheckForNotEmptyField(String fieldName, String value) {
        if (StringUtils.isEmpty(value)) {
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed(String.format("%s field value is not provided", fieldName));
        }
    }

    private void precheckForDriverFileName (String fileName) {
        precheckForNotEmptyField("driver file name", fileName);
        if (!fileName.endsWith(".jar") && !fileName.endsWith(".JAR")) {
            throw APIException.internalServerErrors
            .installDriverPrecheckFailed("driver file name should end with .jar or .JAR as suffix");
        }
        if (hasForbiddenChar(fileName.substring(0, fileName.length() - ".jar".length()))) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    "driver file name (not include .jar suffix part) should only contain letter, digit, dash or underline");
        }
    }

    private void precheckForDriverName(String name) {
        precheckForNotEmptyField("driver_name", name);
        if (name.length() > MAX_DISPLAY_STRING_LENGTH) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    String.format("driver name is longer than %s", MAX_DISPLAY_STRING_LENGTH));
        }
        if (hasForbiddenChar(name)) {
            throw APIException.internalServerErrors
            .installDriverPrecheckFailed("driver name should only contain letter, digit, dash or underline");
        }
    }

    private boolean hasForbiddenChar(String name) {
        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c != '_' && c != '-' && !Character.isLetter(c) && !Character.isDigit(c)) {
                return true;
            }
        }
        return false;
    }

    private void precheckForDriverVersion(String driverVersion) {
        precheckForNotEmptyField("driver_version", driverVersion);
        if (!DRIVER_VERSION_PATTERN.matcher(driverVersion).find()) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    "driver_version field value should be four numbers concatenated by dot");
        }
    }

    private void precheckForProviderName(String providerName, String providerDisplayName,
            StorageDriverMetaData metaData) {
        if (StringUtils.isNotEmpty(providerName) && StringUtils.isNotEmpty(providerDisplayName)) {
            metaData.setProviderName(providerName);
            metaData.setProviderDisplayName(providerDisplayName);
        } else if (StringUtils.isEmpty(providerName) && StringUtils.isEmpty(providerDisplayName)) {
            // This driver doesn't support provider, which is allowed, so do
            // nothing
        } else {
            // This is ambiguous input, which should cause exception
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    "provider_name and provider_display_name fields values should be both providerd or not");
        }
    }

    private Properties extractPropsFromFile(String driverFilePath) {
        Properties props = new Properties();
        try {
            ZipFile zipFile = new ZipFile(driverFilePath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!META_DEF_FILE_NAME.equals(entry.getName())) {
                    continue;
                }
                props.load(zipFile.getInputStream(entry));
            }
            zipFile.close();
        } catch (Exception e) {
            log.error("Error happened when parsing meta data from JAR file", e);
            // use precheck error for now
            throw APIException.internalServerErrors.installDriverPrecheckFailed(e.getMessage());
        }
        if (props.isEmpty()) {
            log.error("Didn't find metadata.properties file or file is empty");
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed("Didn't find metadata.properties file or file is empty");
        }
        log.info("Have successfully load meta data properties from JAR file");
        return props;
    }

    protected StorageDriverMetaData parseDriverMetaData(File driverFile) {
        String driverFilePath = driverFile.getAbsolutePath();
        Properties props = extractPropsFromFile(driverFilePath);

        StorageDriverMetaData metaData = new StorageDriverMetaData();
        // check driver name
        String driverName = props.getProperty(DRIVER_NAME);
        precheckForDriverName(driverName);
        metaData.setDriverName(driverName);
        // check driver version and format
        String driverVersion = props.getProperty(DRIVER_VERSION);
        precheckForDriverVersion(driverVersion);
        metaData.setDriverVersion(driverVersion);
        // check storage name
        String storageName = props.getProperty(STORAGE_NAME);
        precheckForNotEmptyField("storage_name", storageName);
        metaData.setStorageName(storageName);
        // check storage display name
        String storageDisplayName = props.getProperty(STORAGE_DISPLAY_NAME);
        precheckForNotEmptyField("storage_display_name", storageDisplayName);
        metaData.setStorageDisplayName(storageDisplayName);
        // check provider name and provider display name
        String providerName = props.getProperty(PROVIDER_NAME);
        String providerDisplayName = props.getProperty(PROVIDER_DISPLAY_NAME);
        precheckForProviderName(providerName, providerDisplayName, metaData);
        // check meta type
        String metaType = props.getProperty(STORAGE_META_TYPE);
        precheckForMetaType(metaType);
        metaData.setMetaType(metaType.toUpperCase());
        // check enable_ssl
        String enableSslStr = props.getProperty(ENABLE_SSL);
        if (StringUtils.isNotEmpty(enableSslStr)) {
            boolean enableSsl = Boolean.valueOf(enableSslStr);
            metaData.setEnableSsl(enableSsl);
        } else {
            // default to false
            metaData.setEnableSsl(false);
        }
        // check ssl port
        try {
            String sslPortStr = props.getProperty(SSL_PORT);
            if (StringUtils.isNotEmpty(sslPortStr)) {
                long sslPort = 0L;
                sslPort = Long.valueOf(sslPortStr);
                metaData.setSslPort(sslPort);
            }
        } catch (NumberFormatException e) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed("SSL port format is not valid");
        }
        // check non ssl port
        try {
            String nonSslPortStr = props.getProperty(NON_SSL_PORT);
            if (StringUtils.isNotEmpty(nonSslPortStr)) {
                long nonSslPort = 0L;
                nonSslPort = Long.valueOf(nonSslPortStr);
                metaData.setNonSslPort(nonSslPort);
            }
        } catch (NumberFormatException e) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed("SSL port format is not valid");
        }
        // check driver class name
        String driverClassName = props.getProperty(DRIVER_CLASS_NAME);
        precheckForNotEmptyField("driver_class_name", driverClassName);
        metaData.setDriverClassName(driverClassName);
        // check if support auto-tier policy
        String supportAutoTierStr = props.getProperty(SUPPORT_AUTO_TIER_POLICY);
        if (StringUtils.isNotEmpty(supportAutoTierStr)) {
            boolean supportAutoTierPolicy = Boolean.valueOf(supportAutoTierStr);
            metaData.setSupportAutoTierPolicy(supportAutoTierPolicy);
        } else {
            // default to false
            metaData.setSupportAutoTierPolicy(false);
        }
        metaData.setDriverFileName(driverFile.getName());
        log.info("Parsed result from jar file: {}", metaData.toString());
        return metaData;
    }

    protected void precheckForEnv() {
        DrUtil drUtil = new DrUtil(coordinator);

        if (!drUtil.isActiveSite()) {
            throw APIException.internalServerErrors
                    .driverOperationEnvPrecheckFailed("This operation is not allowed on standby site");
        }

        for (Site site : drUtil.listSites()) {
            SiteState siteState = site.getState();
            if (!siteState.equals(SiteState.ACTIVE) && !siteState.equals(SiteState.STANDBY_SYNCED)) {
                throw APIException.internalServerErrors.driverOperationEnvPrecheckFailed(
                        String.format("Site %s is in %s state,not active or synced", site.getName(), siteState));
            }

            ClusterInfo.ClusterState state = coordinator.getControlNodesState(site.getUuid());
            if (state != ClusterInfo.ClusterState.STABLE) {
                throw APIException.internalServerErrors
                        .driverOperationEnvPrecheckFailed(String.format("Currently site %s is not stable", site.getName()));
            }
        }

        // Reject request if there's any ongoing or queued order. This is a short-term solution
        // for sky-walker to prevent storage driver operations from disturbing order execution.
        // For long-term consideration, we need to implement a serialization mechanism among
        // driver operations and order executions to avoid impact on each other.
        if (hasOngoingQueuedOrders()) {
            throw APIException.internalServerErrors.driverOperationEnvPrecheckFailed(
                    "There are ongoing or queued orders now, please wait until these orders complete");
        }
    }

    private boolean hasOngoingQueuedOrders() {
        URIQueryResultList result = new URIQueryResultList();
        for (OrderStatus status : BLOCKING_STATES) {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getOrderStatusConstraint(status.toString()), result);
            if (result.iterator().hasNext()) {
                return true;
            }
        }
        return false;
    }

    protected InterProcessLock getStorageDriverOperationLock() {
        // Try to acquire lock, succeed or throw Exception
        InterProcessLock lock = coordinator.getSiteLocalLock(STORAGE_DRIVER_OPERATION_lOCK);
        boolean acquired;
        try {
            acquired = lock.acquire(LOCK_WAIT_TIME_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            try {
                lock.release();
            } catch (Exception ex) {
                log.error("Fail to release storage driver operation lock", ex);
            }
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    "Acquiring lock failed, there's another trigger operation holding lock");
        }
        if (!acquired) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    "Acquiring lock failed, there's another trigger operation holding lock");
        }

        // Check if there's ongoing storage operation, if there is, release lock
        // and throw exception
        StorageSystemType opOngoingStorageType = null;
        List<StorageSystemType> types = listStorageSystemTypes();

        for (StorageSystemType type : types) {
            String statusStr = type.getDriverStatus();
            if (statusStr == null) {
                log.info("Bypass type {} as it has no status field value", type.getStorageTypeName());
                continue;
            }
            StorageSystemType.STATUS status = Enum.valueOf(StorageSystemType.STATUS.class, type.getDriverStatus());
            if (status.isStorageOperationOngoing()) {
                opOngoingStorageType = type;
                break;
            }
        }

        if (opOngoingStorageType != null) {
            try {
                lock.release();
            } catch (Exception e) {
                log.error("Fail to release storage driver operation lock", e);
            }
            throw APIException.internalServerErrors.installDriverPrecheckFailed(String.format("Driver %s is in % state",
                    opOngoingStorageType.getDriverName(), opOngoingStorageType.getDriverStatus()));
        }
        return lock;
    }

    private void precheckForMetaType(String metaType) {
        if (!isValidMetaType(metaType)) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed("meta_type field value is not valid");
        }
    }

    private boolean isValidMetaType(String metaType) {
        if (StringUtils.isEmpty(metaType)) {
            return false;
        }
        return VALID_META_TYPES.contains(metaType);
    }

    private List<StorageSystemType> listStorageSystemTypes() {
        List<StorageSystemType> result = new ArrayList<StorageSystemType>();
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> it = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (it.hasNext()) {
            result.add(it.next());
        }
        return result;
    }

    protected void auditOperation(OperationTypeEnum type, String status, String stage, Object... descparams) {
        auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE, type, System.currentTimeMillis(), status, stage,
                descparams);
    }
}
