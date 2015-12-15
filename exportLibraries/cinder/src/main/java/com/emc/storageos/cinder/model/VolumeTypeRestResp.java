/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

//REST response for the Show volume type query
@XmlRootElement(name = "volume_type")
public class VolumeTypeRestResp {

    private VolumeType _type;

    @XmlElement(name = "volume_type")
    public VolumeType getVolumeType() {
        if (_type == null) {
            _type = new VolumeType();
        }
        return _type;
    }

    public void setVolumeType(VolumeType type) {
        _type = type;
    }

}
