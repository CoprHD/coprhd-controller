/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.SystemsMapper.map;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

/**
 * StorageSystemTypes resource implementation
 */
@Path("/vdc/storage-system-types")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })

public class StorageSystemTypeService extends TaskResourceService {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypeService.class);
    private static final String EVENT_SERVICE_TYPE = "StorageSystemTypeService";
    private static final String ALL_TYPE = "all";

    /**
     * Show Storage System Type detail for given URI
     *
     * @param id
     *            the URN of Storage System Type
     * @brief Show storage system type of storage 
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
     * @brief List storage system types
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

}
