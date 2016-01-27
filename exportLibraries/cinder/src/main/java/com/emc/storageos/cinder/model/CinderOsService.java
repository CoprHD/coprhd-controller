/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonProperty;
import com.google.gson.annotations.SerializedName;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value="service")
@XmlRootElement(name="service")
public class CinderOsService {
	private String status;
	private String binary;
	private String zone;
	private String state;

	@SerializedName("updated_at")
    @JsonProperty(value = "updated_at")
	private String updatedAt;
	private String host;
	@SerializedName("disabled_reason")
    @JsonProperty(value = "disabled_reason")	
	private String disabledReason;
	
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


public String getUpdatedAt() {
    return updatedAt;
}

public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
}

public String getDisabledReason() {
    return disabledReason;
}

public void setDisabledReason(String disabledReason) {
    this.disabledReason = disabledReason;
}

}