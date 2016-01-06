/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.builders;

import org.apache.commons.lang.ObjectUtils;

import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam;

public class ObjectVirtualPoolUpdateBuilder extends VirtualPoolUpdateBuilder {
    private ObjectVirtualPoolRestRep oldVirtualPool;
    private ObjectVirtualPoolUpdateParam virtualPool;

    public ObjectVirtualPoolUpdateBuilder(ObjectVirtualPoolRestRep oldVirtualPool) {
        this(oldVirtualPool, new ObjectVirtualPoolUpdateParam());
    }

    protected ObjectVirtualPoolUpdateBuilder(ObjectVirtualPoolRestRep oldVirtualPool, ObjectVirtualPoolUpdateParam virtualPool) {
        super(oldVirtualPool, virtualPool);
        this.oldVirtualPool = oldVirtualPool;
        this.virtualPool = virtualPool;
    }

    @Override
    public ObjectVirtualPoolRestRep getOldVirtualPool() {
        return oldVirtualPool;
    }

    @Override
    public ObjectVirtualPoolUpdateParam getVirtualPoolUpdate() {
        return virtualPool;
    }

    public ObjectVirtualPoolUpdateBuilder setMaxRetention(Integer maxRetention) {
        if (!ObjectUtils.equals(maxRetention, oldVirtualPool.getMaxRetention())) {
            getVirtualPoolUpdate().setMaxRetention(maxRetention);
        }
        return this;
    }
    
    public ObjectVirtualPoolUpdateBuilder setMinDataCenters(Integer minDataCenters) {
        if (!ObjectUtils.equals(minDataCenters, oldVirtualPool.getMinDataCenters())) {
            getVirtualPoolUpdate().setMinDataCenters(minDataCenters);
        }
        return this;
    }
}
