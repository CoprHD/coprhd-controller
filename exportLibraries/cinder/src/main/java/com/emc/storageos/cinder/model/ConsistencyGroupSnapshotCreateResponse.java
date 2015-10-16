package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;
@XmlRootElement(name="cgsnapshot")
@JsonRootName(value="cgsnapshot")
public class ConsistencyGroupSnapshotCreateResponse {
		
		@XmlAttribute
		public String id;
		
		@XmlAttribute
		public String name;


}
