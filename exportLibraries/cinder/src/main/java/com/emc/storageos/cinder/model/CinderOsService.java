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
	private String status;
	private String binary;
	private String zone;
	private String state;
	private String updated_at;
	private String host;
	private String disabled_reason;
	
public String getStatus() {
    return status;
}
public void setStatus(String status) {
    this.status = status;
}

public String getBinary() {
    return binary;
}

public void setBinary(String binary) {
    this.binary = binary;
}

public String getZone() {
    return zone;
}

public void setZone(String zone) {
    this.zone = zone;
}

public String getState() {
    return state;
}

public void setState(String state) {
    this.state = state;
}


public String getHost() {
    return host;
}

public void setHost(String host) {
    this.host = host;
}


public String getUpdated_at() {
    return updated_at;
}

public void setUpdated_at(String updated_at) {
    this.updated_at = updated_at;
}

public String getDisabled_reason() {
    return disabled_reason;
}

public void setDisabled_reason(String disabled_reason) {
    this.disabled_reason = disabled_reason;
}

}