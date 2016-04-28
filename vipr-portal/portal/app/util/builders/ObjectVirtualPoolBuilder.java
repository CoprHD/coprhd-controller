/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.builders;

import com.emc.storageos.model.vpool.ObjectVirtualPoolParam;

public class ObjectVirtualPoolBuilder extends VirtualPoolBuilder {
    private ObjectVirtualPoolParam virtualPool;

    public ObjectVirtualPoolBuilder() {
        this(new ObjectVirtualPoolParam());
    }

    protected ObjectVirtualPoolBuilder(ObjectVirtualPoolParam virtualPool) {
        super(virtualPool);
        this.virtualPool = virtualPool;
    }

    @Override
    public ObjectVirtualPoolParam getVirtualPool() {
        return virtualPool;
    }

    public ObjectVirtualPoolBuilder setMaxRetention(Integer maxRetention) {
        virtualPool.setMaxRetention(maxRetention);
        return this;
    }
    
    public ObjectVirtualPoolBuilder setMinDataCenters(Integer minDataCenters) {
        virtualPool.setMinDataCenters(minDataCenters);
        return this;
    }
}
