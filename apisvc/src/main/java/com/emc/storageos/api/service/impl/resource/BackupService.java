package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.model.BackupPolicy;
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
@Path("/backup/policies")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN })
public class BackupService extends TaggedResource {

    @Override
    protected DataObject queryResource(URI id) {

        ArgValidator.checkUri(id);
        BackupPolicy backupPolicy = _dbClient.queryObject(BackupPolicy.class, id);
        ArgValidator.checkEntityNotNull(backupPolicy, id, isIdEmbeddedInURL(id));
        return backupPolicy;
    }

    @Override
    protected URI getTenantOwner(URI id) {

        return null;
    }

    /**
     * List all the existing backup policies
     * 
     * @prereq none
     * @brief List all backup policies
     * @return List<BackupPolicy>
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
//    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public List<BackupPolicy> getBackupPolicies(){

        List<BackupPolicy> list = new ArrayList<BackupPolicy>();
        List<URI> policyUris = _dbClient.queryByType(BackupPolicy.class, true);
        list = _dbClient.queryObject(BackupPolicy.class, policyUris);
        
        return list;
    }

    /**
     * Show the specified backup policy.
     * 
     * @param id the URN of a ViPR backup policy
     * @prereq none
     * @brief Show the details of the specified backup policy
     * @return Policy Object
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
//    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}")
    public BackupPolicy getBackupPolicy(@PathParam("id") URI id) {
        
        ArgValidator.checkFieldUriType(id, BackupPolicy.class, "id");
        BackupPolicy policy = _dbClient.queryObject(BackupPolicy.class, id);

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
    public BackupPolicy createPolicy(
            @PathParam("policyName") String name, 
            @PathParam("incrementalPeriodUnit") TimeUnit periodUnit, 
            @PathParam("incrementalPeriodValue") Long periodValue,
            @PathParam("countForFullBackup") Integer count) {

        _log.info("BackupService: createPolicy Request recieved {}", name);
        //String task = UUID.randomUUID().toString();

        // Create the BackupPolicy object for the DB
        BackupPolicy newPolicy= new BackupPolicy();
        newPolicy.setPolicyId(URIUtil.createId(BackupPolicy.class));
        newPolicy.setPolicyName(name);
        newPolicy.setIncrementalPeriodUnit(periodUnit);
        newPolicy.setIncrementalPeriodValue(periodValue);
        newPolicy.setCountForFullBackup(count);
        _dbClient.createObject(newPolicy);
        
        return newPolicy;
    }

    /**
     * Mark specified backup policy for deletion.
     * 
     * @param id the URN of a ViPR backup policy
     * @prereq none
     * @return Policy Object
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    //@CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}")
    public BackupPolicy deleteBackupPolicy(@PathParam("id") URI id) {
        
        ArgValidator.checkFieldUriType(id, BackupPolicy.class, "id");
        BackupPolicy policy = _dbClient.queryObject(BackupPolicy.class, id);
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
    public BackupPolicy updateBackupPolicy(
        @PathParam("id") URI id,
        @PathParam("policyName") String name, 
        @PathParam("incrementalPeriodUnit") TimeUnit periodUnit, 
        @PathParam("incrementalPeriodValue") Long periodValue,
        @PathParam("countForFullBackup") Integer count) {
        
        // log input received.
        _log.info("Update backup policy request received {}", id);
        //String task = UUID.randomUUID().toString();
        ArgValidator.checkFieldUriType(id, BackupPolicy.class, "id");
        BackupPolicy policy = _dbClient.queryObject(BackupPolicy.class, id);
        
        policy.setPolicyName(name);
        policy.setIncrementalPeriodUnit(periodUnit);
        policy.setIncrementalPeriodValue(periodValue);
        policy.setCountForFullBackup(count);
        _dbClient.persistObject(policy);

        return policy;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        
        return ResourceTypeEnum.BACKUP_POLICY;
    }

}