/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * The class save information for adding not in CG volumesto application
 *
 */
public class ApplicationAddVolumeList implements Serializable{

    private static final long serialVersionUID = 3399830785374039013L;
    private List<URI> volumes;
    // The name of the backend replication group that the volumes would add to
    private String replicationGroupName;
    // The consistency group URI that the volumes would add to
    private URI consistencyGroup;

    public List<URI> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<URI>();
        }
        return volumes;
    }

    public void setVolumes(List<URI> volumes) {
        this.volumes = volumes;
    }

    public String getReplicationGroupName() {
        return replicationGroupName;
    }
    
    public void setReplicationGroupName(String rpname) {
        replicationGroupName = rpname;
    }
    
    public URI getConsistencyGroup() {
        return consistencyGroup;
    }
    
    public void setConsistencyGroup(URI cg) {
        consistencyGroup = cg;
    }
    
}
