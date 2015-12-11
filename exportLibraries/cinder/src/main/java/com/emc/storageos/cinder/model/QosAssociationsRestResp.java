/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

// REST response for the List volume types query
@XmlRootElement(name = "qos_associations")
public class QosAssociationsRestResp {

    private List<VolumeType> types;

    @XmlElementRef
    public List<VolumeType> getVolumeTypes() {
        if (types == null) {
            types = new ArrayList<VolumeType>();
        }
        return types;
    }

}
