/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_create")
public class XtremIOInitiatorCreate {
    
    @SerializedName("initiator-name")
    @JsonProperty(value = "initiator-name")
    private String name;
    
    @SerializedName("ig-id")
    @JsonProperty(value = "ig-id")
    private String initiatorGroup;
    
    @SerializedName("port-address")
    @JsonProperty(value = "port-address")
    private String portAddress;

    public String getInitiatorGroup() {
        return initiatorGroup;
    }

    public void setInitiatorGroup(String initiatorGroup) {
        this.initiatorGroup = initiatorGroup;
    }

    public String getPortAddress() {
        return portAddress;
    }

    public void setPortAddress(String portAddress) {
        this.portAddress = portAddress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public String toString() {
        return "initiator-name: " + name + ". ig-id: " + initiatorGroup + ", port-address: " + portAddress;
    }

}
