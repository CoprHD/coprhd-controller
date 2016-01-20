/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
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
