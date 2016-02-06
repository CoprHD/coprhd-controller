/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

public class UserSecretKeysAddCommandResult {
    private ECSLink link;
    private String secret_key;
    private String key_timestamp;
    private String key_expiry_timestamp;
    
    public ECSLink getLink() {
        return link;
    }
    public void setLink(ECSLink link) {
        this.link = link;
    }
    public String getSecret_key() {
        return secret_key;
    }
    public void setSecret_key(String secret_key) {
        this.secret_key = secret_key;
    }
    public String getKey_timestamp() {
        return key_timestamp;
    }
    public void setKey_timestamp(String key_timestamp) {
        this.key_timestamp = key_timestamp;
    }
    public String getKey_expiry_timestamp() {
        return key_expiry_timestamp;
    }
    public void setKey_expiry_timestamp(String key_expiry_timestamp) {
        this.key_expiry_timestamp = key_expiry_timestamp;
    }
    
    @Override
    public String toString() {
        return "UserSecretKeysAddCommandResult: {link=" + link + ", secret_key=" + secret_key + ", "
                + "key_timestamp=" + key_timestamp
                + ", key_expiry_timestamp=" + key_expiry_timestamp + "}";
    }
    
}
