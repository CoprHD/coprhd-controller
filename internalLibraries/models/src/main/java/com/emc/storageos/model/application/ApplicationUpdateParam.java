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
@XmlRootElement(name = "application_update")
public class ApplicationUpdateParam {
    private String name;
    private String description;
    
    public static class ApplicationVolumeList {
        private List<URI> volumes;
        // The name of the backend replication group that the volumes would add to
        private String replicationGroupName;
        // The consistency group URI that the volumes would add to
        private URI consistencyGroup;
        /**
         * A block volume URI
         * 
         * @valid none
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

    private ApplicationVolumeList addVolumesList;
    private ApplicationVolumeList removeVolumesList;

    /**
     * List of volumes to add to the block consistency group
     * 
     * @valid none
     */
    @XmlElement(name = "add_volumes")
    public ApplicationVolumeList getAddVolumesList() {
        return addVolumesList;
    }

    public void setAddVolumesList(ApplicationVolumeList addVolumesList) {
        this.addVolumesList = addVolumesList;
    }

    /**
     * List of volumes to remove from the block consistency group
     * 
     * @valid none
     */
    @XmlElement(name = "remove_volumes")
    public ApplicationVolumeList getRemoveVolumesList() {
        return removeVolumesList;
    }

    public void setRemoveVolumesList(
            ApplicationVolumeList removeVolumesList) {
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
     * Application unique name
     * 
     * @valid minimum of 2 characters
     * @valid maximum of 128 characters
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
     * Application description
     */
    @XmlElement
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
}
