/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.HostScsiDisk;
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

    private BlockObjectRestRep volume;
    private Datastore datastore;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        volume = BlockStorageUtils.getVolume(volumeId);
        acquireHostLock();
        validateDatastoreVolume(datastoreName);
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
    }

    @Override
    public void execute() throws Exception {
        BlockStorageUtils.expandVolume(volumeId, sizeInGb);
        vmware.refreshStorage(host, cluster);
        vmware.expandVmfsDatastore(host, cluster, hostId, volume, datastore);
        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }
    
    /**
     * Validates the volume associated to vCenter datastore has any pending events due to external change
     */
    protected void validateDatastoreVolume(String datastoreName) {
        List<HostScsiDisk> disks = new HostStorageAPI(host).listScsiDisks();
        for (HostScsiDisk entry : disks) {
            VolumeRestRep volRep = BlockStorageUtils.getVolumeByWWN(VMwareUtils.getDiskWwn(entry));
            if(volRep != null && BlockStorageUtils.isVolumeVMFSDatastore(volRep)){
                Set<String> tagSet = volRep.getTags();
                for (String tag : tagSet) {
                    if (tag.contains(datastoreName)) {
                        Volume volumeObj = getModelClient().findById(Volume.class, volRep.getId());
                        BlockStorageUtils.checkEvents(volumeObj);
                    }
                }
            }
        }
    }
}
