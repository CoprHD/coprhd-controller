/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.util.KerberosUtil;
import com.google.common.collect.Lists;
import com.iwave.ext.windows.WindowsSystemWinRM;

public abstract class WindowsService extends ViPRService {
    @Param(ServiceParams.HOST)
    protected URI hostId;

    private Host host;
    protected List<Host> hosts;
    protected Cluster cluster;
    protected List<WindowsSystemWinRM> windowsSystems;

    private void initializeKerberos() {
        List<AuthnProvider> authProviders = getModelClient().of(AuthnProvider.class).findAll();
        KerberosUtil.initializeKerberos(authProviders);
    }

    private void initHost() {
        hosts = Lists.newArrayList();

        if (BlockStorageUtils.isHost(hostId)) {
            host = getModelClient().hosts().findById(hostId);
            if (host == null) {
                ExecutionUtils.fail("failTask.WindowsService.hostNotFound", hostId, hostId);
            }

            hosts.add(host);
            logInfo("win.service.target.host", host.getLabel());
        }
        else {
            cluster = getModelClient().clusters().findById(hostId);
            if (cluster == null) {
                ExecutionUtils.fail("failTask.WindowsService.clusterNotFound", hostId, hostId);
            }

            hosts.addAll(getModelClient().hosts().findByCluster(hostId));
            logInfo("win.service.target.cluster", cluster.getLabel(), hosts.size());
        }
        windowsSystems = Lists.newArrayList();
        for (Host mhost : hosts) {
            if (mhost == null) {
                ExecutionUtils.fail("failTask.WindowsService.hostNotFound", hostId, hostId);
            }
            windowsSystems.add(WindowsUtils.createWindowsSystem(mhost, cluster));
        }
    }

    @Override
    public void init() throws Exception {
        super.init();
        initializeKerberos();
        initHost();
    }

    protected void acquireHostAndClusterLock() {
        for (Host host : hosts) {
            acquireHostLock(host, cluster);
        }
    }

    protected boolean isClustered() {
        return cluster != null;
    }
}
