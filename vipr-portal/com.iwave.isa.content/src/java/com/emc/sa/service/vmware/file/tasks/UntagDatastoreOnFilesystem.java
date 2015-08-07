/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file.tasks;

import java.net.URI;

import com.emc.sa.machinetags.vmware.VMwareDatastoreTagger;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class UntagDatastoreOnFilesystem extends ViPRExecutionTask<Integer> {

    private VMwareDatastoreTagger tagger;

    private final URI filesystemId;
    private final URI vcenterId;
    private final URI datacenterId;
    private final String datastoreName;

    public UntagDatastoreOnFilesystem(URI filesystemId, URI vcenterId, URI datacenterId, String datastoreName) {
        this.filesystemId = filesystemId;
        this.vcenterId = vcenterId;
        this.datacenterId = datacenterId;
        this.datastoreName = datastoreName;
        provideDetailArgs(filesystemId, vcenterId, datacenterId, datastoreName);
    }

    @Override
    public Integer executeTask() throws Exception {
        tagger = new VMwareDatastoreTagger(getClient());
        return tagger.removeDatastoreTagsFromFilesystem(filesystemId, vcenterId, datacenterId, datastoreName);
    }
}
