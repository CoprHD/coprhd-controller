/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import static com.emc.storageos.systemservices.mapper.StorageSystemTypeMapper.map;

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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.DriverInfo2;
import com.emc.storageos.coordinator.client.model.StorageDriversInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.systemservices.impl.driver.DriverManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
//import com.emc.storageos.systemservices.impl.util.DriverUtil;
import com.google.common.io.Files;
import com.sun.jersey.multipart.FormDataParam;

/**
 * Defines the API for making requests to the storage driver service.
 */
@Path("/storagedriver/")
public class DriverService {
    private static final Logger log = LoggerFactory.getLogger(DriverService.class);
    private static final String UPLOAD_DEVICE_DRIVER = "/tmp/";

    // meta data fields related constants
    private static final String META_DEF_FILE_NAME = "metadata.properties";
    private static final String STORAGE_NAME = "storage_name";
    private static final String PROVIDER_NAME = "provider_name";
    private static final String STORAGE_DISPLAY_NAME = "storage_display_name";
    private static final String PROVIDER_DISPLAY_NAME = "provider_display_name";
    private static final String STORAGE_META_TYPE = "meta_type";
    private static final String ENABLE_SSL = "enable_ssl";
    private static final String NON_SSL_PORT = "non_ssl_port";
    private static final String SSL_PORT = "ssl_port";
    private static final String DRIVER_CLASS_NAME = "driver_class_name";
    private static final String DRIVER_NAME = "driver_name";
    private static final Set<String> VALID_META_TYPES = new HashSet<String>(Arrays.asList(new String[] {"block", "file", "block_and_file", "object"}));

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

    /**
     * File has been uploaded and stored on current node, with meta data, will start to install and restart controller service
     */
    @POST
    @Path("install/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public StorageSystemTypeRestRep install(StorageSystemTypeAddParam addParam) {
        StorageSystemType type = map(addParam);
        type.setInstallStatus("Installing");
        dbClient.createObject(type);

        String localNode = coordinatorExt.getNodeEndpointForSvcId(service.getId()).toString();
        DriverInfo2 info = new DriverInfo2(coordinator.queryConfiguration(DriverInfo2.CONFIG_ID, DriverInfo2.CONFIG_ID));
        info.setInitNode(localNode);
        if (info.getDrivers() == null) {
            info.setDrivers(new ArrayList<String>());
        }
        info.getDrivers().add(addParam.getDriverFilePath());
        coordinator.persistServiceConfiguration(info.toConfiguration());
        log.info("set target info successfully");
        return map(type);
    }

    /**
     * Upload driver jar file as form data
     * @param uploadedInputStream
     * @param filename
     * @return
     * @throws IOException
     */
    @POST
    @Path("formstoreparse/{filename}") // Need to move filename out of the URL path, put it in post data
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public StorageSystemTypeAddParam formStoreParse(@FormDataParam("driver") InputStream uploadedInputStream,
            @PathParam("filename") URI filename) throws Exception {
        File f = new File(UPLOAD_DEVICE_DRIVER + filename);
        OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
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
        // May need to check file integrity

        String tmpFilePath = f.getName();
        return parseDriver(tmpFilePath);
    }
    /**
     * Upload JAR file, return parsed meta data, including storing path: Save this API for vipr-cli
     */
    @POST
    @Path("storeandparse/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes({ MediaType.APPLICATION_OCTET_STREAM })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public StorageSystemTypeAddParam parseAndStore(@Context HttpServletRequest request, @QueryParam("filename") String name) throws Exception {
        log.info("Parse/Upload driver ...");
        InputStream driver = request.getInputStream();
        File f = new File(UPLOAD_DEVICE_DRIVER + name);
        OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
        int bytesRead = 0;
        while (true) {
            byte[] buffer = new byte[0x10000];
            bytesRead = driver.read(buffer);
            if (bytesRead == -1) {
                break;
            }
            os.write(buffer, 0, bytesRead);
        }
        driver.close();
        os.close();
        // Till now, driver file has been saved, need to parse file to get meta data and return
        String tmpFilePath = f.getName();
        return parseDriver(tmpFilePath);
    }

