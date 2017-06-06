/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.collectdata;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * Protection Domain attributes
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaleIOProtectionDomainDataRestRep {
    private String id;
    private String name;
    private String systemId;
    private String protectionDomainState;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getProtectionDomainState() {
        return protectionDomainState;
    }

    public void setProtectionDomainState(String protectionDomainState) {
        this.protectionDomainState = protectionDomainState;
    }

}
