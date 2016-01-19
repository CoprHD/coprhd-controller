/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value="service")
@XmlRootElement(name="service")
public class CinderOsService {
	public String status;
	public String binary;
	public String zone;
	public String state;
	public String updated_at;
	public String host;
	public String disabled_reason;
}