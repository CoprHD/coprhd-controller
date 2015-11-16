package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.block.tier.AutoTierPolicyList;
import com.emc.storageos.model.block.tier.StorageTierList;
import com.emc.storageos.model.block.tier.AutoTieringPolicyRestRep;
import com.emc.storageos.model.block.tier.AutoTieringPolicyBulkRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.api.mapper.functions.MapAutoTierPolicy;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.ArchivalPolicy;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.model.ResourceOperationTypeEnum;

/**
 * Archive service
 */
@Path("/archive/policies")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN })
public class ArchiveService extends TaggedResource {

    @Override
    protected DataObject queryResource(URI id) {

        ArgValidator.checkUri(id);
        ArchivalPolicy archivalPolicy = _dbClient.queryObject(ArchivalPolicy.class, id);
        ArgValidator.checkEntityNotNull(archivalPolicy, id, isIdEmbeddedInURL(id));
        return archivalPolicy;
    }

    @Override
    protected URI getTenantOwner(URI id) {

        return null;
    }

    /**
     * List all the exisiting archival policies
     * 
     * @prereq none
     * @brief List all archival policies
     * @return List<ArchivalPolicy>
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
//    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public List<ArchivalPolicy> getArchivalPolicies(){

        List<ArchivalPolicy> list = new ArrayList<ArchivalPolicy>();
        List<URI> policyUris = _dbClient.queryByType(ArchivalPolicy.class, true);
        list = _dbClient.queryObject(ArchivalPolicy.class, policyUris);
        
        return list;
    }

    /**
     * Show the specified archival policy.
     * 
     * @param id the URN of a ViPR archival policy
     * @prereq none
     * @brief Show the details of the specified archival policy
     * @return Policy Object
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
//    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}")
    public ArchivalPolicy getArchivalPolicy(@PathParam("id") URI id) {
        
        ArgValidator.checkFieldUriType(id, ArchivalPolicy.class, "id");
        ArchivalPolicy policy = _dbClient.queryObject(ArchivalPolicy.class, id);

        return policy;
    }

    /**
     * Create new policy and add it to DB
     * 
     * @param 
     * @prereq none
     * @brief Add the newly created policy to DB
     * @return Policy Object
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ArchivalPolicy createPolicy(
            @PathParam("policyName") String name, 
            @PathParam("unattachedPeriodUnit") TimeUnit periodUnit, 
            @PathParam("unattachedPeriodValue") Long periodValue,
            @PathParam("minSizeAllowed") Integer minSize,
            @PathParam("maxSizeAllowed") Integer maxSize) {

        _log.info("ArchiveService: createPolicy Request recieved {}", name);
        //String task = UUID.randomUUID().toString();

        // Create the ArchivalPolicy object for the DB
        ArchivalPolicy newPolicy= new ArchivalPolicy();
        newPolicy.setPolicyId(URIUtil.createId(ArchivalPolicy.class));
        newPolicy.setPolicyName(name);
        newPolicy.setUnattachedPeriodUnit(periodUnit);
        newPolicy.setUnattachedPeriodValue(periodValue);
        newPolicy.setMinSizeAllowed(minSize);
        newPolicy.setMaxSizeAllowed(maxSize);
        _dbClient.createObject(newPolicy);
        
        return newPolicy;
    }

    /**
     * Mark specified archival policy for deletion.
     * 
     * @param id the URN of a ViPR archival policy
     * @prereq none
     * @return Policy Object
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    //@CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}")
    public ArchivalPolicy deleteArchivalPolicy(@PathParam("id") URI id) {
        
        ArgValidator.checkFieldUriType(id, ArchivalPolicy.class, "id");
        ArchivalPolicy policy = _dbClient.queryObject(ArchivalPolicy.class, id);
        _dbClient.markForDeletion(policy);

        return policy;
    }

    /**
     * Modifies exisiting policy object in DB
     * 
     * @param id the URN of a ViPR fileSystem
     * @brief Update policy object
     * @return modified policy object
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public ArchivalPolicy updateArchivalPolicy(
        @PathParam("id") URI id,
        @PathParam("policyName") String name, 
        @PathParam("unattachedPeriodUnit") TimeUnit periodUnit, 
        @PathParam("unattachedPeriodValue") Long periodValue,
        @PathParam("minSizeAllowed") Integer minSize,
        @PathParam("maxSizeAllowed") Integer maxSize) {
        
        // log input received.
        _log.info("Update archival policy request received {}", id);
        //String task = UUID.randomUUID().toString();
        ArgValidator.checkFieldUriType(id, ArchivalPolicy.class, "id");
        ArchivalPolicy policy = _dbClient.queryObject(ArchivalPolicy.class, id);
        
        policy.setPolicyName(name);
        policy.setUnattachedPeriodUnit(periodUnit);
        policy.setUnattachedPeriodValue(periodValue);
        policy.setMinSizeAllowed(minSize);
        policy.setMaxSizeAllowed(maxSize);
        _dbClient.persistObject(policy);

        return policy;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        
        return ResourceTypeEnum.ARCHIVAL_POLICY;
    }

}