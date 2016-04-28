/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import java.net.URI;
import java.util.List;

import com.emc.hpux.HpuxSystem;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.google.common.collect.Lists;

public abstract class HpuxService extends ViPRService {

    @Param(ServiceParams.HOST)
    protected URI hostId;

    protected Host host;

    protected List<Host> hosts;

    protected List<Initiator> hostPorts = Lists.newArrayList();

    protected HpuxSystem hpuxSystem;

    public HpuxSystem getHpuxSystem() {
        return hpuxSystem;
    }

    protected void initHost() {
        if (BlockStorageUtils.isCluster(hostId)) {
            throw new IllegalStateException("Hpux Services do not support clustering");
        }

        host = getModelClient().hosts().findById(hostId);
        if (host == null) {
            throw new IllegalArgumentException("Host " + hostId + " not found");
        }

        hosts = Lists.newArrayList();
        logInfo("hpux.service.target.host", host.getLabel());

        hostPorts = getModelClient().initiators().findByHost(host.getId());
        hpuxSystem = convertHost(host);
    }

    @Override
    public void init() throws Exception {
        super.init();
        initHost();
    }

    protected void acquireHostsLock() {
        acquireHostLock(host, null);
    }

    public static HpuxSystem convertHost(Host host) {
        HpuxSystem cli = new HpuxSystem();
        cli.setHost(host.getHostName());
        cli.setPort(host.getPortNumber());
        cli.setUsername(host.getUsername());
        cli.setPassword(host.getPassword());
        cli.setHostId(host.getId());
        return cli;
    }
}
