/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

/**
 * Capture get user secret keys ECS REST output 
 */
public class UserSecretKeysGetCommandResult {
    String secret_key_1;
    String key_timestamp_1;
    String key_expiry_timestamp_1;
    String secret_key_2;
    String key_timestamp_2;
    String key_expiry_timestamp_2;
    ECSLink link;
    
    public String getSecret_key_1() {
        return secret_key_1;
    }
    public void setSecret_key_1(String secret_key_1) {
        this.secret_key_1 = secret_key_1;
    }
    public String getKey_timestamp_1() {
        return key_timestamp_1;
    }
    public void setKey_timestamp_1(String key_timestamp_1) {
        this.key_timestamp_1 = key_timestamp_1;
    }
    public String getKey_expiry_timestamp_1() {
        return key_expiry_timestamp_1;
    }
    public void setKey_expiry_timestamp_1(String key_expiry_timestamp_1) {
        this.key_expiry_timestamp_1 = key_expiry_timestamp_1;
    }
    public String getSecret_key_2() {
        return secret_key_2;
    }
    public void setSecret_key_2(String secret_key_2) {
        this.secret_key_2 = secret_key_2;
    }
    public String getKey_timestamp_2() {
        return key_timestamp_2;
    }
    public void setKey_timestamp_2(String key_timestamp_2) {
        this.key_timestamp_2 = key_timestamp_2;
    }
    public String getKey_expiry_timestamp_2() {
        return key_expiry_timestamp_2;
    }
    public void setKey_expiry_timestamp_2(String key_expiry_timestamp_2) {
        this.key_expiry_timestamp_2 = key_expiry_timestamp_2;
    }
    public ECSLink getLink() {
        return link;
    }
    public void setLink(ECSLink link) {
        this.link = link;
    }
    
    @Override
    public String toString() {
        return "UserSecretKeysCommandResult: {secret_key_1=" + secret_key_1 + ", key_timestamp_1=" + key_timestamp_1
                + ", key_expiry_timestamp_1=" + key_expiry_timestamp_1 + ", secret_key_2=" + secret_key_2 + ", "
                + "key_timestamp_2=" + key_timestamp_2 + ", key_expiry_timestamp_2=" + key_expiry_timestamp_2 + ", "
                + "link=" + link + "}";
    }

}
