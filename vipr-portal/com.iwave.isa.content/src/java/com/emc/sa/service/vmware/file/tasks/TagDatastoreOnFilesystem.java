/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.machinetags.vmware.VMwareDatastoreTagger;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class TagDatastoreOnFilesystem extends ViPRExecutionTask<Integer> {

    private final URI filesystemId;
    private final URI vcenterId;
    private final URI datacenterId;
    private final String datastoreName;
    private final String mountPoint;
    private final List<String> endpoints;

    public TagDatastoreOnFilesystem(URI filesystemId, URI vcenterId, URI datacenterId, String datastoreName,
            String mountPoint, List<String> endpoints) {
        this.filesystemId = filesystemId;
        this.vcenterId = vcenterId;
        this.datacenterId = datacenterId;
        this.datastoreName = datastoreName;
        this.mountPoint = mountPoint;
        this.endpoints = endpoints;
        provideDetailArgs(filesystemId, vcenterId, datacenterId, datastoreName, mountPoint, endpoints);
    }

    @Override
    public Integer executeTask() throws Exception {
        final VMwareDatastoreTagger tagger = new VMwareDatastoreTagger(getClient());
        return tagger.addDatastoreTagsToFilesystem(filesystemId, vcenterId, datacenterId, datastoreName, mountPoint, endpoints);
    }

}
