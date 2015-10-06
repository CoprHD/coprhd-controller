package com.emc.storageos.cinder.model;

import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.cinder.model.VolumeCreateResponse.Attachement;
import com.emc.storageos.cinder.model.VolumeCreateResponse.Metadata;
import com.emc.storageos.cinder.model.VolumeCreateResponse.Volume;
import com.emc.storageos.model.RestLinkRep;

@XmlRootElement
public class CinderInitConnectionResponse{
	@XmlElement(name="connection_info")
	public CinderInitConnection connection_info = new CinderInitConnection();
	
	public class CinderInitConnection {			
		public String driver_volume_type;
		public Data data = new Data();  
		
		public class Data{
			public String target_discovered;
			public String qos_specs;
			public String target_iqn;
			public String target_portal;
			public String volume_id;
			public int target_lun;
			public String access_mode;		
		}
		
	}
}