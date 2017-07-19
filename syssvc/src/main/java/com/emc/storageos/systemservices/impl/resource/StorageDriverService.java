/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

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
import static com.emc.storageos.storagedriver.util.DriverMetadataUtil.*;
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
    private static final String READY = "READY";
    private static final String IN_USE = "IN_USE";
    private static final String EVENT_SERVICE_TYPE = "StorageDriver";
    private static final int MAX_DRIVER_SIZE = 20 * 1024 * 1024; // 20MB
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
        log.info("These storage provider types are being referred: {}", Arrays.toString(types.toArray()));
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
        precheckForMetaData(metaData, dbClient);
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

        Set<String> driverNames = getAllDriverNames(dbClient);
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
        List<StorageSystemType> types = listStorageSystemTypes(dbClient);
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
        precheckForMetaData(metaData, dbClient,true, force);
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
        String driverFileName = driverFile.getName();

        return parseMetadata(props, driverFileName);
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
        List<StorageSystemType> types = listStorageSystemTypes(dbClient);

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

    protected void auditOperation(OperationTypeEnum type, String status, String stage, Object... descparams) {
        auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE, type, System.currentTimeMillis(), status, stage,
                descparams);
    }
}
