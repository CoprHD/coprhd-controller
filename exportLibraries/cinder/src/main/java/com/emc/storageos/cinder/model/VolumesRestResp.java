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
public class VolumesRestResp {
    private List<CinderVolume> volumes;

    /**
     * List of volumes that make up this entry. Used primarily to report to cinder.
     */
    @XmlElement
    public List<CinderVolume> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<CinderVolume>();
        }
        return volumes;
    }

    public void setVolumes(List<CinderVolume> volumes) {
        this.volumes = volumes;
    }

}
