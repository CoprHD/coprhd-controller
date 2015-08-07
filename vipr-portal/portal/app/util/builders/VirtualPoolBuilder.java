/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.builders;

import java.util.Collection;
import models.PoolAssignmentTypes;
import models.StorageSystemTypes;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.vpool.VirtualPoolCommonParam;
import com.google.common.collect.Sets;

public class VirtualPoolBuilder {
    private VirtualPoolCommonParam virtualPool;

    public VirtualPoolBuilder(VirtualPoolCommonParam virtualPool) {
        this.virtualPool = virtualPool;
    }

    public VirtualPoolCommonParam getVirtualPool() {
        return virtualPool;
    }

    public VirtualPoolBuilder setName(String name) {
        virtualPool.setName(StringUtils.trimToNull(name));
        return this;
    }

    public VirtualPoolBuilder setDescription(String description) {
        virtualPool.setDescription(StringUtils.trimToNull(description));
        return this;
    }

    public VirtualPoolBuilder setProvisioningType(String provisioningType) {
        virtualPool.setProvisionType(provisioningType);
        return this;
    }

    public VirtualPoolBuilder setSystemType(String systemType) {
        virtualPool.setSystemType(StringUtils.defaultIfEmpty(systemType, StorageSystemTypes.NONE));
        return this;
    }

    public VirtualPoolBuilder setUseMatchedPools(boolean useMatchedPools) {
        virtualPool.setUseMatchedPools(useMatchedPools);
        return this;
    }

    public VirtualPoolBuilder setPoolAssignmentType(String poolAssignmentType) {
        return setUseMatchedPools(PoolAssignmentTypes.isAutomatic(poolAssignmentType));
    }

    public VirtualPoolBuilder setVirtualArrays(Collection<String> virtualArrays) {
        if (virtualArrays != null) {
            virtualPool.setVarrays(Sets.newHashSet(virtualArrays));
        }
        else {
            virtualPool.setVarrays(Sets.<String> newHashSet());
        }
        return this;
    }

    public VirtualPoolBuilder setProtocols(Collection<String> protocols) {
        if (protocols != null) {
            virtualPool.setProtocols(Sets.newHashSet(protocols));
        }
        else {
            virtualPool.setProtocols(Sets.<String> newHashSet());
        }
        return this;
    }
}
