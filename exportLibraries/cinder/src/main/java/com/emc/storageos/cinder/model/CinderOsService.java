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

public String getUpdatedAt() {
    return updated_at;
}

public void setUpdatedAt(String updatedAt) {
    this.updated_at = updatedAt;
}

public String getHost() {
    return host;
}

public void setHost(String host) {
    this.host = host;
}

public String getDisabledReason() {
    return disabled_reason;
}

public void setDisabledReason(String disabledReason) {
    this.disabled_reason = disabledReason;
}

}