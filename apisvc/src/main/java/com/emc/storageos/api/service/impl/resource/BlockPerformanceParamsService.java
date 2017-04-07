/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.PerformanceParams;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.BlockPerformanceParamsCreate;
import com.emc.storageos.model.block.BlockPerformanceParamsRestRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

/**
 * API service for creating and managing PerformanceParams instances.
 */
@Path("/block/performance-params")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, readAcls = { ACL.USE },
writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class BlockPerformanceParamsService extends TaggedResource {

    // Logger reference
    private static final Logger logger = LoggerFactory.getLogger(BlockPerformanceParamsService.class);

    /**
     * Create a block PerformanceParams instance.
     *
     * @prereq none
     * 
     * @param param The details for the new PerformanceParams instance to be created.
     * 
     * @brief Create block PerformanceParams instance.
     * 
     * @return BlockPerformanceParamsRestRep
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockPerformanceParamsRestRep createBlockPerformanceParams(BlockPerformanceParamsCreate param) {
        
        // Verify the name.
        String performanceParamsName = param.getName();
        ArgValidator.checkFieldNotEmpty(performanceParamsName, "Name");
        checkForDuplicateName(performanceParamsName, PerformanceParams.class);
        
        URI performanceParamsURI = URIUtil.createId(PerformanceParams.class);
        logger.info("Creating PerformanceParams instance {} with id {}", performanceParamsName, performanceParamsURI);

        PerformanceParams performanceParams = new PerformanceParams();
        performanceParams.setId(performanceParamsURI);
        performanceParams.setLabel(param.getName());
        performanceParams.setDescription(param.getDescription());
        performanceParams.setAutoTierPolicyName(param.getAutoTieringPolicyName());
        performanceParams.setCompressionEnabled(param.getCompressionEnabled());
        performanceParams.setHostIOLimitBandwidth(param.getHostIOLimitBandwidth());
        performanceParams.setHostIOLimitIOPs(param.getHostIOLimitIOPs());
        performanceParams.setThinVolumePreAllocationPercentage(param.getThinVolumePreAllocationPercentage());
        performanceParams.setDedupCapable(param.getDedupCapable());
        performanceParams.setFastExpansion(param.getFastExpansion());
        _dbClient.createObject(performanceParams);
        
        return BlockMapper.map(performanceParams);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataObject queryResource(URI id) {
        PerformanceParams perfParams = _permissionsHelper.getObjectById(id, PerformanceParams.class);
        ArgValidator.checkEntityNotNull(perfParams, id, isIdEmbeddedInURL(id));
        return perfParams;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.BLOCK_VPOOL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }
}
