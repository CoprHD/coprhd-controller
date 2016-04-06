/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
/**
 * Attributes associated with a user key.
 * 
 */
@XmlRootElement(name = "object_user_secret_key_create")
public class ObjectUserSecretKeyRequestParam {
    private String secretkey;
    
    public ObjectUserSecretKeyRequestParam() {
    }

    public ObjectUserSecretKeyRequestParam(String secretkey) {
        this.secretkey = secretkey;
    }

    @XmlElement(required = false, name = "secret_key")
    public String getSecretkey() {
        return secretkey;
    }

    public void setSecretkey(String secretkey) {
        this.secretkey = secretkey;
    }
    
}
