/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.model.StorageDriverMetaData;
import com.emc.storageos.coordinator.client.model.StorageDriversInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.driver.DriverManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.vipr.model.sys.ClusterInfo;
//import com.emc.storageos.systemservices.impl.util.DriverUtil;
import com.google.common.io.Files;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

/**
 * Defines the API for making requests to the storage driver service.
 */
@Path("/storagedriver")
public class DriverService {
    private static final Logger log = LoggerFactory.getLogger(DriverService.class);
    private static final String TMP_DIR = "/tmp/";

    // meta data fields related constants
    private static final String META_DEF_FILE_NAME = "metadata.properties";
    private static final String DRIVER_NAME = "driver_name";
    private static final String DRIVER_VERSION = "driver_version";
    private static final String DRIVER_VERSION_PATTERN = "^\\d+\\.\\d+\\.\\d+\\.\\d+$";
    private static final String STORAGE_NAME = "storage_name";
    private static final String STORAGE_DISPLAY_NAME = "storage_display_name";
    private static final String PROVIDER_NAME = "provider_name";
    private static final String PROVIDER_DISPLAY_NAME = "provider_display_name";
    private static final String STORAGE_META_TYPE = "meta_type";
    private static final String ENABLE_SSL = "enable_ssl";
    private static final String NON_SSL_PORT = "non_ssl_port";
    private static final String SSL_PORT = "ssl_port";
    private static final String DRIVER_CLASS_NAME = "driver_class_name";
    private static final Set<String> VALID_META_TYPES = new HashSet<String>(
            Arrays.asList(new String[] { "block", "file", "block_and_file", "object" }));
    // These 2 values need to be put in ZK, to make it configurable
    private static final int MAX_DRIVER_NUMBER = 25;
    private static final int MAX_DRIVER_SIZE = 20 << 20; // 20MB
    private static final int MAX_DISPLAY_STRING_LENGTH = 50;
    private static final String STORAGE_DRIVER_OPERATION_lOCK = "storagedriveroperation";
    private static final int LOCK_WAIT_TIME_SEC = 5; // 5 seconds

    private CoordinatorClient coordinator;
    private Service service;
    private CoordinatorClientExt coordinatorExt;
    private DbClient dbClient;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setCoordinatorExt(CoordinatorClientExt coordinatorExt) {
        this.coordinatorExt = coordinatorExt;
    }

    public void setService(Service service) {
        this.service = service;
    }

//    @POST
//    @Path("/uninstall")
//    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
//    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
//    public Response uninstall(StorageDriverListParam driverList) {
//        // check all storage system types supported by this driver, mark as uninstalling
//        LocalRepository localRepo = LocalRepository.getInstance();
//        StorageDriversInfo targetInfo = coordinator.getTargetInfo(StorageDriversInfo.class);
//        StorageDriversInfo localInfo = new StorageDriversInfo();
//        localInfo.setInstalledDrivers(localRepo.getLocalDrivers());
//        for (String driverName : driverList.getDrivers()) {
//            // mark as uninstalling
//            markDriverStatus(driverName, DriverManager.UNINSTALLING);
//            // remove locally
//            localRepo.removeStorageDriver(driverName);
//            // remove from target info
//            if (targetInfo.getInstalledDrivers().contains(driverName)) {
//                targetInfo.getInstalledDrivers().remove(driverName);
//            }
//            // remove from local info
//            if (localInfo.getInstalledDrivers().contains(driverName)) {
//                localInfo.getInstalledDrivers().remove(driverName);
//            }
//        }
//        // restart controller service
//        localRepo.restart(DriverManager.CONTROLLER_SERVICE);
//        // update local list
//        coordinatorExt.setNodeSessionScopeInfo(localInfo);
//        // update local list
//        coordinator.setTargetInfo(targetInfo);
//        return Response.ok().build();
//    }
//
//    private void markDriverStatus(String driverName, String status) {
//        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
//        Iterator<StorageSystemType> it = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
//        while (it.hasNext()) {
//            StorageSystemType type = it.next();
//            if (StringUtils.equals(type.getDriverFileName(), driverName)) {
//                type.setStatus(status);
//                dbClient.updateObject(type);
//            }
//        }
//    }

    @GET
    @Path("/internal/download")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response getDriver(@QueryParam("name") String name) throws FileNotFoundException {
        log.info("download driver {} ...", name);
        InputStream in = new FileInputStream(DriverManager.DRIVER_DIR  + name);
        return Response.ok(in).type(MediaType.APPLICATION_OCTET_STREAM).build();
    }

