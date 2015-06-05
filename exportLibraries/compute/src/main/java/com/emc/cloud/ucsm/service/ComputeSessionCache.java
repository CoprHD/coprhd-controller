/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import java.io.Serializable;

/**
 Serializable helper class to load and store cached compute device sessions on zk.
**/
class ComputeSessionCache implements Serializable {
    private String sessionId = null;
    private Long createTime = null;
    private Long sessionLength =null;
    private String hashKey =null;

    ComputeSessionCache(String sessionId, Long createTime, Long sessionLength,String hashKey) {
        this.sessionId = sessionId;
        this.createTime = createTime;
        this.sessionLength = sessionLength;
        this.hashKey = hashKey;
    }
    ComputeSessionCache(){
        //Empty constructor to null out fields
    }

    String getHashKey() {
        return hashKey;
    }

    void setHashKey(String hashKey) {
        this.hashKey = hashKey;
    }

    Long getSessionLength() {
        return sessionLength;
    }

    void setSessionLength(Long sessionLength) {
        this.sessionLength = sessionLength;
    }

    Long getCreateTime() {
        return createTime;
    }

    void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    String getSessionId() {
        return sessionId;
    }

    void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
