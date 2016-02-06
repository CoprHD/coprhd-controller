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
    private String secret_key_1;
    private String secret_key_1_expiry_timestamp;
    private String secret_key_2;
    private String secret_key_2_expiry_timestamp;
    
    public ObjectUserSecretKeysRestRep() {
    }
    
    @XmlElement(name = "secret_key_1")
    public String getSecret_key_1() {
        return secret_key_1;
    }
    public void setSecret_key_1(String secret_key_1) {
        this.secret_key_1 = secret_key_1;
    }
    
    @XmlElement(name = "secret_key_1_expiry_timestamp")
    public String getSecret_key_1_expiry_timestamp() {
        return secret_key_1_expiry_timestamp;
    }
    public void setSecret_key_1_expiry_timestamp(String secret_key_1_expiry_timestamp) {
        this.secret_key_1_expiry_timestamp = secret_key_1_expiry_timestamp;
    }
    
    @XmlElement(name = "secret_key_2")
    public String getSecret_key_2() {
        return secret_key_2;
    }
    public void setSecret_key_2(String secret_key_2) {
        this.secret_key_2 = secret_key_2;
    }
    
    @XmlElement(name = "secret_key_2_expiry_timestamp")
    public String getSecret_key_2_expiry_timestamp() {
        return secret_key_2_expiry_timestamp;
    }
    public void setSecret_key_2_expiry_timestamp(String secret_key_2_expiry_timestamp) {
        this.secret_key_2_expiry_timestamp = secret_key_2_expiry_timestamp;
    }

}
