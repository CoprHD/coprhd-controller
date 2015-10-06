package com.emc.storageos.cinder.model;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="volume")
public class CinderVolumeDetail{ 
	@XmlElement(name = "volume")
	public VolumeDetail volume = new VolumeDetail();
}

