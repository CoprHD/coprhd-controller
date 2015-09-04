/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.functions.MapStorageTier;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.tier.StorageTierBulkRep;
import com.emc.storageos.model.block.tier.StorageTierList;
import com.emc.storageos.model.block.tier.StorageTierRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/vdc/storage-tiers")
@DefaultPermissions(read_roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        write_roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class StorageTierService extends TaggedResource {

    @Override
    protected StorageTier queryResource(URI id) {
        ArgValidator.checkUri(id);
        StorageTier tier = _dbClient.queryObject(StorageTier.class, id);
        ArgValidator.checkEntityNotNull(tier, id, isIdEmbeddedInURL(id));
        return tier;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * get Storage Tier associated with id.
     *
     * @param id the URN of a ViPR storage tier
     * @brief Show storage tier
     * @return Policy Object
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}")
    public StorageTierRestRep getStorageTier(@PathParam("id") URI id) {
        // CQECC00606330
        ArgValidator.checkFieldUriType(id, StorageTier.class, "id");
        StorageTier tier = queryResource(id);
        return map(tier);
    }

    /**
     * 
     * List all storage tiers
     * 
     * @prereq none
     * @brief List all storage tiers.
     * @return StorageTierList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageTierList getStorageTiers() {
        StorageTierList tierList = new StorageTierList();
        List<URI> tierUris = _dbClient.queryByType(StorageTier.class, true);
        List<StorageTier> tiers = _dbClient.queryObject(StorageTier.class, tierUris);
        for (StorageTier tier : tiers) {
            tierList.getStorageTiers().add(
                    DbObjectMapper.toNamedRelatedResource(ResourceTypeEnum.STORAGE_TIER,
                            tier.getId(), tier.getLabel()));
        }
        return tierList;
    }

    /**
     * Retrieve resource representations based on input ids.
     *
     * @param param POST data containing the id list.
     * @brief List data of storage tier resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public StorageTierBulkRep getBulkResources(BulkIdParam param) {
        return (StorageTierBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<StorageTier> getResourceClass() {
        return StorageTier.class;
    }

    @Override
    public StorageTierBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<StorageTier> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new StorageTierBulkRep(BulkList.wrapping(_dbIterator, MapStorageTier.getInstance()));
    }

    @Override
    public StorageTierBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.STORAGE_TIER;
    }

}
