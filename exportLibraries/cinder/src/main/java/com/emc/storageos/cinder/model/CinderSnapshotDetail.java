package com.emc.storageos.cinder.model;
import java.util.Map;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.emc.storageos.cinder.model.VolumeCreateResponse.Attachement;
import com.emc.storageos.cinder.model.VolumeCreateResponse.Metadata;
import com.emc.storageos.cinder.model.VolumeCreateResponse.Volume;
import com.emc.storageos.model.RestLinkRep;

@XmlRootElement(name="snapshot")
public class CinderSnapshotDetail{ 
	@XmlElement(name = "snapshot")
	public CinderSnapshot snapshot = new CinderSnapshot();
}

