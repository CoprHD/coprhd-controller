/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.virtualpool;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.DriveTypes;
import models.PoolAssignmentTypes;
import models.StorageSystemTypes;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Required;
import play.data.validation.Validation;
import util.TenantUtils;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.builders.ACLUpdateBuilder;
import util.builders.VirtualPoolBuilder;
import util.builders.VirtualPoolUpdateBuilder;

import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.vipr.client.core.ACLResources;
import com.emc.vipr.client.core.QuotaResources;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Base form for file/block virtual pools.
 */
public abstract class VirtualPoolCommonForm<T extends VirtualPoolCommonRestRep> extends BaseVirtualPoolForm<T> {
    // Indicates if there are resources associated to the pool (which affects what can be changed)
    public Integer numResources;

    public String poolType;

    @Required
    public String provisioningType;

    @Required
    public List<String> virtualArrays;

    @Required
    public Set<String> protocols;

    @Required
    public String systemType;

    public boolean enableQuota;

    public Long quota;

    public String poolAssignment;

    public List<String> storagePools;

    public boolean enableTenants;

    public List<String> tenants;

    public boolean isLocked() {
        return (numResources != null) && (numResources > 0);
    }

    public boolean isManualPoolAssignment() {
        return PoolAssignmentTypes.MANUAL.equals(poolAssignment);
    }

    @Override
    public void validate(String formName) {
        super.validate(formName);
        if (enableQuota) {
            Validation.min(formName + ".quota", quota, 1);
        }
        if (isManualPoolAssignment()) {
            Validation.required(String.format("%s.storagePools", formName), storagePools);
        }
        if (enableTenants) {
            Validation.required(String.format("%s.tenants", formName), tenants);
        }
    }

    /**
     * Applies the common fields to a virtual pool builder. This is used when creating a new pool or when retrieving
     * matching pools.
     * 
     * @param builder
     *        the virtual pool builder.
     */
    protected void applyCommon(VirtualPoolBuilder builder) {
        builder.setName(name);
        builder.setDescription(description);
        builder.setVirtualArrays(virtualArrays);
        builder.setProvisioningType(provisioningType);
        builder.setPoolAssignmentType(poolAssignment);
        builder.setSystemType(systemType);
        builder.setProtocols(protocols);
    }

    /**
     * Applies the common fields on a virtual pool update builder.
     * 
     * @param builder
     *        the virtual pool builder.
     */
    protected void applyCommon(VirtualPoolUpdateBuilder builder) {
        builder.setName(name);
        builder.setDescription(description);
        builder.setPoolAssignmentType(poolAssignment);
        builder.setVirtualArrays(defaultList(virtualArrays));

        // Cannot update certain fields if locked
        if (!isLocked()) {
            builder.setProvisioningType(provisioningType);
            builder.setSystemType(systemType);
            builder.setProtocols(defaultSet(protocols));
        }
    }

    /**
     * Loads the common fields from a virtual pool.
     * 
     * @param virtualPool
     *        the virtual pool.
     */
    protected void loadCommon(VirtualPoolCommonRestRep virtualPool) {
        id = ResourceUtils.stringId(virtualPool);
        name = virtualPool.getName();
        description = virtualPool.getDescription();
        poolType = virtualPool.getType();
        numResources = virtualPool.getNumResources();
        provisioningType = virtualPool.getProvisioningType();
        virtualArrays = ResourceUtils.stringRefIds(virtualPool.getVirtualArrays());
        protocols = defaultSet(virtualPool.getProtocols());
        systemType = virtualPool.getSystemType();
        poolAssignment = isTrue(virtualPool.getUseMatchedPools()) ? PoolAssignmentTypes.AUTOMATIC
                : PoolAssignmentTypes.MANUAL;
        storagePools = ResourceUtils.stringRefIds(virtualPool.getAssignedStoragePools());
    }

    /**
     * Loads the quota information from the provide QuotaResources (either the block or file virtual pool resources).
     * 
     * @param resources
     *        the resources from which to load the quota.
     */
    protected void loadQuota(QuotaResources resources) {
        URI virtualPoolId = ResourceUtils.uri(id);
        if (virtualPoolId != null) {
            QuotaInfo quotaInfo = resources.getQuota(virtualPoolId);
            if (quotaInfo != null) {
                enableQuota = quotaInfo.getEnabled();
                quota = quotaInfo.getQuotaInGb();
            }
        }
    }

