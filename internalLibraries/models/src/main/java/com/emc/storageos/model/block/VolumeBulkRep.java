/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.BulkRestRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_volumes")
public class VolumeBulkRep extends BulkRestRep {
    private List<VolumeRestRep> volumes;

    /**
     * List of volume objects specifying its characteristics
     * such as allocated capacity, provisioned capacity, disk
     * technology, and whether or not the volume is thinly provisioned.
     * 
     * @valid none
     */
    @XmlElement(name = "volume")
    public List<VolumeRestRep> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<VolumeRestRep>();
        }
        return volumes;
    }

    public void setVolumes(List<VolumeRestRep> volumes) {
        this.volumes = volumes;
    }

    public VolumeBulkRep() {
    }

    public VolumeBulkRep(List<VolumeRestRep> volumes) {
        this.volumes = volumes;
    }
}
