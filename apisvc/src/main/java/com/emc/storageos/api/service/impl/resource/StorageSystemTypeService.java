/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.SystemsMapper.map;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.server.impl.StorageSystemTypesInitUtils;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

/**
 * StorageSystemTypes resource implementation
 */
@Path("/vdc/storage-system-types")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })

public class StorageSystemTypeService extends TaskResourceService {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypeService.class);
    private static final String EVENT_SERVICE_TYPE = "StorageSystemTypeService";
    private static final String UPLOAD_DEVICE_DRIVER = "/data/storagedrivers/";
    private static final String ALL_TYPE = "all";

    /**
     * Show Storage System Type detail for given URI
     *
     * @param id
     *            the URN of Storage System Type
     * @brief Show StorageSystemType
     * @return Storage System Type details
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public StorageSystemTypeRestRep getStorageSystemType(@PathParam("id") URI id) {
        log.info("GET getStorageSystemType on Uri: " + id);

        ArgValidator.checkFieldUriType(id, StorageSystemType.class, "id");
        StorageSystemType storageType = queryResource(id);
        ArgValidator.checkEntity(storageType, id, isIdEmbeddedInURL(id));
        StorageSystemTypeRestRep storageTypeRest = new StorageSystemTypeRestRep();
        return map(storageType, storageTypeRest);
    }

    /**
     * Returns a list of all Storage System Types requested for like block,
     * file, object or all. Valid input parameters are block, file, object and
     * all
     * 
     * @brief Show list of storage system types base of type or all
     * @return List of all storage system types.
     */
    @GET
    @Path("/type/{type}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageSystemTypeList getStorageSystemTypes(@PathParam("type") String type) {
        log.info("GET getStorageSystemType on type: " + type);

        if (type != null) {
            ArgValidator.checkFieldValueFromEnum(type.toUpperCase(), "metaType",
                    StorageSystemType.META_TYPE.class);
        }

        List<URI> ids = _dbClient.queryByType(StorageSystemType.class, true);

        StorageSystemTypeList list = new StorageSystemTypeList();

        Iterator<StorageSystemType> iter = _dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (iter.hasNext()) {
            StorageSystemType ssType = iter.next();
            if (ssType.getStorageTypeId() == null) {
                ssType.setStorageTypeId(ssType.getId().toString());
            }
            if (StringUtils.equals(ALL_TYPE, type) || StringUtils.equals(type, ssType.getMetaType())) {
                list.getStorageSystemTypes().add(map(ssType));
            }
        }
        return list;
    }

    /**
     * Create a new storage system type that CoprHD is not natively supported.
     *
     * @param param
     *            The StorageSystemTypeAddParam object contains all the
     *            parameters for creation.
     * @brief Create storage system type: Please note this API is available for
     *        short term solution. This will be discontinued and mechanism will
     *        be provided to add new storage type during driver deployment.
     * 
     * @return StorageSystemTypeRestRep object.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StorageSystemTypeRestRep addStorageSystemType(StorageSystemTypeAddParam addparam) {
        log.info("addStorageSystemType");

        ArgValidator.checkFieldNotEmpty(addparam.getStorageTypeName(), "storageTypeName");
        checkDuplicateLabel(StorageSystemType.class, addparam.getStorageTypeName());

        ArgValidator.checkFieldNotEmpty(addparam.getMetaType(), "metaType");

        ArgValidator.checkFieldNotEmpty(addparam.getDriverClassName(), "driverClassName");

        if (addparam.getIsDefaultSsl()) {
            ArgValidator.checkFieldNotEmpty(addparam.getSslPort(), "sslPort");
        } else {
            ArgValidator.checkFieldNotEmpty(addparam.getNonSslPort(), "nonSslPort");
        }

        StorageSystemType ssType = new StorageSystemType();
        URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
        ssType.setId(ssTyeUri);
        ssType.setStorageTypeId(ssTyeUri.toString());

        ssType.setStorageTypeName(addparam.getStorageTypeName());
        ssType.setMetaType(addparam.getMetaType());
        ssType.setDriverClassName(addparam.getDriverClassName());

        if (addparam.getStorageTypeDispName() != null) {
            ssType.setStorageTypeDispName(addparam.getStorageTypeDispName());
        }

        if (addparam.getNonSslPort() != null) {
            ssType.setNonSslPort(addparam.getNonSslPort());
        }

        if (addparam.getSslPort() != null) {
            ssType.setSslPort(addparam.getSslPort());
        }

        ssType.setIsSmiProvider(addparam.getIsSmiProvider());
        ssType.setIsDefaultSsl(addparam.getIsDefaultSsl());
        ssType.setIsDefaultMDM(addparam.getIsDefaultMDM());
        ssType.setIsOnlyMDM(addparam.getIsOnlyMDM());
        ssType.setIsElementMgr(addparam.getIsElementMgr());
        ssType.setIsSecretKey(addparam.getIsSecretKey());

        _dbClient.createObject(ssType);

        auditOp(OperationTypeEnum.ADD_STORAGE_SYSTEM_TYPE, true, AuditLogManager.AUDITOP_BEGIN,
                ssType.getId().toString(), ssType.getStorageTypeName(), ssType.getMetaType());
        return map(ssType);
    }

    /**
     * Delete existing Storage System Type.
     *
     * @param id
     * 
     * @brief Delete Storage System Type. Please note this API is available for
     *        short term solution. This will be discontinued and mechanism will
     *        be provided to delete given storage system type as part of device
     *        driver un-install process.
     * @return No data returned in response body
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteStorageSystemType(@PathParam("id") URI id) {
        log.info("deleteStorageSystemType: {}", id);
        // Name of Array and its Display Name mapping, cannot delete native
        // drivers
        Map<String, String> nativeDriverNameMap = StorageSystemTypesInitUtils.getDisplayNames();

        StorageSystemType sstype = queryObject(StorageSystemType.class, id, true);
        ArgValidator.checkEntity(sstype, id, isIdEmbeddedInURL(id));
        if (nativeDriverNameMap.get(sstype.getStorageTypeName()) == null) {
            _dbClient.markForDeletion(sstype);

            auditOp(OperationTypeEnum.REMOVE_STORAGE_SYSTEM_TYPE, true, AuditLogManager.AUDITOP_BEGIN,
                    sstype.getId().toString(), sstype.getStorageTypeName(), sstype.getMetaType());
            return Response.ok().build();
        } else {
            return Response.status(403).build();
        }

    }

    /**
     * Upload the device driver file. Consumes MediaType.MULTIPART_FORM_DATA.
     * This is an asynchronous operation.
     * 
     * @brief Upload the specified device driver file
     * @return Response information.
     */
    // This is not supported in 3.2 release. Uncomment for later use.

    // @POST
    // @Path("/upload")
    // @CheckPermission(roles = { Role.SYSTEM_ADMIN,
    // Role.RESTRICTED_SYSTEM_ADMIN })
    // @Consumes({ MediaType.APPLICATION_OCTET_STREAM,
    // MediaType.MULTIPART_FORM_DATA })
    // @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    private Response uploadFile(@FormDataParam("deviceDriver") InputStream fileInputStream,
            @FormDataParam("deviceDriver") FormDataContentDisposition contentDispositionHeader) {
        log.info("Upload of device driver file started, time: " + System.currentTimeMillis());

        String filePath = UPLOAD_DEVICE_DRIVER + contentDispositionHeader.getFileName();
        // save the file to the server
        saveFile(fileInputStream, filePath);
        log.info("Device driver file uploaded at " + filePath);
        Response myhttpresponse = Response.status(Response.Status.OK).build();
        return myhttpresponse;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected StorageSystemType queryResource(URI id) {
        ArgValidator.checkUri(id);
        StorageSystemType storageType = _dbClient.queryObject(StorageSystemType.class, id);
        ArgValidator.checkEntity(storageType, id, isIdEmbeddedInURL(id));
        return storageType;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.STORAGE_SYSTEM_TYPE;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    // save uploaded file to a defined location on the server
    private void saveFile(InputStream uploadedInputStream, String serverLocation) {
        try {
            OutputStream outpuStream = new FileOutputStream(new File(serverLocation));
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = uploadedInputStream.read(bytes)) != -1) {
                outpuStream.write(bytes, 0, read);
            }
            outpuStream.flush();
            outpuStream.close();
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }
    }

}
