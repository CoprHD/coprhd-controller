/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Consistency group update parameters
 */
@XmlRootElement(name = "consistency_group_update")
public class BlockConsistencyGroupUpdate {

    public static class BlockConsistencyGroupVolumeList {
        private List<URI> volumes;

        /**
         * A block volume URI
         * 
         */
        @XmlElement(required = true, name = "volume")
        public List<URI> getVolumes() {
            if (volumes == null) {
                volumes = new ArrayList<URI>();
            }
            return volumes;
        }

        public void setVolumes(List<URI> volumes) {
            this.volumes = volumes;
        }
    }

    private BlockConsistencyGroupVolumeList addVolumesList;
    private BlockConsistencyGroupVolumeList removeVolumesList;

    /**
     * List of volumes to add to the block consistency group
     * 
     */
    @XmlElement(name = "add_volumes")
    public BlockConsistencyGroupVolumeList getAddVolumesList() {
        return addVolumesList;
    }

    public void setAddVolumesList(BlockConsistencyGroupVolumeList addVolumesList) {
        this.addVolumesList = addVolumesList;
    }

    /**
     * List of volumes to remove from the block consistency group
     * 
     */
    @XmlElement(name = "remove_volumes")
    public BlockConsistencyGroupVolumeList getRemoveVolumesList() {
        return removeVolumesList;
    }

    public void setRemoveVolumesList(
            BlockConsistencyGroupVolumeList removeVolumesList) {
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
}