    /**
     * Saves the quota value using the provided QuotaResources (either the block or file virtual pool resources).
     * 
     * @param resources
     *        the resources on which to save the quota.
     */
    protected void saveQuota(QuotaResources resources) {
        URI virtualPoolId = ResourceUtils.uri(id);
        if (virtualPoolId != null) {
            QuotaUpdateParam update = new QuotaUpdateParam(isTrue(enableQuota), quota);
            resources.updateQuota(virtualPoolId, update);
        }
    }

    /**
     * Loads the tenant ACL information from the provided ACLResources.
     * 
     * @param resources
     *        the resources from which to load the ACLs.
     */
    protected void loadTenantACLs(ACLResources resources) {
        tenants = Lists.newArrayList();

        URI virtualPoolId = ResourceUtils.uri(id);
        if (virtualPoolId != null) {
            for (ACLEntry acl : resources.getACLs(virtualPoolId)) {
                if (StringUtils.isNotBlank(acl.getTenant())) {
                    tenants.add(acl.getTenant());
                }
            }
        }

        enableTenants = tenants.size() > 0;
    }

    /**
     * Saves the tenant ACL information using the provided ACLResources.
     * 
     * @param resources
     *        the resources on which to save the tenant ACLs.
     */
    protected void saveTenantACLs(ACLResources resources) {
        // Only allow a user than can read all tenants and update ACLs do this
        if (TenantUtils.canReadAllTenants() && VirtualPoolUtils.canUpdateACLs()) {
            URI virtualPoolId = ResourceUtils.uri(id);
            if (virtualPoolId != null) {
                Set<String> tenantIds = Sets.newHashSet();
                if (isTrue(enableTenants) && (tenants != null)) {
                    tenantIds.addAll(tenants);
                }
                ACLUpdateBuilder builder = new ACLUpdateBuilder(resources.getACLs(virtualPoolId));
                builder.setTenants(tenantIds);
                resources.updateACLs(virtualPoolId, builder.getACLUpdate());
            }
        }
    }

    /**
     * Saves the storage pools associated with the given pool.
     * 
     * @param pool
     *        the virtual pool.
     */
    protected T saveStoragePools(T pool) {
        Set<String> oldValues = Sets.newHashSet(ResourceUtils.stringRefIds(pool.getAssignedStoragePools()));
        Set<String> newValues = Sets.newHashSet();
        if (isFalse(pool.getUseMatchedPools())) {
            if (storagePools != null) {
                newValues.addAll(storagePools);
            }
        }

        Set<String> add = Sets.difference(newValues, oldValues);
        Set<String> remove = Sets.difference(oldValues, newValues);
        // Don't bother updating if there is nothing to add/remove
        if (!add.isEmpty() || !remove.isEmpty()) {
            pool = updateStoragePools(add, remove);
        }
        return pool;
    }

    /**
     * Updates the storage pools assigned to this virtual pool.
     * 
     * @param add
     *        the pools to add.
     * @param remove
     *        the pools to remove.
     * @return the updated virtual pool.
     */
    protected abstract T updateStoragePools(Set<String> add, Set<String> remove);

    /**
     * Gets the virtual pool attributes based on the selected virtual arrays.
     * 
     * @return the map of virtual pool attributes.
     */
    public Map<String, Set<String>> getVirtualPoolAttributes() {
        Map<String, Set<String>> allAttributes = VirtualArrayUtils.getAvailableAttributes(uris(virtualArrays));

        Map<String, Set<String>> attributes = Maps.newHashMap();
        attributes.put("driveType", Sets.newHashSet(allAttributes.get(VirtualArrayUtils.ATTRIBUTE_DRIVE_TYPES)));
        attributes.get("driveType").add(DriveTypes.NONE);
        attributes.put("protocols", Sets.newHashSet(allAttributes.get(VirtualArrayUtils.ATTRIBUTE_PROTOCOLS)));
        attributes.put("raidLevels", Sets.newHashSet(allAttributes.get(VirtualArrayUtils.ATTRIBUTE_RAID_LEVELS)));
        attributes.put("systemType", Sets.newHashSet(allAttributes.get(VirtualArrayUtils.ATTRIBUTE_SYSTEM_TYPES)));
        attributes.get("systemType").add(StorageSystemTypes.NONE);

        return attributes;
    }

    protected static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    protected static boolean isFalse(Boolean value) {
        return Boolean.FALSE.equals(value);
    }

    protected static <T> Set<T> defaultSet(Set<T> set) {
        if (set == null) {
            set = Sets.newHashSet();
        }
        return set;
    }
}
