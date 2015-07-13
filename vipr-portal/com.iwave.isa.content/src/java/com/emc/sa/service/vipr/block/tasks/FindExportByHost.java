/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;

import java.net.URI;
import java.util.List;

public class FindExportByHost extends ViPRExecutionTask<ExportGroupRestRep> {
    private URI host;
    private URI project;
    private URI varray;
    private URI volume;

    public FindExportByHost(String host, String project, String varray, String volume) {
        this(uri(host), uri(project), uri(varray), uri(volume));
    }

    public FindExportByHost(URI host, URI project, URI varray, URI volume) {
        this.host =  host;
        this.project = project;
        this.varray = varray;
        this.volume = volume;
        provideDetailArgs(host, project, varray);
    }

    @Override
    public ExportGroupRestRep executeTask() throws Exception {
        List<ExportGroupRestRep> exports = getClient().blockExports().findByHost(host, project, varray);
        if (volume != null) {
            for (ExportGroupRestRep export : exports) {
                if (BlockStorageUtils.isVolumeInExportGroup(export, volume)) {
                    return export;
                }
            }
        }
        return exports.isEmpty() ? null : exports.get(0);
    }
}
