/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.service.vipr.block.ExportVMwareBlockVolumeHelper;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.HostsUpdateParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.block.export.VolumeUpdateParam;
import com.emc.vipr.client.Task;

public class AddHostAndVolumeToExportNoWait extends WaitForTask<ExportGroupRestRep> {

    private final URI exportId;
    private final URI hostId;
    private final URI volumeId;
    private final Integer hlu;
    
    public AddHostAndVolumeToExportNoWait(URI exportId, URI hostId, URI volumeId, Integer hlu) {
        super();
        this.exportId = exportId;
        this.hostId = hostId;
        this.volumeId = volumeId;
        this.hlu = hlu;
        provideDetailArgs(exportId, hostId);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();
        List<VolumeParam> volumes = new ArrayList<VolumeParam>();
        Integer currentHlu = hlu;
        VolumeParam volume = new VolumeParam(volumeId);
        if (currentHlu != null) {
            if (currentHlu.equals(ExportVMwareBlockVolumeHelper.USE_EXISTING_HLU) && hlu != null) {
                if (hlu == null) {
                    volume.setLun(-1);
                } else {
                    volume.setLun(hlu);
                }
            } else {
                volume.setLun(currentHlu);
            }
        }
        if ((currentHlu != null) && (currentHlu > -1)) {
            currentHlu++;
        }
        volumes.add(volume);
        exportUpdateParam.setVolumes(new VolumeUpdateParam(volumes, new ArrayList<URI>()));
        exportUpdateParam.setHosts(new HostsUpdateParam());
        exportUpdateParam.getHosts().getAdd().add(hostId);

        return getClient().blockExports().update(exportId, exportUpdateParam);
    }

}
