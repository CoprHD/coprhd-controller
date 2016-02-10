/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DiscoveredDataObjectRestRep;

@XmlRootElement(name = "object_user_secret_key")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ObjectUserSecretKeyAddRestRep extends DiscoveredDataObjectRestRep {
    private String secret_key;
    private String secret_key_expiry_timestamp;

    public ObjectUserSecretKeyAddRestRep() {
    }
    
    @XmlElement(name = "secret_key")
    public String getSecret_key() {
        return secret_key;
    }
    public void setSecret_key(String secret_key) {
        this.secret_key = secret_key;
    }
    
    @XmlElement(name = "secret_key_expiry_timestamp")
    public String getSecret_key_expiry_timestamp() {
        return secret_key_expiry_timestamp;
    }
    public void setSecret_key_expiry_timestamp(String secret_key_expiry_timestamp) {
        this.secret_key_expiry_timestamp = secret_key_expiry_timestamp;
    }
    
}
