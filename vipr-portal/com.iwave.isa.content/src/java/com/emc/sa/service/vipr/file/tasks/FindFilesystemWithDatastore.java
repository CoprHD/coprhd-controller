/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.machinetags.vmware.VMwareDatastoreTagger;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;

/**
 * Search in a bourne project for a filesystem with a specific datastore tag on it.
 * 
 * Throws an exception if the datastore cannot be found on any filesystem in the given project.
 */
public class FindFilesystemWithDatastore extends ViPRExecutionTask<FileShareRestRep> {

    private final URI projectId;
    private final URI vcenterId;
    private final URI datacenterId;
    private final String datastoreName;

    public FindFilesystemWithDatastore(URI projectId, URI vcenterId, URI datacenterId, String datastoreName) {
        super();
        this.projectId = projectId;
        this.vcenterId = vcenterId;
        this.datacenterId = datacenterId;
        this.datastoreName = datastoreName;
        provideDetailArgs(projectId, vcenterId, datacenterId, datastoreName);
    }

    @Override
    public FileShareRestRep executeTask() throws Exception {
        VMwareDatastoreTagger tagger = new VMwareDatastoreTagger(getClient());
        for (FileShareRestRep filesystem : getClient().fileSystems().findByProject(projectId)) {
            final int index = tagger.getDatastoreIndex(filesystem.getId(), vcenterId, datacenterId, datastoreName);
            if (index > 0) {
                return filesystem;
            }
        }
        throw stateException("illegalState.fileFilesystemWithDatastore", vcenterId, datacenterId, datastoreName);
    }
}
