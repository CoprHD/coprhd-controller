/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;

/**
 * Common LeaderSelectorListener Implementation
 * The specific leader should implement its startLeadership() and stopLeadership().
 */
public abstract class LeaderSelectorListenerImpl implements LeaderSelectorListener {
    private static final Log _log = LogFactory.getLog(LeaderSelectorListenerImpl.class);

    private boolean _isRunning = false;

    protected CuratorFramework _curatorClient = null;

    /*
     * Start the leadership operation.
     * It would be invoked after getting leader role.
     * Note: The implemetation should lauch scheduled
     * thread at fixed interval in backgroud.
     */
    protected abstract void startLeadership() throws Exception;

    /*
     * Stop the leadership operation
     * It would cleanup itself and give up the leader role.
     * Note: The implemetation should cancel the backend thread
     * started via startLeadership()
     */
    protected abstract void stopLeadership();

    public void takeLeadership(CuratorFramework client) throws Exception {
        _curatorClient = client;

        _log.info("Leader is starting ...");
        synchronized (this) {
            _isRunning = true;

            startLeadership();

            try {
                while (_isRunning) {
                    wait();
                }
            } catch (InterruptedException e) {
                stopLeadership();
            }
            _log.info("Leader is stopped.");
        }
        return;
    }

    public void stateChanged(CuratorFramework client, ConnectionState newState) {
        _log.info("Connection state changes to " + newState.toString());
        if ((newState == ConnectionState.SUSPENDED)
                || (newState == ConnectionState.LOST)) {
            _log.info("### Got SUSPENDED but ignored");
            return;
            /*
            synchronized (this) {
                if (!_isRunning) {
                    return;
                }

                _log.info("Leader is stopping ...");
                _isRunning = false;

                stopLeadership();

                notify();
            }
            */
        }
    }
}
