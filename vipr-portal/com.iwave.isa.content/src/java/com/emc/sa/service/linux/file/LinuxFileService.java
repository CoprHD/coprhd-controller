/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.linux.LinuxUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.db.client.model.Host;
import com.iwave.ext.linux.LinuxSystemCLI;

public abstract class LinuxFileService extends ViPRService {
    @Param(ServiceParams.HOST)
    protected URI hostId;

    protected Host host;

    protected LinuxSystemCLI linuxSystem;

    protected void initHost() {

        host = getModelClient().hosts().findById(hostId);
        if (host == null) {
            throw new IllegalArgumentException("Host " + hostId + " not found");
        }

        logInfo("linux.service.target.host", host.getLabel());

        linuxSystem = LinuxUtils.convertHost(host);
    }

    @Override
    public void init() throws Exception {
        super.init();
        initHost();
    }

    protected void acquireHostsLock() {
        acquireHostLock(host, null);
    }
}
