/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.model.block.VolumeRestRep;
import com.google.common.collect.Maps;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-DeleteVmfsDatastoreAndVolume")
public class DeleteVmfsDatastoreAndVolumeService extends VMwareHostService {
    @Param(DATASTORE_NAME)
    protected List<String> datastoreNames;

    private Map<Datastore, List<VolumeRestRep>> datastores;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        datastores = Maps.newHashMap();
        acquireHostLock();
        for (String datastoreName : datastoreNames) {
            Datastore datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
            vmware.verifyDatastoreForRemoval(datastore);
            List<VolumeRestRep> volumes = vmware.findVolumesBackingDatastore(host, datastore);
            datastores.put(datastore, volumes);
        }
    }

    @Override
    public void execute() throws Exception {

        for (Map.Entry<Datastore, List<VolumeRestRep>> entry : datastores.entrySet()) {

            Datastore datastore = entry.getKey();
            List<VolumeRestRep> volumes = entry.getValue();

            vmware.deleteVmfsDatastore(volumes, hostId, datastore, true);

            if (!volumes.isEmpty()) {
                List<URI> volumeList = new ArrayList<URI>();
                for (VolumeRestRep volume : volumes) {
                    volumeList.add(volume.getId());
                }
                BlockStorageUtils.removeVolumes(volumeList);
            }
            else {
                logInfo("delete.vmfs.datastore.volume.not.found");
            }
        }

        vmware.refreshStorage(host, cluster);

        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }
}
