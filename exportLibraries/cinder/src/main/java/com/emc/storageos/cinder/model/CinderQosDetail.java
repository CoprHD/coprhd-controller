/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;

public class CinderQosDetail {
    @XmlElement(name = "qos_specs")
    public CinderQos qos_spec = new CinderQos();
}
