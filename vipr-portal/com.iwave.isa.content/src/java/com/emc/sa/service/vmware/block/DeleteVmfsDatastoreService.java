/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.VolumeRestRep;
import com.google.common.collect.Maps;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-DeleteVmfsDatastore")
public class DeleteVmfsDatastoreService extends VMwareHostService {
    @Param(DATASTORE_NAME)
    protected List<String> datastoreNames;

    private Map<Datastore, List<VolumeRestRep>> datastores;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        datastores = Maps.newHashMap();
        acquireHostLock();
        for (String datastoreName : datastoreNames) {
            validateDatastoreVolume(datastoreName);
            Datastore datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
            vmware.verifyDatastoreForRemoval(datastore);
            List<VolumeRestRep> volumes = vmware.findVolumesBackingDatastore(host, datastore);
            datastores.put(datastore, volumes);
        }
    }

    @Override
    public void execute() throws Exception {

        for (Map.Entry<Datastore, List<VolumeRestRep>> entry : datastores.entrySet()) {
            vmware.deleteVmfsDatastore(entry.getValue(), hostId, entry.getKey(), false);
        }
        vmware.refreshStorage(host, cluster);
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
