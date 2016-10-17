/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.google.common.collect.Lists;

/**
 * Task that verifies dependencies on a list of volumes. An illegal state exception will be thrown if any 
 * volume contain dependencies (snapshot, snapshot session, full copy, continuous copy)
 *
 * @author cormij4
 *
 */

public class VerifyVolumeDependencies extends ViPRExecutionTask<Void> {

    private List<URI> volumeIds;
    private URI projectId;
    
    public VerifyVolumeDependencies(List<URI> ids, URI projectId) {
        this.volumeIds = ids;
        this.projectId = projectId;
        provideDetailArgs(ids);
    }
    
    @Override
    public void execute() throws Exception {
        List<URI> exportedVolumes = getExportedVolumes(projectId);

        List<DependencyType> dependencies = new ArrayList<DependencyType>();
        for (URI id : this.volumeIds) {
            // List to gather dependencies
            List<String> dependencyType = new ArrayList<String>();
            
            if (!getClient().blockSnapshots().getByVolume(id).isEmpty()) {
                dependencyType.add(getMessage(DependencyType.SNAPSHOT));
            }
            if (!getClient().blockSnapshotSessions().getByVolume(id).isEmpty()) {
                dependencyType.add(getMessage(DependencyType.SNAPSHOT_SESSION));
            }
            if (!getClient().blockVolumes().getFullCopies(id).isEmpty()) {
                dependencyType.add(getMessage(DependencyType.FULL_COPY));
            }
            if (!getClient().blockVolumes().getContinuousCopies(id).isEmpty()) {
                dependencyType.add(getMessage(DependencyType.CONTINUOUS_COPY));
            }
            if (isExported(id, exportedVolumes)) {
                dependencyType.add(getMessage(DependencyType.EXPORTED));
            }

            // If we have dependencies, get volume name so that display information is relevant
            if (!dependencyType.isEmpty()) {
                String volName = getClient().blockVolumes().get(id).getName();
                for (String type : dependencyType) {
                    dependencies.add(new DependencyType(type, volName));
                }
            }
        }
        
        if (!dependencies.isEmpty()) {
            throw stateException("VerifyVolumeDependencies.illegalState.volumeContainsDependencies", dependencies);
        }
    }

    /**
     * Check if volume is exported.
     *
     * @param id of the volume to validate
     * @param exportedVolumes for the given project
     * @return true or false is volume is exported
     */
    private boolean isExported(URI id, List<URI> exportedVolumes) {
        return exportedVolumes.contains(id);
    }

    /**
     * Retrieve all exported volumes on given project ID.
     *
     * @param projectId to retrieve exported volumes
     * @return list of exported volume
     */
    private List<URI> getExportedVolumes(URI projectId) {
        List<URI> volumeIds = Lists.newArrayList();
        for (ExportGroupRestRep export : getClient().blockExports().findByProject(projectId)) {
            for (ExportBlockParam resource : export.getVolumes()) {
                if (ResourceType.isType(ResourceType.VOLUME, resource.getId())) {
                    volumeIds.add(resource.getId());
                }
            }
        }

        return volumeIds;
    }

    /**
     * Private helper class to help display the different dependency type for a volume
     *
     * @author cormij4
     *
     */
    private class DependencyType {
        public static final String SNAPSHOT = "VerifyVolumeDependencies.Snapshot";
        public static final String SNAPSHOT_SESSION = "VerifyVolumeDependencies.SnapshotSession";
        public static final String FULL_COPY = "VerifyVolumeDependencies.FullCopy";
        public static final String CONTINUOUS_COPY = "VerifyVolumeDependencies.ContinuousCopy";
        public static final String EXPORTED = "VerifyVolumeDependencies.Exported";

        private String type;
        private String volumeName;

        public DependencyType(String type, String name) {
            this.type = type;
            this.volumeName = name;
        }

        public String toString() {
            return volumeName + " - " + this.type;
        }
    }
}