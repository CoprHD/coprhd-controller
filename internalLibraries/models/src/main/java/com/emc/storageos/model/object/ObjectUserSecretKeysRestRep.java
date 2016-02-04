/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import java.net.URI;

import javax.xml.bind.annotation.*;

import com.emc.storageos.model.DiscoveredDataObjectRestRep;

@XmlRootElement(name = "object_user_secret_keys")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ObjectUserSecretKeysRestRep extends DiscoveredDataObjectRestRep {
    private String key1;
    
    @XmlElement(name = "key1")
    public String getKey1() {
        return key1;
    }
    public void setKey1(String key1) {
        this.key1 = key1;
    }
    


}