    @GET
    @Path("internal/download/")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response getDriver(@QueryParam("name") String name) throws FileNotFoundException {
        log.info("download driver {} ...", name);
        InputStream in = new FileInputStream(DriverManager.DRIVER_DIR  + name);
        return Response.ok(in).type(MediaType.APPLICATION_OCTET_STREAM).build();
    }
    // ---------------------- for test only -----------------------------------
    @POST
    @Path("oneshotinstall/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response oneshotInstall(@FormDataParam("driver") InputStream uploadedInputStream) throws Exception {
        File f = new File(UPLOAD_DEVICE_DRIVER + UUID.randomUUID());
        OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
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
        // May need to check file integrity in real product code

        String tmpFilePath = f.getName();
        StorageSystemTypeAddParam params = parseDriver(tmpFilePath);

        // move to /data/drivers
        Files.move(f, new File(DriverManager.DRIVER_DIR + params.getDriverName()));

        LocalRepository localRepo = LocalRepository.getInstance();
        // restart controller service
        localRepo.restart(DriverManager.CONTROLLER_SERVICE);
        // update local driver list
        Set<String> localDrivers = localRepo.getLocalDrivers();
        StorageDriversInfo info = new StorageDriversInfo();
        info.setInstalledDrivers(localDrivers);
        coordinatorExt.setNodeSessionScopeInfo(info);

        // insert meta data into db
        StorageSystemType type = map(params);
        type.setInstallStatus("Installing");
        type.setDriverFileName(params.getDriverName());
        dbClient.createObject(type);
        // update target list
        info = coordinator.getTargetInfo(StorageDriversInfo.class);
        if (info == null) {
            info = new StorageDriversInfo();
        }
        info.getInstalledDrivers().add(params.getDriverName());
        coordinator.setTargetInfo(info);
        return Response.ok().build();
    }
    // ---------------------- for test only -----------------------------------
    private StorageSystemTypeAddParam parseDriver(String path) throws Exception {
        ZipFile zipFile = new ZipFile(UPLOAD_DEVICE_DRIVER + path);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        Properties metaData = new Properties();
        while(entries.hasMoreElements()){
            ZipEntry entry = entries.nextElement();
            if (!META_DEF_FILE_NAME.equals(entry.getName())) {
                continue;
            }
            metaData.load(zipFile.getInputStream(entry));
        }
        zipFile.close();

        // till now, meta data has been read into properties file

        StorageSystemTypeAddParam addParam = new StorageSystemTypeAddParam();

        // set storage name
        String storageName = metaData.getProperty(STORAGE_NAME);
        if (StringUtils.isEmpty(storageName)) {
            throw new RuntimeException("Storage name can't be null or empty");
        }
        addParam.setStorageTypeName(storageName);

        // set storage display name
        String storageDispName = metaData.getProperty(STORAGE_DISPLAY_NAME);
        if (StringUtils.isEmpty(storageDispName)) {
            throw new RuntimeException("Storage display name can't be null or empty");
        }
        addParam.setStorageTypeDispName(storageDispName);

        // set driver name
        String driverName = metaData.getProperty(DRIVER_NAME);
        if (StringUtils.isEmpty(driverName)) {
            throw new RuntimeException ("Driver name can't be null or empty");
        }
        addParam.setDriverName(driverName);

       // set storage driver class name
        String driverClassName = metaData.getProperty(DRIVER_CLASS_NAME);
        if (StringUtils.isEmpty(driverClassName)) {
            throw new RuntimeException("Storage display name can't be null or empty");
        }
        addParam.setDriverClassName(driverClassName);

        // set provider name and isSmiProvider field
        String providerName = metaData.getProperty(PROVIDER_NAME);
        String providerDispName = metaData.getProperty(PROVIDER_DISPLAY_NAME);
        if (StringUtils.isEmpty(providerName) && StringUtils.isEmpty(providerDispName)) {
            // not a provider
            addParam.setIsSmiProvider(false);
        } else if (StringUtils.isEmpty(providerName) || StringUtils.isEmpty(providerDispName)) {
            // required fields for provider is not filled completely
            throw new RuntimeException("Please fill both provider name and display name if it's a provider, otherwise, leave both fields blank");
        } else {
            // a provider
            addParam.setIsSmiProvider(true);
            addParam.setProviderName(providerName);
            addParam.setProviderDispName(providerDispName);
        }

        // set meta type
        String metaType = metaData.getProperty(STORAGE_META_TYPE);
        if (!isValidMetaType(metaType)) {
            throw new RuntimeException("Storage meta type can't be null, and could only be among block/file/block_and_file/object");
        }
        addParam.setMetaType(metaType);

        // set driver file path
        addParam.setDriverFilePath(path);

        // set enable_ssl_port and ssl port
        String enableSSL = metaData.getProperty(ENABLE_SSL);
        if (Boolean.toString(true).equals(enableSSL)) {
            addParam.setIsDefaultSsl(true);
            String sslPort = metaData.getProperty(SSL_PORT);
            if (sslPort == null) {
                throw new RuntimeException("SSL is enabled but SSL port is not specified");
            }
            // won't set ssl port if ssl is not enabled
            addParam.setSslPort(sslPort);
        }

        // set non ssl port
        String nonSslPort = metaData.getProperty(NON_SSL_PORT);
        if (nonSslPort != null) {
            addParam.setNonSslPort(nonSslPort);
        }

        return addParam;
    }
    private boolean isValidMetaType(String metaType) {
        if (metaType == null) {
            return false;
        }
        return VALID_META_TYPES.contains(metaType);
    }
}
