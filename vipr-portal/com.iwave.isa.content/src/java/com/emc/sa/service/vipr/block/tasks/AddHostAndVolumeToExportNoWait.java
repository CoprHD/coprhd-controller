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
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
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
    private final URI portGroup;
    
    public AddHostAndVolumeToExportNoWait(URI exportId, URI hostId, URI volumeId, Integer hlu, URI portGroup) {
        super();
        this.exportId = exportId;
        this.hostId = hostId;
        this.volumeId = volumeId;
        this.hlu = hlu;
        this.portGroup = portGroup;
        provideDetailArgs(exportId, hostId);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();

        List<VolumeParam> volumes = new ArrayList<VolumeParam>();
        if (!NullColumnValueGetter.isNullURI(volumeId)) {
            VolumeParam volume = new VolumeParam(volumeId);
            volume.setLun(-1);
            if (hlu != null && !hlu.equals(ExportVMwareBlockVolumeHelper.USE_EXISTING_HLU)) {
                volume.setLun(hlu);
            }

            volumes.add(volume);
            exportUpdateParam.setVolumes(new VolumeUpdateParam(volumes, new ArrayList<URI>()));
        }

        if (!NullColumnValueGetter.isNullURI(hostId)) {
            exportUpdateParam.setHosts(new HostsUpdateParam());
            exportUpdateParam.getHosts().getAdd().add(hostId);
        }

        if (!NullColumnValueGetter.isNullURI(portGroup)) {
            ExportPathParameters exportPathParameters = new ExportPathParameters();
            exportPathParameters.setPortGroup(portGroup);
            exportUpdateParam.setExportPathParameters(exportPathParameters);
        }
        return getClient().blockExports().update(exportId, exportUpdateParam);
    }

}
