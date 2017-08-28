/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.DELETION_TYPE;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-RemoveBlockVolume")
public class RemoveBlockVolumeService extends VMwareHostService {

    @Param(HOST)
    protected URI hostId;

    @Param(VOLUMES)
    protected List<String> volumeIds;

    @Param(DELETION_TYPE)
    protected VolumeDeleteTypeEnum deletionType;

    protected List<BlockObjectRestRep> volumes;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
    }

    @Override
    public void execute() {
        for (BlockObjectRestRep volume : volumes) {
            String datastoreName = KnownMachineTags.getBlockVolumeVMFSDatastore(hostId, volume);
            if (!StringUtils.isEmpty(datastoreName)) {
                Datastore datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
                if (datastore != null) {
                    vmware.unmountVmfsDatastore(host, cluster, datastore);
                }
            }
        }

        for (BlockObjectRestRep volume : volumes) {
            vmware.detachLuns(host, cluster, volume);
        }

        vmware.disconnect();

        BlockStorageUtils.removeBlockResources(uris(volumeIds), deletionType);

        connectAndInitializeHost();

        vmware.refreshStorage(host, cluster);

        // form is always passing hostId, never clusterId - need to figure out which it is.
        String hostOrClusterId = BlockStorageUtils.getHostOrClusterId(hostId);
        if (hostOrClusterId != null) {
            ExecutionUtils.addAffectedResource(hostOrClusterId.toString());
        }
    }

}
