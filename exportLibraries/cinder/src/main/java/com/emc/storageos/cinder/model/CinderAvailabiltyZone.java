package com.emc.storageos.cinder.model;

import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.emc.storageos.model.RestLinkRep;

@XmlRootElement(name="availability")
public class CinderAvailabiltyZone {	
    	//public absoluteStats absolute = new absoluteStats();
	public String zoneName;
    	public CinderAvailabiltyZone.state zoneState = new CinderAvailabiltyZone.state();
    	
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlType(name = "")
    	public class state{
    		public boolean available;    		
    	}
}
