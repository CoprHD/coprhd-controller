package com.emc.storageos.cinder.model;

import com.emc.storageos.model.RestLinkRep;

import javax.xml.bind.annotation.XmlElement;

public class CinderQosDetail{

    private RestLinkRep selfLink;

	@XmlElement(name = "qos_specs")
	public CinderQos qos_spec = new CinderQos();

    @XmlElement(name = "links")
    public RestLinkRep getLink(){
        return selfLink;
    }
    public void setLink(RestLinkRep link) {
        selfLink = link;
    }
}

