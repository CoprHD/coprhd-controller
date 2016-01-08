/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.api.service.impl.placement.FileMirrorSchedular;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.ArrayList;


public class FileRemoteMirrorServiceApiImpl extends AbstractFileServiceApiImpl<FileMirrorSchedular> {

    private static final Logger _log = LoggerFactory.getLogger(FileRemoteMirrorServiceApiImpl.class);

    public FileRemoteMirrorServiceApiImpl() {
        super(null);
    }

    /**
     * it take mirror recommendation and then creates source and mirror fileshare
     */
    @Override
    public TaskList createFileSystems(FileSystemParam param, Project project, VirtualArray varray,
                                      VirtualPool vpool, TenantOrg tenantOrg, DataObject.Flag[] flags, List<Recommendation> recommendations,
                                      TaskList taskList, String taskId, VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
        // TBD
        return null;
    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType,
                                  boolean forceDelete, String task) throws InternalException {
        _log.info("Request to delete {} FileShare(s) with Mirror Protection", fileSystemURIs.size());
        super.deleteFileSystems(systemURI, fileSystemURIs, deletionType, forceDelete, task);

    }

    @Override
    protected List<FileDescriptor> getDescriptorsOfFileShareDeleted(URI systemURI, List<URI> fileShareURIs, String deletionType, boolean forceDelete) {
        return null;
    }


    private List<URI> prepareRecommendedFileSystems(final FileSystemParam param, final String task,
                                                    final TaskList taskList, final Project project, final VirtualArray varray,
                                                    final VirtualPool vpool, DataObject.Flag[] flags,
                                                    final List<Recommendation> recommendations,
                                                    VirtualPoolCapabilityValuesWrapper vpoolCapabilities) {
        List<URI> fileSystemURIs = new ArrayList<URI>();

        return fileSystemURIs;
    }
}
