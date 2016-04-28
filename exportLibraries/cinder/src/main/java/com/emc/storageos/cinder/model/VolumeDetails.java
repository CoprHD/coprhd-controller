/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value = "volumes")
@XmlRootElement(name = "volumes")
public class VolumeDetails {
    private List<VolumeDetail> volumes;

    @XmlElement(name = "volume")
    public List<VolumeDetail> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<VolumeDetail>();
        }
        return volumes;
    }

    public void setVolumes(List<VolumeDetail> volumes) {
        this.volumes = volumes;
    }
}
