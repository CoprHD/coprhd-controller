/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RestLinkRep;

@XmlRootElement(name = "extensions")
@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
public class Extension {
    public String name;
    public String description;
    public String namespace;
    public String alias;
    public String updated;
    private RestLinkRep selfLink;

    @XmlElement(name = "links")
    public RestLinkRep getLink() {
        return selfLink;
    }

}
