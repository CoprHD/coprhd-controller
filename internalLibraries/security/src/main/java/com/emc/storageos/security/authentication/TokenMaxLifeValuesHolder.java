/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.authentication;

/**
 * Holds the token max life and related values, for other beans
 * to consume.
 * */
public class TokenMaxLifeValuesHolder {
    private static final int MAX_TOKEN_LIFE_TIME_IN_MINS = 8 * 60; // 8 hrs
    private static final int MAX_TOKEN_IDLE_TIME_IN_MINS = 2 * 60; // 2 hrs
    private static final int TOKEN_IDLE_TIME_GRACE_IN_MINS = 10;  
    private static final int FOREIGN_TOKEN_CACHE_EXPIRATION_IN_MINS = 10; 

  
    protected int _maxTokenLifeTimeInMins = MAX_TOKEN_LIFE_TIME_IN_MINS;
    protected int _maxTokenIdleTimeInMins = MAX_TOKEN_IDLE_TIME_IN_MINS;
    protected int _tokenIdleTimeGraceInMins = TOKEN_IDLE_TIME_GRACE_IN_MINS;
    protected int _foreignTokenCacheExpirationInMins = FOREIGN_TOKEN_CACHE_EXPIRATION_IN_MINS;
    protected long _overrideKeyRotationIntervalInMsecs = 0;

    public void setMaxTokenLifeTimeInMins(int mins) {
        _maxTokenLifeTimeInMins = mins;
    }
    
    public int getMaxTokenLifeTimeInMins() {
        return _maxTokenLifeTimeInMins;
    }

    public void setMaxTokenIdleTimeInMins(int mins) {
        _maxTokenIdleTimeInMins = mins;
    }
    
    public int getMaxTokenIdleTimeInMins() {
        return _maxTokenIdleTimeInMins;
    }

    public void setTokenIdleTimeGraceInMins(int mins) {
        _tokenIdleTimeGraceInMins = mins;
    }
    
    public int getTokenIdleTimeGraceInMins() {
        return _tokenIdleTimeGraceInMins;
    }
    
    public void setForeignTokenCacheExpirationInMins(int mins) {
        _foreignTokenCacheExpirationInMins = mins;
    }
    
    public int getForeignTokenCacheExpirationInMins() {
        return _foreignTokenCacheExpirationInMins;
    }
    
    public long computeRotationTimeInMSecs() {
        if (_overrideKeyRotationIntervalInMsecs == 0) {
            long maxLifeInMsecs = (_maxTokenLifeTimeInMins * 60 * 1000);
            return (maxLifeInMsecs *3);
        }
        return _overrideKeyRotationIntervalInMsecs;
    }
    
    /**
     * Rotation interval is computed automatically
     * But this setter allows overriding the computed value.
     * @param i
     */
    public void setKeyRotationIntervalInMSecs(long i) {
        _overrideKeyRotationIntervalInMsecs = i;
    }
    
    
}
