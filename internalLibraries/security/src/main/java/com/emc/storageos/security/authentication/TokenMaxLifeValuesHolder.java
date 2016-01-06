/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.authentication;

import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Holds the token max life and related values, for other beans
 * to consume.
 * */
public class TokenMaxLifeValuesHolder {
    private static final Logger _log = LoggerFactory.getLogger(TokenMaxLifeValuesHolder.class);

    private static final int TOKEN_IDLE_TIME_GRACE_IN_MINS = 10;
    private static final int FOREIGN_TOKEN_CACHE_EXPIRATION_IN_MINS = 10;

    protected int _maxTokenLifeTimeInMins;
    protected int _maxTokenIdleTimeInMins;
    protected int _tokenIdleTimeGraceInMins = TOKEN_IDLE_TIME_GRACE_IN_MINS;
    protected int _foreignTokenCacheExpirationInMins = FOREIGN_TOKEN_CACHE_EXPIRATION_IN_MINS;
    protected long _overrideKeyRotationIntervalInMsecs = 0;

    private CoordinatorClient _coordinator;
    private SystemPropertyChangeListener _listener;

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
        loadParameterFromZK();
        addSystemPropertyChangeListener();
    }

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
            return (maxLifeInMsecs * 3);
        }
        return _overrideKeyRotationIntervalInMsecs;
    }

    /**
     * Rotation interval is computed automatically
     * But this setter allows overriding the computed value.
     * 
     * @param i
     */
    public void setKeyRotationIntervalInMSecs(long i) {
        _overrideKeyRotationIntervalInMsecs = i;
    }

    private class SystemPropertyChangeListener implements NodeListener {
        private String SYSTEM_PROPERTY_PATH = "/config/upgradetargetpropertyoverride/global";

        public String getPath() {
            return SYSTEM_PROPERTY_PATH;
        }

        /**
         * called when user modify IPs, procedure or node status from ipreconfig point of view
         */
        @Override
        public void nodeChanged() {
            _log.info("systerm property changed");
            loadParameterFromZK();
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {
            _log.info("connection state changed to {}", state);
            if (state.equals(State.CONNECTED)) {
                loadParameterFromZK();
            }
        }
    }

    private void addSystemPropertyChangeListener() {
        try {
            if (_listener != null) {
                _coordinator.removeNodeListener(_listener);
            }
            _listener = new SystemPropertyChangeListener();
            _coordinator.addNodeListener(_listener);
        } catch (Exception e) {
            _log.error("Fail to add node listener for system property znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        _log.info("Succeed to add node listener for system property znode");
    }

    /**
     * load parameter from system properties of ZooKeeper.
     * if the properties do not exist, or exception when loading, use default values.
     */
    public void loadParameterFromZK() {
        try {
            _log.info("load token life time and idle time from zk");
            PropertyInfoExt params = _coordinator.getTargetInfo(PropertyInfoExt.class);
            _maxTokenLifeTimeInMins = NumberUtils.toInt(params.getProperty(Constants.TOKEN_LIFE_TIME),
                    Constants.DEFAULT_TOKEN_LIFE_TIME);
            _maxTokenIdleTimeInMins = NumberUtils.toInt(params.getProperty(Constants.TOKEN_IDLE_TIME),
                    Constants.DEFAULT_TOKEN_IDLE_TIME);
        } catch (Exception e) {
            _log.warn("load parameter from ZK error, use default values.");
            _maxTokenLifeTimeInMins = Constants.DEFAULT_TOKEN_LIFE_TIME;
            _maxTokenIdleTimeInMins = Constants.DEFAULT_TOKEN_IDLE_TIME;
        }
    }

}
