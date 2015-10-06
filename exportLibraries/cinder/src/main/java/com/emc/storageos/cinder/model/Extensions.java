package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "extensions")
public class Extensions {
	private List<Extension> extensions;
	
	@XmlElement (name = "extensions")
	public List<Extension> getVolumeTypes(){
		if (extensions == null){
			extensions = new ArrayList<Extension>();
		}
		return extensions;
	}
}
