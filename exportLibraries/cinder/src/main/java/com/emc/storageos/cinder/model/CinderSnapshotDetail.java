/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "snapshot")
public class CinderSnapshotDetail {
    @XmlElement(name = "snapshot")
    public CinderSnapshot snapshot = new CinderSnapshot();
}
