/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RestLinkRep;

@XmlRootElement
public class Version {
    public String status;
    public String id;
    public String updated;
    private RestLinkRep selfLink;

    @XmlElement(name = "links")
    public RestLinkRep getLink() {
        return selfLink;
    }

}
