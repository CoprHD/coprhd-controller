package com.emc.storageos.api.service.impl.resource;


import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.plugins.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.RemoteReplicationScheduler;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;

public class RemoteReplicationBlockServiceApiImpl extends AbstractBlockServiceApiImpl<RemoteReplicationScheduler> {
    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationBlockServiceApiImpl.class);

    public RemoteReplicationBlockServiceApiImpl() {
        super(Constants.REMOTE_REPLICATION);
    }

    @Override
    public List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI, List<URI> volumeURIs, String deletionType) {
        return null;
    }

    @Override
    protected List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperations(Volume volume, VirtualPool currentVpool, VirtualPool newVpool, StringBuffer notSuppReasonBuff) {
        return null;
    }

    @Override
    public Collection<? extends String> getReplicationGroupNames(VolumeGroup group) {
        return null;
    }
}
