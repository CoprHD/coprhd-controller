/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Application update parameters
 */
@XmlRootElement(name = "volume_group_update")
public class VolumeGroupUpdateParam {
    private String name;
    private String description;
    private String parent;
    
    public static class VolumeGroupVolumeList {
        private List<URI> volumes;
        // The name of the backend replication group that the volumes would add to
        private String replicationGroupName;
        // The consistency group URI that the volumes would add to
        private URI consistencyGroup;
        /**
         * A block volume URI
         * 
         */
        @XmlElement(name = "volume")
        public List<URI> getVolumes() {
            if (volumes == null) {
                volumes = new ArrayList<URI>();
            }
            return volumes;
        }

        public void setVolumes(List<URI> volumes) {
            this.volumes = volumes;
        }
        
        @XmlElement(name = "replication_group_name")
        public String getReplicationGroupName() {
            return replicationGroupName;
        }
        
        public void setReplicationGroupName(String rpname) {
            replicationGroupName = rpname;
        }
        
        @XmlElement(name = "consistency_group")
        public URI getConsistencyGroup() {
            return consistencyGroup;
        }
        
        public void setConsistencyGroup(URI cg) {
            consistencyGroup = cg;
        }
    }

    private VolumeGroupVolumeList addVolumesList;
    private VolumeGroupVolumeList removeVolumesList;

    /**
     * List of volumes to add to the volume group
     * 
     */
    @XmlElement(name = "add_volumes")
    public VolumeGroupVolumeList getAddVolumesList() {
        return addVolumesList;
    }

    public void setAddVolumesList(VolumeGroupVolumeList addVolumesList) {
        this.addVolumesList = addVolumesList;
    }

    /**
     * List of volumes to remove from the volume group
     * 
     */
    @XmlElement(name = "remove_volumes")
    public VolumeGroupVolumeList getRemoveVolumesList() {
        return removeVolumesList;
    }

    public void setRemoveVolumesList(
            VolumeGroupVolumeList removeVolumesList) {
        this.removeVolumesList = removeVolumesList;
    }

    public boolean hasEitherAddOrRemoveVolumes() {
        return hasVolumesToAdd() || hasVolumesToRemove();
    }

    public boolean hasBothAddAndRemoveVolumes() {
        return hasVolumesToAdd() && hasVolumesToRemove();
    }

    public boolean hasVolumesToAdd() {
        return addVolumesList != null && addVolumesList.volumes != null &&
                !addVolumesList.volumes.isEmpty();
    }

    public boolean hasVolumesToRemove() {
        return removeVolumesList != null && removeVolumesList.volumes != null &&
                !removeVolumesList.volumes.isEmpty();
    }
    /**
     * volume group unique name
     *
     * Valid values: 
     *     minimum of 2 characters
     *     maximum of 128 characters
     */
    @XmlElement
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * volume group description
     */
    @XmlElement
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the parent
     */
    @XmlElement
    public String getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(String parent) {
        this.parent = parent;
    }
    
}
