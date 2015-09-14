/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

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
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/vdc/auto-tier-policies")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN })
public class AutoTieringService extends TaggedResource {

    /**
     * Gets the AutoTier Policy with the passed id from the database.
     * 
     * @param id the URN of a ViPR auto tier policy
     * 
     * @return A reference to the registered Policy.
     */
    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        AutoTieringPolicy autoTierPolicy = _dbClient.queryObject(AutoTieringPolicy.class, id);
        ArgValidator.checkEntityNotNull(autoTierPolicy, id, isIdEmbeddedInURL(id));
        return autoTierPolicy;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Show the specified auto tiering policy.
     * 
     * @param id the URN of a ViPR auto tier policy
     * @prereq none
     * @brief Show the details of the specified auto tiering policy
     * @return Policy Object
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}")
    public AutoTieringPolicyRestRep getAutoTierPolicy(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, AutoTieringPolicy.class, "id");
        AutoTieringPolicy policy = _dbClient.queryObject(AutoTieringPolicy.class, id);
        ArgValidator.checkEntityNotNull(policy, id, isIdEmbeddedInURL(id));
        return map(policy);
    }

    /**
     * 
     * @param provisionType The provisioning type associated with this policy [Thin,Thick or All]
     * @param uniquePolicyNames If unique_auto_tier_policy_names is set to true, then unique auto tier policy Names alone without any
     *            storage system details will be returned,
     *            even if the same policy exists in multiple arrays. If unique_auto_tier_policy_names is set to false, then duplicate policy
     *            names, with the storage system details, are returned
     * 
     * @prereq none
     * @brief List all auto tier policies
     * @return AutoTierPolicyList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public AutoTierPolicyList getAutoTierPolicies(@QueryParam("provisioning_type") String provisionType,
            @QueryParam("unique_auto_tier_policy_names") Boolean uniquePolicyNames) {
        if (null == uniquePolicyNames) {
            uniquePolicyNames = false;
        }
        AutoTierPolicyList policyList = new AutoTierPolicyList();
        List<URI> policyUris = _dbClient.queryByType(AutoTieringPolicy.class, true);
        List<AutoTieringPolicy> policies = _dbClient.queryObject(AutoTieringPolicy.class, policyUris);
        for (AutoTieringPolicy policy : policies) {
            if (!doesGivenProvisionTypeMatchAutoTierPolicy(provisionType, policy)) {
                continue;
            }
            BlockMapper.addAutoTierPolicy(policy, policyList, uniquePolicyNames);
        }
        return policyList;
    }

    /**
     * Show the storage tiers associated with a specific auto tiering policy
     * Only auto tiering policies belonging to VMAX systems have direct association to tiers.
     * 
     * @param id the URN of a ViPR auto tier policy
     * 
     * @prereq none
     * @brief List storage tiers for auto tiering policy
     * @return Policy Object
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}/storage-tiers")
    public StorageTierList getStorageTiersForGivenPolicy(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, AutoTieringPolicy.class, "id");
        AutoTieringPolicy policy = _dbClient.queryObject(AutoTieringPolicy.class, id);
        ArgValidator.checkEntityNotNull(policy, id, isIdEmbeddedInURL(id));
        StorageTierList storageTierList = new StorageTierList();
        List<URI> tierUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStorageTierFASTPolicyConstraint(policy.getId().toString()));
        List<StorageTier> tiers = _dbClient.queryObject(StorageTier.class, tierUris);
        for (StorageTier tier : tiers) {
            storageTierList.getStorageTiers().add(toNamedRelatedResource(tier, tier.getNativeGuid()));
        }
        return storageTierList;
    }

    /**
     * Retrieve data of auto tier policies based on input ids.
     * 
     * @param param POST data containing the id list.
     * 
     * @prereq none
     * @brief List data of auto tier policies.
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public AutoTieringPolicyBulkRep getBulkResources(BulkIdParam param) {
        return (AutoTieringPolicyBulkRep) super.getBulkResources(param);
    }

    @Override
    public AutoTieringPolicyBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<AutoTieringPolicy> dbIterator = _dbClient.queryIterativeObjects(
                AutoTieringPolicy.class, ids);
        return new AutoTieringPolicyBulkRep(BulkList.wrapping(dbIterator, MapAutoTierPolicy.getInstance()));
    }

    @Override
    public AutoTieringPolicyBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<AutoTieringPolicy> getResourceClass() {
        return AutoTieringPolicy.class;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.AUTO_TIERING_POLICY;
    }

    private boolean doesGivenProvisionTypeMatchAutoTierPolicy(
            String provisioningType, AutoTieringPolicy policy) {
        if (null == provisioningType || provisioningType.isEmpty()) {
            return true;
        }
        // for vnx case, all Policies will be set to ALL
        if (AutoTieringPolicy.ProvisioningType.All.toString().equalsIgnoreCase(
                policy.getProvisioningType())) {
            return true;
        }

        if (provisioningType.equalsIgnoreCase(VirtualPool.ProvisioningType.Thick.toString())
                && AutoTieringPolicy.ProvisioningType.ThicklyProvisioned.toString()
                        .equalsIgnoreCase(policy.getProvisioningType())) {
            return true;
        }
        if (provisioningType.equalsIgnoreCase(VirtualPool.ProvisioningType.Thin.toString())
                && AutoTieringPolicy.ProvisioningType.ThinlyProvisioned.toString()
                        .equalsIgnoreCase(policy.getProvisioningType())) {
            return true;
        }
        return false;
    }

}
