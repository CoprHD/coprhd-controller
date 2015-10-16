package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@XmlRootElement(name="cgsnapshot")
@JsonRootName(value="cgsnapshot")
public class ConsistencyGroupSnapshotDetail {
    
	@XmlAttribute
	public String status;
	@XmlAttribute
	public String description;
	@XmlAttribute
	public String created_at;
	@XmlAttribute
	public String consistencygroup_id;
	@XmlAttribute
	public String id;
	@XmlAttribute
	public String name;
}
