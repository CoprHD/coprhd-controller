/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util.builders;

import static com.emc.vipr.client.core.util.ResourceUtils.stringRefIds;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import models.PoolAssignmentTypes;
import models.StorageSystemTypes;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.pools.VirtualArrayAssignments;
import com.emc.storageos.model.vpool.ProtocolAssignments;
import com.emc.storageos.model.vpool.ProtocolChanges;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.storageos.model.vpool.VirtualPoolUpdateParam;
import com.google.common.collect.Sets;

public class VirtualPoolUpdateBuilder {
    private VirtualPoolCommonRestRep oldVirtualPool;
    private VirtualPoolUpdateParam virtualPool;

    public VirtualPoolUpdateBuilder(VirtualPoolCommonRestRep oldVirtualPool, VirtualPoolUpdateParam virtualPool) {
        this.oldVirtualPool = oldVirtualPool;
        this.virtualPool = virtualPool;
    }

    public VirtualPoolCommonRestRep getOldVirtualPool() {
        return oldVirtualPool;
    }

    public VirtualPoolUpdateParam getVirtualPoolUpdate() {
        return virtualPool;
    }

    public VirtualPoolUpdateBuilder setName(String name) {
        if (!StringUtils.equals(name, oldVirtualPool.getName())) {
            virtualPool.setName(name);
        }
        return this;
    }

    public VirtualPoolUpdateBuilder setDescription(String description) {
        if (!StringUtils.equals(description, oldVirtualPool.getDescription())) {
            virtualPool.setDescription(description);
        }
        return this;
    }

    public VirtualPoolUpdateBuilder setProvisioningType(String provisioningType) {
        if (!StringUtils.equals(provisioningType, oldVirtualPool.getProvisioningType())) {
            virtualPool.setProvisionType(provisioningType);
        }
        return this;
    }

    public VirtualPoolUpdateBuilder setSystemType(String systemType) {
        if (!StringUtils.equals(systemType, oldVirtualPool.getSystemType())) {
            virtualPool.setSystemType(StringUtils.defaultIfEmpty(systemType, StorageSystemTypes.NONE));
        }
        return this;
    }

    public VirtualPoolUpdateBuilder setUseMatchedPools(boolean useMatchedPools) {
        if (!ObjectUtils.equals(useMatchedPools, oldVirtualPool.getUseMatchedPools())) {
            virtualPool.setUseMatchedPools(useMatchedPools);
        }
        return this;
    }

    public VirtualPoolUpdateBuilder setPoolAssignmentType(String poolAssignmentType) {
        return setUseMatchedPools(PoolAssignmentTypes.isAutomatic(poolAssignmentType));
    }

    public VirtualPoolUpdateBuilder setVirtualArrays(Collection<String> virtualArrays) {
        List<String> oldVirtualArrays = stringRefIds(oldVirtualPool.getVirtualArrays());

        Set<String> add = Sets.newHashSet(CollectionUtils.subtract(virtualArrays, oldVirtualArrays));
        Set<String> remove = Sets.newHashSet(CollectionUtils.subtract(oldVirtualArrays, virtualArrays));

        VirtualArrayAssignmentChanges changes = new VirtualArrayAssignmentChanges();
        if (!add.isEmpty()) {
            changes.setAdd(new VirtualArrayAssignments(add));
        }
        if (!remove.isEmpty()) {
            changes.setRemove(new VirtualArrayAssignments(remove));
        }
        virtualPool.setVarrayChanges(changes);
        return this;
    }

    public VirtualPoolUpdateBuilder setProtocols(Collection<String> protocols) {
        Set<String> oldProtocols = oldVirtualPool.getProtocols();

        Set<String> add = Sets.newHashSet(CollectionUtils.subtract(protocols, oldProtocols));
        Set<String> remove = Sets.newHashSet(CollectionUtils.subtract(oldProtocols, protocols));

        ProtocolChanges changes = new ProtocolChanges();
        if (!add.isEmpty()) {
            changes.setAdd(new ProtocolAssignments(add));
        }
        if (!remove.isEmpty()) {
            changes.setRemove(new ProtocolAssignments(remove));
        }
        virtualPool.setProtocolChanges(changes);
        return this;
    }
    
    protected URI defaultURI(URI uri) {
        if (uri != null) {
            return uri;
        }
        return URI.create("");
    }
}
