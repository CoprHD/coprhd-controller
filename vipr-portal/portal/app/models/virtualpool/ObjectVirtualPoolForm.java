/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.virtualpool;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static util.BourneUtil.getViprClient;

import java.util.List;
import java.util.Set;

import jobs.vipr.ConnectedFileVirtualPoolsCall;
import jobs.vipr.MatchingFileStoragePoolsCall;
import jobs.vipr.MatchingObjectStoragePoolsCall;
import play.Logger;
import play.data.validation.Min;
import play.data.validation.Required;
import util.VirtualPoolUtils;
import util.builders.ObjectVirtualPoolBuilder;
import util.builders.ObjectVirtualPoolUpdateBuilder;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.emc.vipr.client.core.ObjectVirtualPools;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Sets;

public class ObjectVirtualPoolForm extends VirtualPoolCommonForm<ObjectVirtualPoolRestRep> {
    @Min(0)
    public Integer maxRetention;
    
    @Min(0)
    public Integer minDataCenters;

    @Required
    public Set<String> objectProtocols;
    
    @Override
    public void load(ObjectVirtualPoolRestRep virtualPool) {
        doLoad(virtualPool);
        ObjectVirtualPools vpools = getViprClient().objectVpools();
        loadTenantACLs(vpools);
    }

    protected void doLoad(ObjectVirtualPoolRestRep virtualPool) {
        loadCommon(virtualPool);
        maxRetention = virtualPool.getMaxRetention();
        minDataCenters = virtualPool.getMinDataCenters();
        objectProtocols = Sets.newHashSet(virtualPool.getProtocols());
        
    }

    @Override
    public ObjectVirtualPoolRestRep save() {
    	ObjectVirtualPoolRestRep virtualPool = doSave();
        ObjectVirtualPools vpools = getViprClient().objectVpools();
        saveTenantACLs(vpools);
        return virtualPool;
    }

    protected ObjectVirtualPoolRestRep doSave() {
    	ObjectVirtualPoolRestRep virtualPool;
        if (isNew()) {
            ObjectVirtualPoolBuilder builder = apply(new ObjectVirtualPoolBuilder());
            virtualPool = VirtualPoolUtils.create(builder.getVirtualPool());
            this.id = ResourceUtils.stringId(virtualPool);
        }
        else {
            ObjectVirtualPoolRestRep oldVirtualPool = VirtualPoolUtils.getObjectVirtualPool(id);
            ObjectVirtualPoolUpdateBuilder builder = apply(new ObjectVirtualPoolUpdateBuilder(oldVirtualPool));
            virtualPool = VirtualPoolUtils.update(id, builder.getVirtualPoolUpdate());
            List<NamedRelatedResourceRep> matchingPools = VirtualPoolUtils.refreshMatchingPools(virtualPool);
            Logger.info("Refreshed File Virtual Pool '%s' matching pools: %d", virtualPool.getName(), matchingPools.size());
        }
        virtualPool = saveStoragePools(virtualPool);
        return virtualPool;
    }

    private ObjectVirtualPoolBuilder apply(ObjectVirtualPoolBuilder builder) {
        applyCommon(builder);
        builder.setMaxRetention(maxRetention);
        builder.setMinDataCenters(minDataCenters);
        return builder;
    }

    private ObjectVirtualPoolUpdateBuilder apply(ObjectVirtualPoolUpdateBuilder builder) {
        applyCommon(builder);
        builder.setMaxRetention(maxRetention);
        builder.setMinDataCenters(minDataCenters);
        return builder;
    }

    @Override
    protected ObjectVirtualPoolRestRep updateStoragePools(Set<String> add, Set<String> remove) {
        return VirtualPoolUtils.updateAssignedObjectPools(id, add, remove);
    }

    public MatchingObjectStoragePoolsCall matchingStoragePools() {
    	ObjectVirtualPoolBuilder builder = new ObjectVirtualPoolBuilder();
        apply(builder);
        builder.setUseMatchedPools(true);
        return new MatchingObjectStoragePoolsCall(builder.getVirtualPool());
    }

    public ConnectedFileVirtualPoolsCall connectedVirtualPools() {
        return new ConnectedFileVirtualPoolsCall(uris(virtualArrays));
    }
}
