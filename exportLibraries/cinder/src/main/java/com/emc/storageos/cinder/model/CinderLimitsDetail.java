package com.emc.storageos.cinder.model;

import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.cinder.model.VolumeCreateResponse.Attachement;
import com.emc.storageos.cinder.model.VolumeCreateResponse.Metadata;
import com.emc.storageos.cinder.model.VolumeCreateResponse.Volume;
import com.emc.storageos.model.RestLinkRep;

@XmlRootElement(name="limits")
public class CinderLimitsDetail {	
	public CinderLimits limits = new CinderLimits();	
}
