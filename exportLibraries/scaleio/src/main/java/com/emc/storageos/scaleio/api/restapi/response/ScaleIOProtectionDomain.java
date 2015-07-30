/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.scaleio.api.restapi.response;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * Protection Domain attributes
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaleIOProtectionDomain {
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
