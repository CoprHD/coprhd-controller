/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

/**
 * 
 * @author cormij4
 *
 */

public class VerifyVolumeDependencies extends ViPRExecutionTask<Void> {

    public List<URI> volumeIds;
    
    public VerifyVolumeDependencies(List<URI> ids) {
        this.volumeIds = ids;
        provideDetailArgs(ids);
    }
    
    @Override
    public void execute() throws Exception {
        List<DependencyType> dependencies = new ArrayList<DependencyType>();
        for (URI id : this.volumeIds) {
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
    
    private class DependencyType {
        public static final String SNAPSHOT = "Snapshot";
        public static final String SNAPSHOT_SESSION = "SnapshotSession";
        public static final String FULL_COPY = "FullCopy";
        public static final String CONTINUOUS_COPY = "ContinuousCopy";
        
        
        public String type;
        public String volumeName;
        
        public DependencyType(String type, String name) {
            this.type = type;
            this.volumeName = name;
        }
        
        public String toString() {
            return volumeName + " - " + this.type;
        }
    }
}
