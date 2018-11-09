/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ArtificialFailures;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-ExpandVmfsDatastore")
public class ExpandVmfsDatastoreService extends VMwareHostService {
    /*
     * NOTE: there is not proper provider support for filtering mounted volumes by datastore.
     */
    @Param(DATASTORE_NAME)
    protected String datastoreName;
    @Param(VOLUMES)
    protected URI volumeId;
    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    private Datastore datastore;
    private BlockObjectRestRep volume;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        acquireHostLock();
        volume = BlockStorageUtils.getVolume(volumeId);
        if (BlockStorageUtils.isViprVolumeExpanded(volume, sizeInGb)) {
            ExecutionUtils.fail("expand.vmfs.datastore.fail", new Object[] {}, volume.getName(), BlockStorageUtils.getCapacity(volume));
        }
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);

        vmware.verifyVolumesBackingDatastore(host, hostId, datastore);

        vmware.disconnect();
    }

    @Override
    public void execute() throws Exception {

        // Skip the expand if the current volume capacity is larger than the requested expand size
        if (BlockStorageUtils.isVolumeExpanded(volume, sizeInGb)) {
                logWarn("expand.vmfs.datastore.skip", volumeId, BlockStorageUtils.getCapacity(volume));
        } else {
                BlockStorageUtils.expandVolume(volumeId, sizeInGb);
        }

        connectAndInitializeHost();
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
        vmware.refreshStorage(host, cluster);
        artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_VMWARE_EXPAND_DATASTORE);
        vmware.expandVmfsDatastore(host, cluster, hostId, volume, datastore);
        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }

    @Override
    public boolean checkClusterConnectivity() {
        return false;
    }
}
