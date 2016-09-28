/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.google.common.collect.Sets;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

public class FindLunsBackingDatastore extends ExecutionTask<Set<String>> {
    private final HostSystem host;
    private final Datastore datastore;

    public FindLunsBackingDatastore(HostSystem host, Datastore datastore) {
        this.host = host;
        this.datastore = datastore;
        provideDetailArgs(host.getName(), datastore.getName());
    }

    @Override
    public Set<String> executeTask() throws Exception {
        List<HostScsiDisk> disks = new HostStorageAPI(host).listDisks(datastore);
        Set<String> luns = Sets.newHashSet();
        for (HostScsiDisk disk : disks) {

            if (!validateCanonicalPrefix(disk.getCanonicalName())) {
                logError("FindLunsBackingDatastore.failure.invalidprefix", disk.getCanonicalName());
            }

            String volumeWwn = VMwareUtils.getDiskWwn(disk);
            if (StringUtils.isNotBlank(volumeWwn)) {
                luns.add(volumeWwn);
            }
        }
        return luns;
    }

    private boolean validateCanonicalPrefix(final String canonicalName) {
        if (canonicalName.startsWith(VMwareUtils.CANONICAL_NAME_PREFIX)
                || canonicalName.startsWith(VMwareUtils.ALTERNATE_CANONICAL_NAME_PREFIX)) {
            return true;
        }
        return false;
    }
}
