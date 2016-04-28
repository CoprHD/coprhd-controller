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

// REST response for the List volume types query
@JsonRootName(value = "volume_types")
@XmlRootElement(name = "volume_types")
public class VolumeTypesRestResp {

    private List<VolumeType> volume_types;

    @XmlElement(name = "volume_type")
    public List<VolumeType> getVolume_types() {
        if (volume_types == null) {
            volume_types = new ArrayList<VolumeType>();
        }
        return volume_types;
    }

}
