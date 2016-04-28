/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.NamedRelatedResourceRep;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "block_mirror")
public class BlockMirrorRestRep extends VolumeRestRep {
    private NamedRelatedResourceRep source;
    private String syncState;
    private String syncType;
    private String replicaState;

    /**
     * Volume representing the source in a mirror relationship.
     * 
     */
    @XmlElement
    public NamedRelatedResourceRep getSource() {
        return source;
    }

    public void setSource(NamedRelatedResourceRep source) {
        this.source = source;
    }

    /**
     * Synchronization state. 
     * Valid values:
     * UNKNOWN 
     * RESYNCHRONIZING
     * SYNCHRONIZED
     * FRACTURED
     * 
     */
    @XmlElement
    public String getSyncState() {
        return syncState;
    }

    public void setSyncState(String syncState) {
        this.syncState = syncState;
    }

    /**
     * Synchronization type.
     * 
     */
    @XmlElement
    public String getSyncType() {
        return syncType;
    }

    public void setSyncType(String syncType) {
        this.syncType = syncType;
    }

    @XmlElement(name = "replica_state")
    public String getReplicaState() {
        return replicaState;
    }

    public void setReplicaState(String replicaState) {
        this.replicaState = replicaState;
    }
}