    @POST
    @Path("/install")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response install(@FormDataParam("driver") InputStream uploadedInputStream,
            @FormDataParam("driver") FormDataContentDisposition details) {
        // Precheck for driver size
        String fileName = details.getFileName();
        long fileSize = details.getSize();
        log.info("Received driver jar file: {}, size: {}", fileName, fileSize);
        if (fileSize >= MAX_DRIVER_SIZE) {
            throw APIException.badRequests.fileSizeExceedsLimit(MAX_DRIVER_SIZE);
        }
        // Precheck for environment
        precheckForEnv();

        File driverFile = new File(TMP_DIR + fileName);
        try {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(driverFile));
            int bytesRead = 0;
            while (true) {
                byte[] buffer = new byte[0x10000];
                bytesRead = uploadedInputStream.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                os.write(buffer, 0, bytesRead);
            }
            uploadedInputStream.close();
            os.close();
        } catch (Exception e) {
            log.error("Error happened when uploading driver file", e);
            throw APIException.internalServerErrors.installDriverUploadFailed(e.getMessage());
        }

        log.info("Finished uploading driver file");

        StorageDriverMetaData metaData = parseDriverMetaData(driverFile);

        precheckForMetaData(metaData);

        InterProcessLock lock = getStorageDriverOperationLock();
        try {
            // move file from /tmp to /data/drivers
            Files.move(driverFile, new File(DriverManager.DRIVER_DIR + driverFile.getName()));

            // insert meta data int db
            List<StorageSystemType> types = DriverManager.convert(metaData);
            for (StorageSystemType type : types) {
                type.setStatus(StorageSystemType.STATUS.INSTALLING.toString());
                type.setIsNative(false);
                dbClient.createObject(type);
            }

            // update local list in ZK
            Set<String> localDrivers = LocalRepository.getInstance().getLocalDrivers();
            StorageDriversInfo info = new StorageDriversInfo();
            info.setInstalledDrivers(localDrivers);
            coordinatorExt.setNodeSessionScopeInfo(info);

            // update target list in ZK
            info = coordinator.getTargetInfo(StorageDriversInfo.class);
            if (info == null) {
                info = new StorageDriversInfo();
            }
            info.getInstalledDrivers().add(metaData.getDriverFileName());
            coordinator.setTargetInfo(info);
            log.info("Successfully triggered install operation for driver", metaData.getDriverName());
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Error happened when installing driver file", e);
            throw APIException.internalServerErrors.installDriverFailed(e.getMessage());
        } finally {
            try {
                lock.release();
            } catch (Exception ignore) {
                log.error(String.format("Lock release failed when installing driver %s", metaData.getDriverName()));
            }
        }
    }

    /**
     * Precheck conditions:
     *  1. This site must be active site;
     *  2. All sites should only be in ACTIVE or STANDBY_SYNCED state;
     *  3. All sites should be stable (all syssvcs are online).
     */
    private void precheckForEnv() {
        DrUtil drUtil = new DrUtil(coordinator);

        if (!drUtil.isActiveSite()) {
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed("This operation is not allowed on standby site");
        }

        for (Site site : drUtil.listSites()) {
            SiteState siteState = site.getState();
            if (!siteState.equals(SiteState.ACTIVE) && !siteState.equals(SiteState.STANDBY_SYNCED)) {
                throw APIException.internalServerErrors.installDriverPrecheckFailed(
                        String.format("Site %s is in %s state,not synced", site.getName(), siteState));
            }

            ClusterInfo.ClusterState state = coordinator.getControlNodesState(site.getUuid());
            if (state != ClusterInfo.ClusterState.STABLE) {
                throw APIException.internalServerErrors
                        .installDriverPrecheckFailed(String.format("Currently site %s is not stable", site.getName()));
            }
        }
    }


    /**
     * Precheck conditions: 
     * 1. driver name, storage name, storage display name, provider name,
     *    provider display name, driver class name not duplicate;
     * 2. Driver number not bigger than MAX_DRIVER_NUMBER
     * 
     */
    private void precheckForMetaData(StorageDriverMetaData metaData) {
        List<StorageSystemType> types = listStorageSystemTypes();
        Set<String> drivers = new HashSet<String>();
        for (StorageSystemType type : types) {
            if (StringUtils.equals(type.getDriverName(), metaData.getDriverName())) {
                throw APIException.internalServerErrors.installDriverPrecheckFailed("Duplicate driver name");
            }
            if (StringUtils.equals(type.getStorageTypeName(), metaData.getStorageName())) {
                throw APIException.internalServerErrors.installDriverPrecheckFailed("Duplicate storage name");
            }
            if (StringUtils.equals(type.getStorageTypeDispName(), metaData.getStorageDisplayName())) {
                throw APIException.internalServerErrors.installDriverPrecheckFailed("Duplicate storage display name");
            }
            if (StringUtils.equals(type.getStorageTypeName(), metaData.getProviderName())) {
                throw APIException.internalServerErrors.installDriverPrecheckFailed("Duplicate provider name");
            }
            if (StringUtils.equals(type.getStorageTypeDispName(), metaData.getProviderDisplayName())) {
                throw APIException.internalServerErrors.installDriverPrecheckFailed("Duplicate provider display name");
            }
            if (StringUtils.equals(type.getDriverClassName(), metaData.getDriverClassName())) {
                throw APIException.internalServerErrors.installDriverPrecheckFailed("Duplicate provider display name");
            }
            drivers.add(type.getDriverName());
        }
        if (drivers.size() > MAX_DRIVER_NUMBER) {
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed("Can't install more drivers as max driver number has been reached");
        }
    }

    private StorageDriverMetaData parseDriverMetaData(File driverFile) {
        String driverFilePath = driverFile.getAbsolutePath();
        Properties props = new Properties();
        try {
            ZipFile zipFile = new ZipFile(driverFilePath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            
            while(entries.hasMoreElements()){
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
        log.info("Have successfully load meta data properties from JAR file");
        
        StorageDriverMetaData metaData = new StorageDriverMetaData();
        // check driver name
        String driverName = props.getProperty(DRIVER_NAME);
        if (StringUtils.isEmpty(driverName)) {
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed("driver_name field value is not provided");
        }
        if (driverName.length() > MAX_DISPLAY_STRING_LENGTH) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    String.format("driver name is longger than %s", MAX_DISPLAY_STRING_LENGTH));
        }
        metaData.setDriverName(driverName);
        // check driver version and format
        String driverVersion = props.getProperty(DRIVER_VERSION);
        if (StringUtils.isEmpty(driverVersion)) {
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed("driver_version field value is not provided");
        }
        if (!Pattern.compile(DRIVER_VERSION_PATTERN).matcher(driverVersion).find()) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    "driver_version field value should be four numbers concatenated by dot");
        }
        metaData.setDriverVersion(driverVersion);
        // check storage name
        String storageName = props.getProperty(STORAGE_NAME);
        if (StringUtils.isEmpty(storageName)) {
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed("storage_name field value is not provided");
        }
        metaData.setStorageName(storageName);
        // check storage display name
        String storageDisplayName = props.getProperty(STORAGE_DISPLAY_NAME);
        if (StringUtils.isEmpty(storageDisplayName)) {
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed("storage_display_name field value is not provided");
        }
        metaData.setStorageDisplayName(storageDisplayName);
        // check provider name and provider display name
        String providerName = props.getProperty(PROVIDER_NAME);
        String providerDisplayName = props.getProperty(PROVIDER_DISPLAY_NAME);
        if (StringUtils.isNotEmpty(providerName) && StringUtils.isNotEmpty(providerDisplayName)) {
            metaData.setProviderName(providerName);
            metaData.setProviderDisplayName(providerDisplayName);
        } else if (StringUtils.isEmpty(providerName) && StringUtils.isEmpty(providerDisplayName)) {
            // This driver doesn't support provider, which is allowed, so do nothing
        } else {
            // This is ambiguous input, which should cause exception
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    "provider_name and provider_display_name fields values should be both providerd or not");
        }
        // check meta type
        String metaType = props.getProperty(STORAGE_META_TYPE);
        if (!isValidMetaType(metaType)) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed("meta_type field value is not valid");
        }
        metaData.setMetaType(metaType);
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
        if (StringUtils.isEmpty(driverClassName)) {
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed("driver_class_name field value is not provided");
        }
        metaData.setDriverClassName(driverClassName);
        metaData.setDriverFileName(driverFile.getName());
        log.info("Parsed result from jar file: {}", metaData.toString());
        return metaData;
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

    private InterProcessLock getStorageDriverOperationLock() {
     // Try to acquire lock, succeed or throw Exception
        InterProcessLock lock = coordinator.getLock(STORAGE_DRIVER_OPERATION_lOCK);
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

        // Check if there's ongoing storage operation, if there is, release lock and throw exception
        StorageSystemType  ongoingDriver = null;
        List<StorageSystemType> types = listStorageSystemTypes();
        
        for (StorageSystemType type : types) {
            StorageSystemType.STATUS status = Enum.valueOf(StorageSystemType.STATUS.class, type.getStatus());
            if (status.hasOngoingOperation()) {
                ongoingDriver = type;
                break;
            }
        }

        if (ongoingDriver != null) {
            try {
                lock.release();
            } catch (Exception e) {
                log.error("Fail to release storage driver operation lock", e);
            }
            throw APIException.internalServerErrors.installDriverPrecheckFailed(String
                    .format("Driver %s is in % state", ongoingDriver.getDriverName(), ongoingDriver.getStatus()));
        }

        return lock;
    }
}
