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

    private BlockObjectRestRep volume;
    private Datastore datastore;

    @Override
    public void precheck() throws Exception {
        StringBuilder preCheckErrors = new StringBuilder();

        super.precheck();
        volume = BlockStorageUtils.getVolume(volumeId);
        acquireHostLock();
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);

        // If no volumes were found (or not all the volumes were found in our DB), indicate an error
        if (vmware.findVolumesBackingDatastore(host, hostId, datastore) == null) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("expand.vmfs.datastore.notsamewwn", datastoreName) + " ");
        }

        vmware.disconnect();

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString());
        }
    }

    @Override
    public void execute() throws Exception {
        BlockStorageUtils.expandVolume(volumeId, sizeInGb);
        connectAndInitializeHost();
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
        vmware.refreshStorage(host, cluster);
        vmware.expandVmfsDatastore(host, cluster, hostId, volume, datastore);
        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }
}
