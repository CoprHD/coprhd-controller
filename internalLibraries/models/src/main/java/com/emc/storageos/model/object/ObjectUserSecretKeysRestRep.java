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
    private String secretkey1;
    
    @XmlElement(name = "secret_key1")
    public String getSecretkey1() {
        return secretkey1;
    }
    public void setSecretkey1(String secretkey1) {
        this.secretkey1 = secretkey1;
    }
    


}
