/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.RestLinkRep;

public class CinderVolume {
    public String name;
    public String id;
    private RestLinkRep selfLink;

    @XmlElement(name = "links")
    public RestLinkRep getLink() {
        return selfLink;
    }

    public void setLink(RestLinkRep link) {
        selfLink = link;
    }

}
