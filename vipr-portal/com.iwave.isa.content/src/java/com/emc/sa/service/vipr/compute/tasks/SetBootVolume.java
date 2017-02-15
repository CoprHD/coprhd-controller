/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.LongRunningTask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.vipr.client.Task;

public class SetBootVolume extends LongRunningTask<HostRestRep> {
    private URI hostId;
    private URI volumeId;
    private boolean updateSanBootTargets = false;

    public SetBootVolume(Host host, URI volumeId, boolean updateSanBootTargets) {
        this.hostId = host.getId();
        this.volumeId = volumeId;
        this.updateSanBootTargets = updateSanBootTargets;
        setWaitFor(true);
        provideDetailArgs(host.getHostName(), volumeId);
    }

    public SetBootVolume(HostRestRep host, URI volumeId) {
        this.hostId = host.getId();
        this.volumeId = volumeId;
        setWaitFor(true);
        provideDetailArgs(host.getHostName(), volumeId);
    }

    @Override
    protected Task<HostRestRep> doExecute() throws Exception {
        HostUpdateParam update = new HostUpdateParam();
        update.setBootVolume(volumeId);
        return getClient().hosts().update(hostId, update, false, false, updateSanBootTargets);
    }
}
